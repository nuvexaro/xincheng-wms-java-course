package org.jeecg.modules.wms.ai.chat.controller;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.api.ISysBaseAPI;
import org.jeecg.common.system.util.JwtUtil;
import org.jeecg.common.util.AssertUtils;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.common.util.TokenUtils;
import org.jeecg.common.util.UUIDGenerator;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.config.shiro.IgnoreAuth;
import org.jeecg.modules.airag.app.entity.AiragApp;
import org.jeecg.modules.airag.app.service.IAiragAppService;
import org.jeecg.modules.airag.app.vo.ChatSendParams;
import org.jeecg.modules.airag.llm.handler.EmbeddingHandler;
import org.jeecg.modules.wms.ai.chat.tools.WmsInventoryTools;
import org.jeecg.modules.wms.ai.chat.tools.WmsPickRouteTools;
import org.jeecg.modules.wms.ai.chat.tools.WmsProductTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 助手接口
 * <p>
 * 3.2       ：/ai/chat/send  AI助手 v0.1（Flux 转 SseEmitter，对接 JeecgBoot 前端）
 * 3.4       ：              AI助手 v0.2（读取 APP 的 System 提示词）
 * 3.5       ：              AI助手 v0.3（会话记忆，保存到 Redis）
 * 4.6       ：              RAG（按应用的 knowledgeIds 检索知识库）
 * <p>
 * 4.1 / 4.3 的测试接口 /ai/chat、/ai/embedtest 已拆到 TestController，仅 dev 环境可用。
 */
@Slf4j
@RestController
@RequestMapping("/ai")
public class ChatController {

    /** 单次消息最大长度 */
    private static final int MAX_CONTENT_LENGTH = 4000;

    /** 一个应用最多使用的知识库数量 */
    private static final int MAX_KNOWLEDGE_IDS = 20;

    /** 课件示例是 0.5，本地测试数据较短，调低到 0.3；值越大越严格 */
    private static final double RAG_SIMILARITY_THRESHOLD = 0.3;

    /** SSE 超时时间：10 分钟 */
    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    @Resource(name = "chatClientOpenAi")
    private ChatClient chatClient;

    @Autowired
    private IAiragAppService airagAppService;

    @Autowired
    private ISysBaseAPI sysBaseApi;

    @Autowired
    private RedisUtil redisUtil;

    /** 向量库：PostgreSQL + pgvector（ChatConfiguration 里配置） */
    @Resource(name = "pgVectorStore")
    private VectorStore pgVectorStore;

    /** AI 工具：库存查询 */
    @Autowired
    private WmsInventoryTools wmsInventoryTools;

    /** AI 工具：商品查询 */
    @Autowired
    private WmsProductTools wmsProductTools;

    /** AI 工具：拣货路径查询 */
    @Autowired
    private WmsPickRouteTools wmsPickRouteTools;

