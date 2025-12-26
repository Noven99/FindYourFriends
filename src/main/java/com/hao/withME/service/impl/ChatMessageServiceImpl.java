package com.hao.withME.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // 引入 Lambda
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hao.withME.mapper.ChatMessageMapper;
import com.hao.withME.mapper.UserTeamMapper;
import com.hao.withME.model.domain.ChatMessage;
import com.hao.withME.model.domain.UserTeam;
import com.hao.withME.model.vo.ChatListVO;
import com.hao.withME.service.ChatMessageService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements ChatMessageService {

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private UserTeamMapper userTeamMapper;

    @Override
    public List<ChatListVO> getChatList(Long currentUserId) {
        // 1. 先查询用户加入的所有队伍 ID
        // 修复点：使用 LambdaQueryWrapper
        LambdaQueryWrapper<UserTeam> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserTeam::getUserId, currentUserId);

        // 如果有逻辑删除字段，请保留；否则删除这行
        queryWrapper.eq(UserTeam::getIsDelete, 0);

        List<UserTeam> userTeams = userTeamMapper.selectList(queryWrapper);

        List<Long> teamIds = userTeams.stream()
                .map(UserTeam::getTeamId)
                .collect(Collectors.toList());

        if (teamIds.isEmpty()) {
            teamIds.add(-1L);
        }

        // 2. 调用 Mapper 执行复杂 SQL
        return chatMessageMapper.selectChatList(currentUserId, teamIds);
    }
}