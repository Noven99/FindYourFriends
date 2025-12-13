package com.hao.withME.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hao.withME.model.domain.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
* @author 86182
* @description 针对表【user(用户)】的数据库操作Mapper
* @createDate 2025-07-03 23:01:12
* @Entity com.hao.usercenter.model.domain.User
*/
public interface UserMapper extends BaseMapper<User> {

    List<User> selectList(QueryWrapper<Object> queryWrapper);
}




