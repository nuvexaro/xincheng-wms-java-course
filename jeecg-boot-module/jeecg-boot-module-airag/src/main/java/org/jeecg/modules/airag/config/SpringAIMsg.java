package org.jeecg.modules.airag.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 按照 Spring AI 的 Message 内容定义的消息对象
 * 用于在 RedisChatMemory 中统一读取消息类型和文本
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class SpringAIMsg {

    /** 消息类型：SYSTEM / USER / ASSISTANT / TOOL */
    private MessageType messageType;

    /** 消息文本 */
    private String text;

    /** 元数据 */
    private Map<String, Object> metadata = new HashMap<>();

    /** 工具调用（只有 AssistantMessage 有） */
    private List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();

    public SpringAIMsg(Message message) {
        this.messageType = message.getMessageType();
        this.text = message.getText() == null ? "" : message.getText();
        // 原 metadata 可能是不可变 Map，复制一份
        if (message.getMetadata() != null) {
            this.metadata = new HashMap<>(message.getMetadata());
        }
        if (message instanceof AssistantMessage am && am.getToolCalls() != null) {
            this.toolCalls = new ArrayList<>(am.getToolCalls());
        }
    }

    /**
     * 转回 Spring AI 的 Message
     * Spring AI 的构造方法不允许 text / metadata / toolCalls 为 null，这里统一兜底
     */
    public Message toMessage() {
        String safeText = text == null ? "" : text;
        Map<String, Object> safeMetadata = metadata == null ? new HashMap<>() : metadata;
        List<AssistantMessage.ToolCall> safeToolCalls = toolCalls == null ? List.of() : toolCalls;
        return switch (messageType) {
            case SYSTEM -> new SystemMessage(safeText);
            case USER -> new UserMessage(safeText, List.of(), safeMetadata);
            case ASSISTANT -> new AssistantMessage(safeText, safeMetadata, safeToolCalls, List.of());
            default -> throw new IllegalArgumentException("Unsupported message type: " + messageType);
        };
    }
}
