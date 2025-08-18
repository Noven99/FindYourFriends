package com.hao.usercenter.service;

import com.hao.usercenter.model.domain.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

//用户服务测试
@SpringBootTest
class UserServiceTest {
    @Resource
    private UserService userService;

    @Test
    public void testAddUser() {
        User user = new User();

        user.setUsername("测试1号");
        user.setUserAccount("123456");
        user.setAvatarUrl("https://practice1103.oss-cn-beijing.aliyuncs.com/001.png");
        user.setGender(0);
        user.setUserPassword("123456");
        user.setPhone("123");
        user.setEmail("456");

        boolean result = userService.save(user);
        System.out.println(user.getId());
        assertTrue(result);
    }

    @Test
    void userRegister() {
        //1,测试（正常注册）
        String userAccount = "测试2号";
        String password = "12345678";
        String checkPassword = "12345678";
        long result = userService.userRegister(userAccount, password, checkPassword);
        Assertions.assertTrue(result > 0);

        //2,异常测试（账号名异常）
        userAccount = "";
        result = userService.userRegister(userAccount, password, checkPassword);
        Assertions.assertEquals(-1, result);

        userAccount = "测试2号@@@%&";
        result = userService.userRegister(userAccount, password, checkPassword);
        Assertions.assertEquals(-1, result);

        userAccount = "12";
        result = userService.userRegister(userAccount, password, checkPassword);
        Assertions.assertEquals(-1, result);

        //2,异常测试（密码异常）
        password = "123456";
        checkPassword = "123456";
        result = userService.userRegister(userAccount, password, checkPassword);
        Assertions.assertEquals(-1, result);

        password = "12345678";
        checkPassword = "123456";
        result = userService.userRegister(userAccount, password, checkPassword);
        Assertions.assertEquals(-1, result);

        //2,异常测试（账号名重复）
        userAccount = "测试2号";
        password = "123456789";
        checkPassword = "123456789";
        result = userService.userRegister(userAccount, password, checkPassword);
        Assertions.assertEquals(-1, result);
    }
}