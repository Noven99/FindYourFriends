package com.hao.usercenter.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hao.usercenter.common.BaseResponse;
import com.hao.usercenter.common.ErrorCode;
import com.hao.usercenter.common.ResultUtils;
import com.hao.usercenter.exception.BusinessException;
import com.hao.usercenter.model.domain.User;
import com.hao.usercenter.model.request.UserLoginRequest;
import com.hao.usercenter.model.request.UserRegisterRequest;
import com.hao.usercenter.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.hao.usercenter.constant.UserConstant.ADMIN_ROLE;
import static com.hao.usercenter.constant.UserConstant.USER_LOGIN_STATE;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    //用户注册接口
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        //1，判断传入进来的是否为空
        if (userRegisterRequest == null) {
            throw new BusinessException(ErrorCode.PARAMAS_ERROR);
        }
        //2，获取传入进来的请求参数的数据
        String account = userRegisterRequest.getAccount();
        String password = userRegisterRequest.getPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        String planetCode = userRegisterRequest.getPlanetCode();
        //3，对请求参数进行校验（这里的校验不涉及业务）
        if (StringUtils.isAnyBlank(account, password, checkPassword, planetCode)) {
            throw new BusinessException(ErrorCode.PARAMAS_ERROR);
        }

        long result = userService.userRegister(account, password, checkPassword, planetCode);
        return ResultUtils.success(result);
    }

    //用户登录接口
    @PostMapping("/login")
    public BaseResponse<User> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        //1，判断传入进来的是否为空
        if (userLoginRequest == null) {
            throw new BusinessException(ErrorCode.PARAMAS_ERROR);
        }
        //2，获取传入进来的请求参数的数据
        String account = userLoginRequest.getAccount();
        String password = userLoginRequest.getPassword();
        //3，对请求参数进行校验（这里的校验不涉及业务）
        if (StringUtils.isAnyBlank(account, password)) {
            throw new BusinessException(ErrorCode.PARAMAS_ERROR);
        }
        User user = userService.userLogin(account, password, request);
        return ResultUtils.success(user);
    }

    //用户注销接口
    @PostMapping("/logout")
    public BaseResponse<Integer> userLogin(HttpServletRequest request) {
        //1，判断传入进来的是否为空
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMAS_ERROR);
        }
        int i = userService.userLogout(request);
        return ResultUtils.success(i);
    }

    /**
     * 获取用户的登录态
     *
     * @param request
     * @return
     */
    @GetMapping("/current")
    public BaseResponse<User> getCurrentUser(HttpServletRequest request) {
        Object userInfo = request.getSession().getAttribute(USER_LOGIN_STATE);
        User user = (User) userInfo;//转为 User 对象
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        Long userId = user.getId();
        //TODO 校验用户是否合法
        User updateUser = userService.getById(userId);
        User safeUser = userService.getSafeUser(updateUser);
        return ResultUtils.success(safeUser);
    }

    //根据用户名查询用户（仅管理员 HttpServletRequest request 获取用户的登录态判断是否为管理员）
    @GetMapping("/search")
    public BaseResponse<List<User>> searchUsers(String unsername, HttpServletRequest request) {
/*        //1，鉴权仅管理员可查询（获取用户登录态）
        Object userInfo = request.getSession().getAttribute(USER_LOGIN_STATE);
        User user = (User) userInfo;//转为 User 对象
        if (user == null || user.getRole() != ADMIN_ROLE) {
            return new ArrayList<>();//返回空数组
        }*/

        //将上面这段代码抽出来写成方法（鉴权）
        if (!isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }

        //2，针对 User 实体类对应的表，构造 sql 语句（查询）
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        //如果传入进来的字符不为空
        if (StringUtils.isNotBlank(unsername)) {
            //模糊查询，用户名含有传入进来的字符
            wrapper.like("username", unsername);
        }
        //返回条件查询的用户列表（脱敏）
        List<User> userList = userService.list(wrapper);
        List<User> safeUserList = userList.stream()           // (1) 将 List 转为 Stream
                .map(user -> userService.getSafeUser(user))   // (2) 对每个用户脱敏
                .collect(Collectors.toList());                // (3) 重新收集为 List
        return ResultUtils.success(safeUserList);
    }

    //根据 id 删除用户（仅管理员 HttpServletRequest request 获取用户的登录态判断是否为管理员）
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteUser(@RequestBody long id, HttpServletRequest request) {
   /*     //1，鉴权仅管理员可删除（获取用户登录态）
        Object userInfo = request.getSession().getAttribute(USER_LOGIN_STATE);
        User user = (User) userInfo;//转为 User 对象
        if (user == null || user.getRole() != ADMIN_ROLE) {
            return false;
        }*/

        //将上面这段代码抽出来写成方法（鉴权）
        if (!isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }

        if (id <= 0) return null;
        boolean b = userService.removeById(id);
        return ResultUtils.success(b);
    }

    //【鉴权】
    private boolean isAdmin(HttpServletRequest request) {
        //1，鉴权仅管理员可删除（获取用户登录态）
        Object userInfo = request.getSession().getAttribute(USER_LOGIN_STATE);
        User user = (User) userInfo;//转为 User 对象
        if (user == null || user.getUserRole() != ADMIN_ROLE) {
            return false;
        }
        return true;
    }
}
