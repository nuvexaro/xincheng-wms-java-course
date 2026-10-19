package org.jeecg.modules.airag.app.service;

import dev.langchain4j.data.message.ChatMessage;
import jakarta.servlet.http.HttpServletRequest;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.airag.app.entity.AiragApp;
import org.jeecg.modules.airag.app.vo.AppDebugParams;
import org.jeecg.modules.airag.app.vo.ChatConversation;
import org.jeecg.modules.airag.app.vo.ChatSendParams;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * ai聊天
 *
 * @author chenrui
 * @date 2025/2/25 13:36
 */
public interface IAiragChatService {

    /**
     * 发送消息
     *
     * @param chatSendParams
     * @return
     */
    SseEmitter send(ChatSendParams chatSendParams);

    /**
     * 调试应用
     *
     * @param appDebugParams
     * @return
     */
    SseEmitter debugApp(AppDebugParams appDebugParams);

    /**
     * 停止响应
     *
     * @param requestId
     * @return
     */
    Result<?> stop(String requestId);

    /**
     * 获取所有对话
     *
     * @param appId
     * @return
     */
    Result<?> getConversations(String appId);

    /**
     * 获取对话聊天记录
     *
     * @param conversationId
     * @return
     */
    Result<?> getMessages(String conversationId);

    /**
     * 删除会话
     *
     * @param conversationId
     * @return
     */
    Result<?> deleteConversation(String conversationId);

    /**
     * 更新会话标题
     *
     * @param updateTitleParams
     * @return
     */
    Result<?> updateConversationTitle(ChatConversation updateTitleParams);

    /**
     * 清空消息
     *
     * @param conversationId
     * @return
     */
    Result<?> clearMessage(String conversationId);

    // ===================== 以下方法供 RedisChatMemory 等外部类复用 =====================

    /**
     * 追加消息：把消息加入本次消息列表，并记录到会话历史（系统消息不记录历史）
     *
     * @param messages         本次发送给大模型的消息列表
     * @param message          要追加的消息
     * @param chatConversation 会话
     * @param topicId          话题id
     */
    void appendMessage(List<ChatMessage> messages, ChatMessage message, ChatConversation chatConversation, String topicId);

    /**
     * 构造消息：从会话历史中取出指定话题的消息
     *
     * @param conversation 会话
     * @param topicId      话题id
     * @return 消息列表
     */
    List<ChatMessage> collateMessage(ChatConversation conversation, String topicId);

    /**
     * 获取会话，不存在则创建
     *
     * @param app            应用
     * @param conversationId 会话id
     * @return 会话
     */
    @NotNull
    ChatConversation getOrCreateChatConversation(AiragApp app, String conversationId);

    /**
     * 保存会话
     *
     * @param chatConversation 会话
     * @param temp             是否临时会话（临时会话3小时后过期）
     * @param httpRequest      请求，可为 null
     */
    void saveChatConversation(ChatConversation chatConversation, boolean temp, HttpServletRequest httpRequest);
}