    /**
     * 3.5 AI助手 v0.3：发送消息（带 System 提示词 + 会话记忆）
     */
    @IgnoreAuth
    @PostMapping(value = "/chat/send")
    public SseEmitter send(@RequestBody ChatSendParams chatSendParams, HttpServletRequest httpRequest) {
        AssertUtils.assertNotEmpty("参数异常", chatSendParams);
        String userMessage = chatSendParams.getContent();
        AssertUtils.assertNotEmpty("至少发送一条消息", userMessage);
        if (userMessage.length() > MAX_CONTENT_LENGTH) {
            throw new JeecgBootException("消息内容不能超过" + MAX_CONTENT_LENGTH + "个字符");
        }

        // 会话id：如果是新会话则生成一个。Redis Key: airag:chat:用户名:会话id
        String conversationId = oConvertUtils.getString(chatSendParams.getConversationId(), UUIDGenerator.generate());
        chatSendParams.setConversationId(conversationId);
        // 前端传过来的 topicId 如果没有值则随机生成一个
        String topicId = oConvertUtils.getString(chatSendParams.getTopicId(), UUIDGenerator.generate());
        chatSendParams.setTopicId(topicId);
        // 设置登录用户名称，作用：组装 redis 的 key
        // 未登录时与 JeecgBoot 保持一致，用 sessionId 代替，这样原有的会话列表接口也能查到
        String username = getUsername(httpRequest);
        boolean anonymous = oConvertUtils.isEmpty(username);
        if (anonymous) {
            username = httpRequest.getSession().getId();
        }
        chatSendParams.setUsername(username);
        chatSendParams.setAnonymous(anonymous);
        // 每次请求都生成一个新的 requestId
        String requestId = UUIDGenerator.generate();
        // SseEmitter：基于 HTTP 的服务器推送技术，仅允许服务器向客户端发送事件流
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // 构建请求：用户提示词
        ChatClient.ChatClientRequestSpec chat = chatClient
                .prompt()
                .user(userMessage);

        // 3.4 获取 app 信息，设置 System 提示词；appId 传了就必须能查到该应用
        Integer msgNum = null;
        AiragApp app = null;
        if (StrUtil.isNotBlank(chatSendParams.getAppId())) {
            app = airagAppService.getById(chatSendParams.getAppId());
            if (null == app) {
                throw new JeecgBootException("应用不存在或已删除");
            }
            if (StrUtil.isNotBlank(app.getPrompt())) {
                chat.system(app.getPrompt());
            }
            msgNum = app.getMsgNum();
        }

        // 3.5 添加会话记忆：把 会话id、topicId、用户名、appId 一起以 JSON 传给 RedisChatMemory
        final Integer retrieveSize = msgNum;
        chat.advisors(a -> {
            a.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, JSON.toJSONString(chatSendParams));
            // APP 中配置了“历史消息数”，则只带最近 N 条历史给大模型
            if (retrieveSize != null && retrieveSize > 0) {
                a.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY, retrieveSize);
            }
        });

        // 4.6 RAG：应用配置了知识库时加入 QuestionAnswerAdvisor。
        // 这段逻辑独立于 System 提示词是否存在，应用没写提示词也要能用知识库。
        if (app != null && StrUtil.isNotBlank(app.getKnowledgeIds())) {
            List<Object> knowledgeIdList = new ArrayList<>();
            for (String knowId : app.getKnowledgeIds().split(",")) {
                String trimKnowId = knowId.trim();
                // PgVector 的过滤条件会直接把字符串值拼进 SQL 的 jsonpath，只接受安全 id
                if (StrUtil.isNotBlank(trimKnowId)
                        && EmbeddingHandler.isValidEmbedId(trimKnowId)
                        && !knowledgeIdList.contains(trimKnowId)) {
                    knowledgeIdList.add(trimKnowId);
                }
                if (knowledgeIdList.size() >= MAX_KNOWLEDGE_IDS) {
                    break;
                }
            }
            if (!knowledgeIdList.isEmpty()) {
                Filter.Expression knowledgeFilter = new FilterExpressionBuilder()
                        .in(EmbeddingHandler.EMBED_STORE_METADATA_KNOWLEDGEID, knowledgeIdList)
                        .build();
                SearchRequest searchRequest = SearchRequest.builder()
                        .query(userMessage)
                        .topK(2)
                        .similarityThreshold(RAG_SIMILARITY_THRESHOLD)
                        .filterExpression(knowledgeFilter)
                        .build();
                chat.advisors(new QuestionAnswerAdvisor(pgVectorStore, searchRequest));
            }
        }

        // 工具调用：只对登录用户挂载，匿名请求不开放库存、商品、波次查询
        if (!Boolean.TRUE.equals(chatSendParams.getAnonymous())) {
            chat.tools(wmsInventoryTools, wmsProductTools, wmsPickRouteTools);
        }

        // 调用大模型（流式）
        Flux<String> flux = chat.stream().content();

        // 是否正在思考（兼容推理模型的 <think> 标签）
        AtomicBoolean isThinking = new AtomicBoolean(false);

        // 用 Swap 保存订阅：即使回调在 subscribe 返回之前触发，也能正确释放订阅
        Disposable.Swap subscriptionRef = Disposables.swap();
        // 标记 emitter 是否已经结束，避免超时/断开后继续 send 或重复 complete
        AtomicBoolean emitterClosed = new AtomicBoolean(false);
        emitter.onCompletion(() -> {
            emitterClosed.set(true);
            subscriptionRef.dispose();
        });
        emitter.onTimeout(() -> {
            subscriptionRef.dispose();
            if (emitterClosed.compareAndSet(false, true)) {
                emitter.complete();
            }
        });
        emitter.onError(e -> {
            emitterClosed.set(true);
            subscriptionRef.dispose();
        });

        // 发送开头的数据：前端要求的格式为 开始标记 -> 中间数据 -> 结束标记
        try {
            sendMessage(emitter, conversationId, topicId, requestId, "> ", "MESSAGE");
            sendMessage(emitter, conversationId, topicId, requestId, "\n> ", "MESSAGE");
        } catch (Exception e) {
            log.error("推送消息头给浏览器失败", e);
            if (emitterClosed.compareAndSet(false, true)) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        // 订阅大模型的返回
        Disposable subscription = flux.subscribe(
                // onNext：处理正常数据
                data -> {
                    if (emitterClosed.get()) {
                        subscriptionRef.dispose();
                        return;
                    }
                    if ("<think>".equals(data)) {
                        isThinking.set(true);
                        data = "> ";
                    }
                    if ("</think>".equals(data)) {
                        isThinking.set(false);
                        data = "\n\n";
                    }
                    if (isThinking.get() && null != data && data.contains("\n")) {
                        data = "\n> ";
                    }
                    try {
                        sendMessage(emitter, conversationId, topicId, requestId, data, "MESSAGE");
                    } catch (Exception e) {
                        log.error("推送大模型返回数据给浏览器失败", e);
                        subscriptionRef.dispose();
                        if (emitterClosed.compareAndSet(false, true)) {
                            emitter.completeWithError(e);
                        }
                    }
                },
                // onError：处理错误
                error -> {
                    log.error("大模型调用异常", error);
                    subscriptionRef.dispose();
                    if (emitterClosed.compareAndSet(false, true)) {
                        emitter.completeWithError(error);
                    }
                },
                // onComplete：处理完成事件，发送结束标记
                () -> {
                    if (!emitterClosed.compareAndSet(false, true)) {
                        return;
                    }
                    try {
                        sendEndMessage(emitter, conversationId, topicId, requestId);
                    } catch (Exception e) {
                        log.error("推送结束标记给浏览器失败", e);
                        emitter.completeWithError(e);
                        return;
                    }
                    emitter.complete();
                }
        );
        subscriptionRef.update(subscription);
        return emitter;
    }

    /**
     * 从请求头的 token 中解析登录用户名
     */
    private String getUsername(HttpServletRequest httpRequest) {
        try {
            String token = (null != httpRequest)
                    ? TokenUtils.getTokenByRequest(httpRequest)
                    : TokenUtils.getTokenByRequest();
            if (TokenUtils.verifyToken(token, sysBaseApi, redisUtil)) {
                return JwtUtil.getUsername(token);
            }
        } catch (Exception e) {
            log.warn("解析登录用户失败：{}", e.getMessage());
        }
        return null;
    }

    /**
     * 辅助方法：向客户端发送消息
     */
    private void sendMessage(SseEmitter emitter, String conversationId, String topicId,
                             String requestId, String message, String event) throws IOException {
        Map<String, Object> response = new HashMap<>();
        response.put("conversationId", conversationId);
        response.put("topicId", topicId);
        response.put("requestId", requestId);
        response.put("event", event);

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("message", message);
        response.put("data", messageData);

        emitter.send(SseEmitter.event().data(JSONObject.toJSONString(response)));
    }

    /**
     * 辅助方法：发送结束消息
     */
    private void sendEndMessage(SseEmitter emitter, String conversationId, String topicId,
                                String requestId) throws IOException {
        Map<String, Object> endResponse = new HashMap<>();
        endResponse.put("event", "MESSAGE_END");
        endResponse.put("flowId", null);
        endResponse.put("requestId", requestId);
        endResponse.put("conversationId", conversationId);
        endResponse.put("topicId", topicId);
        endResponse.put("data", null);

        emitter.send(SseEmitter.event().data(JSONObject.toJSONString(endResponse)));
    }
}
