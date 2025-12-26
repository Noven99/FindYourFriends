package com.hao.withME.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hao.withME.model.domain.ChatMessage;
import com.hao.withME.model.vo.ChatListVO;

import java.util.List;

public interface ChatMessageService extends IService<ChatMessage> {
    List<ChatListVO> getChatList(Long userId);
}