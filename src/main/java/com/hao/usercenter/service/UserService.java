package com.hao.usercenter.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hao.usercenter.model.domain.User;

import javax.servlet.http.HttpServletRequest;

/**
 * @author 86182
 * @description 针对表【user(用户)】的数据库操作Service
 * @createDate 2025-06-28 23:24:12
 */
public interface UserService extends IService<User> {



    //用户注册
    long userRegister(String account, String password, String checkPassword,String planetCode);

    //用户登录
    User userLogin(String account, String password, HttpServletRequest request);

    //【用户脱敏】
    User getSafeUser(User origionUser);

    //用户注销
    int userLogout(HttpServletRequest request);
}
