package com.hao.withME.model.domain; // 你的包名可能不同

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

@TableName(value = "chat_message")
@Data
public class ChatMessage implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 必须加上 @TableField("数据库列名")
    @TableField("sender_id")
    private Long senderId;

    @TableField("receiver_id")
    private Long receiverId;

    @TableField("team_id")
    private Long teamId;

    private String content;

    private Integer type; // 0-私聊 1-群聊

    @TableField("create_time")
    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    // 新增：对应数据库的 is_read 字段（消息状态：0未读，1已读）
    @TableField("is_read")
    private Integer isRead;
}