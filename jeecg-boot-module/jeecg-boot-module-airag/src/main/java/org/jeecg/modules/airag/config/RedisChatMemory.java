package org.jeecg.modules.airag.config;

import com.alibaba.fastjson.JSON;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.UUIDGenerator;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.airag.app.consts.AiAppConsts;
import org.jeecg.modules.airag.app.entity.AiragApp;
import org.jeecg.modules.airag.app.service.IAiragAppService;
import org.jeecg.modules.airag.app.service.IAiragChatService;
import org.jeecg.modules.airag.app.vo.ChatConversation;
import org.jeecg.modules.airag.app.vo.ChatSendParams;
import org.jeecg.modules.airag.common.handler.IAIChatHandler;
import org.jeecg.modules.airag.common.vo.MessageHistory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的会话记忆
 * <p>
 * 数据结构：String
 * Key   ：airag:chat:用户名:会话id
 * Value ：ChatConversation（与 JeecgBoot 原有格式一致，原有的会话列表/历史/删除接口可直接复用）
 * <p>
 * ChatMemory 接口的第一个参数本来是 conversationId，
 * 这里由 ChatController 传入 ChatSendParams 的 JSON 串，从而同时拿到 会话id、topicId、用户名、appId。
 */
@Slf4j
@Component
public class RedisChatMemory implements ChatMemory {

    private static final String REDIS_KEY_PREFIX = "airag:chat:";

    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private IAiragChatService airagChatService;
    @Autowired
    private IAiragAppService airagAppService;
    @Autowired
    private IAIChatHandler aiChatHandler;

    /**
     * 添加消息到会话历史
     * 调用时机：1. 用户消息发给大模型之前  2. 大模型回复完成之后
     */
    @Override
    public void add(String chatSendParamsJson, List<Message> messagesList) {
        if (messagesList == null || messagesList.isEmpty()) {
            return;
        }
        // 解析请求参数，传入的参数是 json 字符串；
        // 其它调用方（如测试接口）可能传入默认会话 id，拿不到用户名时直接跳过记忆，不能让整个对话失败
        ChatSendParams chatSendParams = parseChatSendParams(chatSendParamsJson);
        if (chatSendParams == null) {
            return;
        }
        String conversationId = chatSendParams.getConversationId();
        String topicId = chatSendParams.getTopicId();
        String redisKey = buildRedisKey(chatSendParams.getUsername(), conversationId);

        // 获取 app 信息
        AiragApp app = null;
        if (oConvertUtils.isNotEmpty(chatSendParams.getAppId())) {
            app = airagAppService.getById(chatSendParams.getAppId());
        }
        if (oConvertUtils.isObjectEmpty(app)) {
            app = new AiragApp();
            app.setId(AiAppConsts.DEFAULT_APP_ID);
        }

        // 查询原来的会话，没有则新建
        ChatConversation chatConversation = (ChatConversation) redisTemplate.boundValueOps(redisKey).get();
        if (null == chatConversation) {
            chatConversation = createConversation(conversationId);
        }
        chatConversation.setApp(app);

        // 逐条追加（一般只有 1 条：用户问题 或 AI 回复）
        boolean changed = false;
        for (Message message : messagesList) {
            // SpringAIMsg 是按照 Spring AI 消息内容定义的
            SpringAIMsg msg = new SpringAIMsg(message);
            MessageType messageType = msg.getMessageType();
            String text = msg.getText();
            if (oConvertUtils.isEmpty(text)) {
                continue;
            }

            ChatMessage chatMessage;
            if (messageType == MessageType.USER) {
                // 第一条用户消息作为会话标题
                if (oConvertUtils.isEmpty(chatConversation.getTitle())) {
                    chatConversation.setTitle(text.length() > 5 ? text.substring(0, 5) : text);
                }
                chatMessage = aiChatHandler.buildUserMessage(text, null);
            } else if (messageType == MessageType.ASSISTANT) {
                chatMessage = new AiMessage(text);
            } else {
                // SYSTEM / TOOL 消息不记录到会话历史
                continue;
            }
            // 追加到会话历史（第一个参数是本次发送的消息列表，这里用不到，传一个空列表即可）
            airagChatService.appendMessage(new ArrayList<>(), chatMessage, chatConversation, topicId);
            changed = true;
        }

        // 保存到 redis：匿名会话（用 sessionId 当用户名）按临时会话设置过期时间，登录用户保持原版行为
        if (changed) {
            BoundValueOperations redisKeyOps = redisTemplate.boundValueOps(redisKey);
            redisKeyOps.set(chatConversation);
            if (Boolean.TRUE.equals(chatSendParams.getAnonymous())) {
                redisKeyOps.expire(3, TimeUnit.HOURS);
            }
        }
    }

