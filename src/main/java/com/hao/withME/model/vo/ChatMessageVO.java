package com.hao.withME.model.vo;

import com.hao.withME.model.domain.ChatMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ChatMessageVO extends ChatMessage {
    /**
     * 发送者显示的名称 (对应 User 表的 userNo 或 username)
     */
    private String senderName;
}