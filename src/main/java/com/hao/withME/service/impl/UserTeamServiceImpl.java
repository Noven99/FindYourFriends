package com.hao.withME.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hao.withME.model.domain.UserTeam;
import com.hao.withME.service.UserTeamService;
import com.hao.withME.mapper.UserTeamMapper;
import org.springframework.stereotype.Service;

/**
* @author 86182
* @description 针对表【user_team(用户队伍关系)】的数据库操作Service实现
* @createDate 2025-12-22 20:19:59
*/
@Service
public class UserTeamServiceImpl extends ServiceImpl<UserTeamMapper, UserTeam>
    implements UserTeamService{

}




