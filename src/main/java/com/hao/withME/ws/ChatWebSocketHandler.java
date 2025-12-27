package com.hao.withME.ws;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.hao.withME.mapper.UserTeamMapper;
import com.hao.withME.model.domain.ChatMessage;
import com.hao.withME.model.domain.User;
import com.hao.withME.model.domain.UserTeam;
import com.hao.withME.model.vo.ChatMessageVO;
import com.hao.withME.service.ChatMessageService;
import com.hao.withME.service.UserService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    @Resource
    private ChatMessageService chatMessageService;

    @Resource
    private UserTeamMapper userTeamMapper;
    @Resource
    private UserService userService;

    private static final Gson gson = new Gson();

    //只保留这一个 Map 存在线用户
    private static final ConcurrentHashMap<Long, WebSocketSession> onlineSessions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 建立连接成功
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = getUserIdFromSession(session);
        if (userId != null) {
            onlineSessions.put(userId, session);
            log.info("用户上线: {}", userId);
        } else {
            session.close();
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        // 简单防空判断
        if (payload == null || payload.isEmpty()) return;

        ChatMessage chatMessage = objectMapper.readValue(payload, ChatMessage.class);

        Long senderId = getUserIdFromSession(session);
        if (senderId == null) return;

        chatMessage.setSenderId(senderId);
        chatMessage.setCreateTime(new Date());
        chatMessage.setIsRead(0);

        // 1. 保存到数据库
        chatMessageService.save(chatMessage);

        // 2. === 关键修改开始 ===
        // 构建 VO 对象，准备填充 senderName
        ChatMessageVO messageVO = new ChatMessageVO();
        BeanUtils.copyProperties(chatMessage, messageVO);

        // 查询发送者信息
        User sender = userService.getById(senderId);
        if (sender != null) {
            // 这里对应你 SQL 里的 u.userNo AS senderName
            messageVO.setSenderName(sender.getUserNo());
        } else {
            messageVO.setSenderName("未知用户");
        }
        // === 关键修改结束 ===


        // 2. 广播/发送逻辑
        Integer type = chatMessage.getType();
        if (type == null || type == 0) {
            // === 私聊 ===
            Long receiverId = chatMessage.getReceiverId();
            if (receiverId != null) {
                sendToUser(receiverId, messageVO);
                sendToUser(senderId, messageVO);
            }
        } else if (type == 1) {
            // === 群聊 ===
            Long teamId = chatMessage.getTeamId();
            if (teamId != null) {
                sendToTeam(teamId, messageVO, senderId);
                sendToUser(senderId, messageVO);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long userId = getUserIdFromSession(session);
        if (userId != null) {
            onlineSessions.remove(userId);
        }
    }

    private void sendToUser(Long receiverId, Object message) {
        WebSocketSession receiverSession = onlineSessions.get(receiverId);
        if (receiverSession != null && receiverSession.isOpen()) {
            try {
                String jsonResp = gson.toJson(message);
                receiverSession.sendMessage(new TextMessage(jsonResp));
            } catch (IOException e) {
                log.error("发送消息失败", e);
            }
        }
    }

    /**
     * 修复点：使用 LambdaQueryWrapper 避免列名错误
     */
    private void sendToTeam(Long teamId, Object message, Long excludeUserId) {
        LambdaQueryWrapper<UserTeam> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserTeam::getTeamId, teamId);

        List<UserTeam> userTeams = userTeamMapper.selectList(queryWrapper);

        for (UserTeam userTeam : userTeams) {
            Long memberId = userTeam.getUserId();
            if (!memberId.equals(excludeUserId)) {
                sendToUser(memberId, message);
            }
        }
    }

    private Long getUserIdFromSession(WebSocketSession session) {
        try {
            if (session.getUri() == null) return null;
            String query = session.getUri().getQuery();
            if (query != null && query.contains("userId=")) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    String[] kv = pair.split("=");
                    if (kv.length == 2 && "userId".equals(kv[0])) {
                        return Long.parseLong(kv[1]);
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析 userId 失败", e);
        }
        return null;
    }

    // 如果后续要用 DTO，可以用这个类接收前端的结构
    @Data
    private static class ChatRequest {
        private Integer type;    // 0-私聊, 1-群聊
        private Long targetId;   // 接收者ID 或 队伍ID
        private String content;
    }
}