    /**
     * 查询会话历史
     * 调用时机：用户消息发给大模型之前，取出历史消息和本次问题一起发给大模型
     *
     * @param lastN 最多返回最近 N 条（Controller 会把 APP 配置的“历史消息数”传进来，默认 100）
     */
    @Override
    public List<Message> get(String chatSendParamsJson, int lastN) {
        ChatSendParams chatSendParams = parseChatSendParams(chatSendParamsJson);
        if (chatSendParams == null) {
            return List.of();
        }
        String topicId = chatSendParams.getTopicId();
        String redisKey = buildRedisKey(chatSendParams.getUsername(), chatSendParams.getConversationId());

        ChatConversation chatConversation = (ChatConversation) redisTemplate.boundValueOps(redisKey).get();
        if (chatConversation == null || chatConversation.getMessages() == null) {
            return List.of();
        }

        List<Message> result = new ArrayList<>();
        for (MessageHistory history : chatConversation.getMessages()) {
            // 与 JeecgBoot 一致：只取当前话题的消息（前端“清除上下文”会开启新话题）
            if (oConvertUtils.isNotEmpty(topicId) && !Objects.equals(topicId, history.getTopicId())) {
                continue;
            }
            String content = history.getContent() == null ? "" : history.getContent();
            switch (String.valueOf(history.getRole())) {
                case "user" -> result.add(new UserMessage(content, List.of(), Map.of()));
                case "ai" -> result.add(new AssistantMessage(content, Map.of(), List.of(), List.of()));
                // 其它类型直接跳过，不抛异常，避免整个对话失败
                default -> log.warn("[RedisChatMemory]忽略不支持的消息类型:{}", history.getRole());
            }
        }

        // 只保留最近 lastN 条，避免上下文过长
        if (lastN > 0 && result.size() > lastN) {
            result = new ArrayList<>(result.subList(result.size() - lastN, result.size()));
        }
        return result;
    }

    /**
     * 清除会话历史（暂时不用，清除会话使用 JeecgBoot 原有接口）
     */
    @Override
    public void clear(String chatSendParamsJson) {
        ChatSendParams chatSendParams = parseChatSendParams(chatSendParamsJson);
        if (chatSendParams == null) {
            return;
        }
        redisTemplate.delete(buildRedisKey(chatSendParams.getUsername(), chatSendParams.getConversationId()));
    }

    /**
     * 创建新会话
     */
    private ChatConversation createConversation(String conversationId) {
        conversationId = oConvertUtils.getString(conversationId, UUIDGenerator.generate());
        ChatConversation chatConversation = new ChatConversation();
        chatConversation.setId(conversationId);
        chatConversation.setCreateTime(new Date());
        return chatConversation;
    }

    /**
     * 解析会话参数。
     * <p>
     * MessageChatMemoryAdvisor 在没有会话参数时默认传 "default"，这不是合法 JSON；
     * 用户名或会话 id 为空时也无法组装 Redis Key，这几种情况都返回 null，由调用方跳过会话记忆。
     * 不打印原始参数，避免把用户消息内容写进日志。
     */
    private ChatSendParams parseChatSendParams(String chatSendParamsJson) {
        if (oConvertUtils.isEmpty(chatSendParamsJson)) {
            return null;
        }
        try {
            ChatSendParams chatSendParams = JSON.parseObject(chatSendParamsJson, ChatSendParams.class);
            if (chatSendParams == null
                    || oConvertUtils.isEmpty(chatSendParams.getUsername())
                    || oConvertUtils.isEmpty(chatSendParams.getConversationId())) {
                log.warn("[RedisChatMemory]会话参数不完整，跳过会话记忆");
                return null;
            }
            return chatSendParams;
        } catch (Exception e) {
            log.warn("[RedisChatMemory]会话参数不是合法 JSON，跳过会话记忆");
            return null;
        }
    }

    /**
     * Key: airag:chat:用户名:会话id
     */
    private String buildRedisKey(String username, String conversationId) {
        if (oConvertUtils.isEmpty(username)) {
            throw new JeecgBootException("会话用户不能为空");
        }
        return REDIS_KEY_PREFIX + username + ":" + conversationId;
    }
}
