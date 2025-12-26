package com.hao.withME.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hao.withME.model.domain.ChatMessage;
import com.hao.withME.model.vo.ChatListVO;
import com.hao.withME.model.vo.ChatMessageVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 查询混合聊天列表（私聊+群聊）
     * @param userId 当前用户ID
     * @param teamIds 当前用户所在的队伍ID列表
     * @return 聊天列表
     */
    List<ChatListVO> selectChatList(@Param("userId") Long userId, @Param("teamIds") List<Long> teamIds);

    List<ChatMessageVO> selectTeamChatHistory(@Param("teamId") Long teamId);


}