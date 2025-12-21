package com.hao.withME.model.request;

import lombok.Data;

import java.io.Serializable;

//用户注册请求体
@Data
public class UserRegisterRequest implements Serializable {

    private static final long serialVersionUID = 2597448597570827208L;

    private String userAccount, userPassword, checkPassword;
    private String planetCode;
}
