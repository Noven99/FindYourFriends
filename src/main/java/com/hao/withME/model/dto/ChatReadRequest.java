package com.hao.withME.model.dto;

import lombok.Data;

@Data
public class ChatReadRequest {
    /**
     * 对方的ID (即消息发送者)
     */
    private Long targetId;
    
    /**
     * 聊天类型 (可选，如果你的接口要兼容群聊已读逻辑)
     */
    private Integer type;
}