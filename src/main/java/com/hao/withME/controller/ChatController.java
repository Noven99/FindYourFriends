package com.hao.withME.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hao.withME.common.BaseResponse;
import com.hao.withME.common.ErrorCode;
import com.hao.withME.common.ResultUtils;
import com.hao.withME.mapper.ChatMessageMapper;
import com.hao.withME.model.domain.ChatMessage;
import com.hao.withME.model.domain.User;
import com.hao.withME.model.dto.ChatReadRequest;
import com.hao.withME.model.vo.ChatListVO;
import com.hao.withME.model.vo.ChatMessageVO;
import com.hao.withME.service.ChatMessageService;
import com.hao.withME.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/chat")
@CrossOrigin(origins = {"http://localhost:3000"})
public class ChatController {

    @Resource
    private ChatMessageService chatMessageService;

    @Resource
    private ChatMessageService chatService;

    @Resource
    private UserService userService;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    /**
     * 获取私聊历史记录
     * 请求参数：senderId (我), receiverId (对方)
     */
    @PostMapping("/private/history")
    public List<ChatMessage> getPrivateChatHistory(@RequestBody ChatMessage request) {
        // 1. 获取当前用户ID
        Long myId = request.getSenderId();
        // 2. 获取对方ID
        Long otherId = request.getReceiverId();

        LambdaQueryWrapper<ChatMessage> queryWrapper = new LambdaQueryWrapper<>();

        // 3. 构造双向查询条件: (A发给B) OR (B发给A)
        queryWrapper.and(wrapper -> wrapper
                .eq(ChatMessage::getSenderId, myId)
                .eq(ChatMessage::getReceiverId, otherId)
                .or()
                .eq(ChatMessage::getSenderId, otherId)
                .eq(ChatMessage::getReceiverId, myId)
        );

        // 只查私聊类型，防止混入脏数据
        queryWrapper.eq(ChatMessage::getType, 0);
        queryWrapper.orderByAsc(ChatMessage::getCreateTime);

        return chatMessageService.list(queryWrapper);
    }

    /**
     * 获取群聊历史记录
     * 请求参数：teamId
     */
    @PostMapping("/team/history")
    public List<ChatMessageVO> getTeamChatHistory(@RequestBody ChatMessage request) {
        Long teamId = request.getTeamId();
        if (teamId == null) {
            return new ArrayList<>();
        }
        // 使用自定义的关联查询
        return chatMessageMapper.selectTeamChatHistory(teamId);
    }

    /**
     * 消息已读接口
     * 场景：用户点击了和 targetId 的聊天框，将 targetId 发给我的所有未读消息设为已读
     */
    @PostMapping("/read")
    public BaseResponse<Boolean> readMessage(@RequestBody ChatReadRequest request, HttpServletRequest httpServletRequest) {
        // 1. 校验参数
        if (request == null || request.getTargetId() == null) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }

        // 2. 获取当前登录用户 (我是接收者)
        User loginUser = userService.getLoginUser(httpServletRequest);
        Long myId = loginUser.getId();
        Long targetId = request.getTargetId();

        // 3. 更新数据库
        // SQL 等价于: UPDATE chat_message SET is_read = 1
        // WHERE sender_id = targetId AND receiver_id = myId AND is_read = 0 AND type = 0
        UpdateWrapper<ChatMessage> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("sender_id", targetId);
        updateWrapper.eq("receiver_id", myId);
        updateWrapper.eq("type", 0); // 仅针对私聊，群聊逻辑复杂暂不处理
        updateWrapper.eq("is_read", 0); // 只更新未读的
        updateWrapper.set("is_read", 1);

        boolean result = chatMessageService.update(updateWrapper);
        return ResultUtils.success(result);
    }


    @GetMapping("/list")
    public BaseResponse<List<ChatListVO>> getChatList(HttpServletRequest request) {
        // 1. 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new RuntimeException("未登录");
        }

        // 2. 查询会话列表
        List<ChatListVO> chatList = chatService.getChatList(loginUser.getId());

        return ResultUtils.success(chatList);
    }
}