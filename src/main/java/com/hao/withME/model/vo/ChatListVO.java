package com.hao.withME.model.vo;

import lombok.Data;
import java.util.Date;

/**
 * 聊天列表展示对象
 */
@Data
public class ChatListVO {
    /**
     * 目标ID (如果是私聊就是对方userId，如果是群聊就是teamId)
     */
    private Long targetId;

    /**
     * 目标名称 (对方昵称 或 群名称)
     */
    private String targetName;


    /**
     * 聊天类型: 0-私聊, 1-群聊
     */
    private Integer type;

    /**
     * 最新一条消息内容
     */
    private String lastMessage;

    /**
     * 最新消息时间
     */
    private Date lastMessageTime;

    /**
     * 未读消息
     */
    private Integer unreadNum;

}