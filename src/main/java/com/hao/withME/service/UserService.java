package com.hao.withME.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hao.withME.model.domain.User;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

import static com.hao.withME.constant.UserConstant.ADMIN_ROLE;
import static com.hao.withME.constant.UserConstant.USER_LOGIN_STATE;

/**
 * @author 86182
 * @description 针对表【user(用户)】的数据库操作Service
 * @createDate 2025-06-28 23:24:12
 */
public interface UserService extends IService<User> {


    //用户注册
    long userRegister(String account, String password, String checkPassword, String planetCode);

    //用户登录
    User userLogin(String account, String password, HttpServletRequest request);

    //【用户脱敏】
    User getSafeUser(User origionUser);

    //用户注销
    int userLogout(HttpServletRequest request);

    /**
     * 根据标签查询用户
     *
     * @param tagNameList
     * @return
     */
    List<User> searchUsersByTags(List<String> tagNameList);

    //更新用户
    int updateUser(User user, User loginUser);

    //获取当前登录用户信息
    User getLoginUser(HttpServletRequest request);

    //【鉴权】
    boolean isAdmin(HttpServletRequest request);

    boolean isAdmin(User loginUser);

    /**
     * 匹配用户
     * @param num
     * @param loginUser
     * @return
     */
    List<User> matchUsers(long num, User loginUser);
}
