package com.hao.withME.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hao.withME.common.BaseResponse;
import com.hao.withME.common.ErrorCode;
import com.hao.withME.common.ResultUtils;
import com.hao.withME.exception.BusinessException;
import com.hao.withME.model.domain.User;
import com.hao.withME.model.request.UserLoginRequest;
import com.hao.withME.model.request.UserRegisterRequest;
import com.hao.withME.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hao.withME.constant.UserConstant.ADMIN_ROLE;
import static com.hao.withME.constant.UserConstant.USER_LOGIN_STATE;

@RestController
@RequestMapping("/user")
@CrossOrigin(origins = {"http://localhost:3000"})
@Slf4j
public class UserController {

    @Resource
    private UserService userService;


    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    //用户注册接口
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        //1，判断传入进来的是否为空
        if (userRegisterRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        //2，获取传入进来的请求参数的数据
        String account = userRegisterRequest.getUserAccount();
        String password = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        String planetCode = userRegisterRequest.getPlanetCode();
        //3，对请求参数进行校验（这里的校验不涉及业务）
        if (StringUtils.isAnyBlank(account, password, checkPassword, planetCode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        long result = userService.userRegister(account, password, checkPassword, planetCode);
        return ResultUtils.success(result);
    }

    //用户登录接口
    @PostMapping("/login")
    public BaseResponse<User> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        //1，判断传入进来的是否为空
        if (userLoginRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        //2，获取传入进来的请求参数的数据
        String account = userLoginRequest.getUserAccount();
        String password = userLoginRequest.getUserPassword();
        //3，对请求参数进行校验（这里的校验不涉及业务）
        if (StringUtils.isAnyBlank(account, password)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.userLogin(account, password, request);
        return ResultUtils.success(user);
    }

    //用户注销接口
    @PostMapping("/logout")
    public BaseResponse<Integer> userLogout(HttpServletRequest request) {
        //1，判断传入进来的是否为空
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
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
        if (!userService.isAdmin(request)) {
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


    //推荐用户
    @GetMapping("/recommend")
    public BaseResponse<Page<User>> recommendUsers(long pageSize, long pageNum, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        String redisKey = String.format("Hao:user:recommend:%s", loginUser.getId());
        ValueOperations<String, Object> valueOperations = redisTemplate.opsForValue();
        // 如果有缓存，直接读缓存
        Page<User> userPage = (Page<User>) valueOperations.get(redisKey);
        if (userPage != null) {
            return ResultUtils.success(userPage);
        }

        //2，针对 User 实体类对应的表，构造 sql 语句（查询）
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        userPage = userService.page(new Page<>(pageNum, pageSize), queryWrapper);
        // 写缓存
        try {
            valueOperations.set(redisKey, userPage, 30000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("redis set key error", e);
        }
        return ResultUtils.success(userPage);
    }

    //用户更新
    @PostMapping("/update")
    public BaseResponse<Integer> updateUser(@RequestBody User user, HttpServletRequest request) {
        //1,校验参数是否为空
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        //2,校验权限
        User loginUser = userService.getLoginUser(request);

        //3,实际修改
        int result = userService.updateUser(user, loginUser);
        return ResultUtils.success(result);
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
        if (!userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }

        if (id <= 0) return null;
        boolean b = userService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 根据标签查询用户
     *
     * @param tagNameList
     * @return
     */
    @GetMapping("/search/tags")
    public BaseResponse<List<User>> searchUsersByTags(@RequestParam(required = false) List<String> tagNameList) {
        if (CollectionUtils.isEmpty(tagNameList)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        List<User> userList = userService.searchUsersByTags(tagNameList);
        return ResultUtils.success(userList);
    }


    /**
     * 获取最匹配的用户
     *
     * @param num
     * @param request
     * @return
     */
    @GetMapping("/match")
    public BaseResponse<List<User>> matchUsers(long num, HttpServletRequest request) {
        if (num <= 0 || num > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        return ResultUtils.success(userService.matchUsers(num, user));
    }

}
