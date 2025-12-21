package com.hao.withME.model.request;

import lombok.Data;

import java.io.Serializable;

//用户登录请求体
@Data
public class UserLoginRequest implements Serializable {
    private static final long serialVersionUID = 8758293676191504220L;
    private String userAccount, userPassword;
}
