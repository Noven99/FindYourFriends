package com.hao.withME.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hao.withME.common.ErrorCode;
import com.hao.withME.exception.BusinessException;
import com.hao.withME.mapper.UserMapper;
import com.hao.withME.model.domain.User;
import com.hao.withME.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.hao.withME.constant.UserConstant.ADMIN_ROLE;
import static com.hao.withME.constant.UserConstant.USER_LOGIN_STATE;

/**
 * @author 86182
 * @description 针对表【user(用户)】的数据库操作Service实现
 * @createDate 2025-06-28 23:24:12
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {
    @Resource
    private UserMapper userMapper;

    //盐值，混淆密码
    private static final String SALT = "Hao";


    //用户注册
    @Override
    public long userRegister(String account, String password, String checkPassword, String planetCode) {
        //1 校验参数（不能为空，长度，账号名不能重复）
        if (StringUtils.isAnyBlank(account, password, checkPassword, planetCode)) {
            throw new BusinessException(ErrorCode.PARAMAS_ERROR, "参数为空");
        }
        if (account.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMAS_ERROR, "用户账户过短");
        }
        if (password.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMAS_ERROR, "用户密码过短");
        }
        if (planetCode.length() > 5) {
            throw new BusinessException(ErrorCode.PARAMAS_ERROR, "星球编号过长");
        }

        //账号不能包含特殊字符（直接网上搜Java用户名正则表达式校验特殊字符）
        String exceptionCharacter = "\\pP|\\pS|\\s+";
        Matcher matcher = Pattern.compile(exceptionCharacter).matcher(account);
        if (matcher.find()) {
            throw new BusinessException(ErrorCode.PARAMAS_ERROR, "账户含有特殊字符");
        }

        //密码和确认密码需要一样
        if (!password.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMAS_ERROR, "两次密码不一致");
        }

        //账号不能重复（这里需要查询数据库，如果账号名本身就无效，没必要再去数据库里面查询是否有重复账号名字）
        QueryWrapper<User> wrapper = new QueryWrapper<>();//使用 MyBatis-Plus 的 QueryWrapper 来检查数据库里的 User 表
        wrapper.eq("userAccount", account);//查询 userAccount 这一列有多少个 等于传入进来的 account 的记录
        long count = userMapper.selectCount(wrapper);//统计数量
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMAS_ERROR, "已经存在相同账户，账户不能重复");
        }
        ; //如果数量>0，则表明有一样的账号，账号重复了

        //星球编号不能重复（和上面使用的是同一个查询对象）
        wrapper = new QueryWrapper<>();
        wrapper.eq("planetCode", planetCode);
        count = userMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMAS_ERROR, "星球编号不能重复");
        }

        //2 对密码进行加密（Spring自带的Md5单向加密）
        String addSaltPassword = SALT + password;
        String encryptPassword = DigestUtils.md5DigestAsHex(addSaltPassword.getBytes());

        //3 向数据库中插入数据
        User user = new User();
        user.setUserAccount(account);
        user.setUserPassword(encryptPassword);
        user.setPlanetCode(planetCode);
        boolean save = this.save(user);//这里还是调用service里面的方法
        if (!save) {
            throw new BusinessException(ErrorCode.PARAMAS_ERROR, "插入数据失败");
        }

        return user.getId();
    }

    //用户登录，HttpServletRequest 用来保存用户登录态
    @Override
    public User userLogin(String account, String password, HttpServletRequest request) {
        //1，校验账户和密码
        if (StringUtils.isAnyBlank(account, password)) {
            throw new BusinessException(ErrorCode.PARAMAS_ERROR, "参数为空");
        }
        if (account.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMAS_ERROR, "用户账户过短");
        }
        if (password.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMAS_ERROR, "用户密码过短");
        }

        //账号不能包含特殊字符（直接网上搜Java用户名正则表达式校验特殊字符）
        String exceptionCharacter = "\\pP|\\pS|\\s+";
        Matcher matcher = Pattern.compile(exceptionCharacter).matcher(account);
        if (matcher.find()) {
            throw new BusinessException(ErrorCode.PARAMAS_ERROR, "账户含有特殊字符");
        }

        //2，对用户的密码进行加密和数据库的密码进行比较，查询用户是否存在
        String addSaltPassword = SALT + password;
        String encryptPassword = DigestUtils.md5DigestAsHex(addSaltPassword.getBytes());

        //从数据库中查询账户和密码与传入进来的一样的结果
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("userAccount", account);
        wrapper.eq("userPassword", encryptPassword);

        //获取结果（拿到用户信息）
        User user = userMapper.selectOne(wrapper);
        //如果用户不存在
        if (user == null) {
            log.info("user login failed , account or password is not correct.");
            throw new BusinessException(ErrorCode.PARAMAS_ERROR, "用户不存在");

        }

        //3，用户脱敏
        User safeUser = getSafeUser(user);

        //4，记录用户的登录态（获取session,这里的session就相当于服务端的存储空间，可以理解成 map,往里面存键值对）
        request.getSession().setAttribute(USER_LOGIN_STATE, safeUser);

        return safeUser;
    }

    //【用户脱敏】
    @Override
    public User getSafeUser(User origionUser) {
        if (origionUser == null) {
            throw new BusinessException(ErrorCode.PARAMAS_ERROR, "用户不存在");
        }
        User safeUser = new User();

        safeUser.setId(origionUser.getId());
        safeUser.setUsername(origionUser.getUsername());
        safeUser.setUserAccount(origionUser.getUserAccount());
        safeUser.setAvatarUrl(origionUser.getAvatarUrl());
        safeUser.setPlanetCode(origionUser.getPlanetCode());
        safeUser.setGender(origionUser.getGender());
        safeUser.setPhone(origionUser.getPhone());
        safeUser.setEmail(origionUser.getEmail());
        safeUser.setUserStatus(origionUser.getUserStatus());
        safeUser.setCreateTime(origionUser.getCreateTime());
        safeUser.setUserRole(origionUser.getUserRole());
        safeUser.setTags(origionUser.getTags());

        return safeUser;
    }

    @Override
    public int userLogout(HttpServletRequest request) {
        request.getSession().removeAttribute(USER_LOGIN_STATE); //移除登陆态
        return 2;
    }

    /**
     * 根据标签查询用户
     *
     * @param tagNameList
     * @return
     */
    @Override
    public List<User> searchUsersByTags(List<String> tagNameList) {
        if (CollectionUtils.isEmpty(tagNameList)) {
            throw new BusinessException(ErrorCode.PARAMAS_ERROR);
        }

        // sql查询
       /* QueryWrapper<Object> queryWrapper = new QueryWrapper<>();
        //遍历标签 and 拼接模糊 查询
        for (String tagName : tagNameList) {
            queryWrapper = queryWrapper.like("tags", tagName);
        }*/

        //内存查询
        //1 先查询所有用户
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        List<User> userList = userMapper.selectList(queryWrapper);
        Gson gson = new Gson();

        //2 在内存中判断是否包含要求的标签
        return userList.stream().filter(user -> {
            String tagStr = user.getTags();
            if (StringUtils.isBlank(tagStr)) {
                return false;
            }
            Set<String> tempTagNameSet = gson.fromJson(
                    tagStr, new TypeToken<Set<String>>() {
                    }.getType());
            for (String s : tagNameList) {
                if (!tempTagNameSet.contains(s)) {
                    return false;
                }
            }
            return true;
        }).map(this::getSafeUser).collect(Collectors.toList());
    }

    //更新用户信息
    @Override
    public int updateUser(User user, User loginUser) {
        long userId = user.getId(); //获取要修改用户的id
        if (userId <= 0) { //判断能否在数据库中查到
            throw new BusinessException(ErrorCode.PARAMAS_ERROR);
        }
        //如果是管理员，更新用户信息
        //如果不是管理员，只更新用户自己的信息
        if (!isAdmin(loginUser) && user.getId() != loginUser.getId()) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        User oldUser = userMapper.selectById(userId);
        if (oldUser == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        return userMapper.updateById(user);
    }

    //获取当前用户登陆信息
    @Override
    public User getLoginUser(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        return (User) userObj;
    }

    //鉴权
    @Override
    public boolean isAdmin(HttpServletRequest request) {
        //1，鉴权仅管理员可删除（获取用户登录态）
        Object userInfo = request.getSession().getAttribute(USER_LOGIN_STATE);
        User user = (User) userInfo;//转为 User 对象
        if (user == null || user.getUserRole() != ADMIN_ROLE) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isAdmin(User loginUser) {
        if (loginUser == null || loginUser.getUserRole() != ADMIN_ROLE) {
            return false;
        }
        return true;
    }

}




