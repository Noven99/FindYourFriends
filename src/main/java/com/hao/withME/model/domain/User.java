package com.hao.withME.model.domain;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 用户
 * @TableName user
 */
@TableName(value ="user")
@Data
public class User implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private long id;

    /**
     * 用户编号
     */
    private String userNo;

    /**
     * 账号
     */
    private String userAccount;


    /**
     * 用户标签
     */
    private String tags;


    /**
     * 密码
     */
    private String userPassword;



    /**
     * 状态 0 - 正常
     */
    private Integer userStatus;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    /**
     * 鉴权：用户角色：0为普通用户，1为管理员
     */
    private Integer userRole;


    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}