package com.neon.controller;

import com.neon.pojo.Message;
import com.neon.pojo.Comment;
import com.neon.pojo.Users;
import com.neon.dao.MessageDao;
import com.neon.dao.CommentDao;
import com.neon.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

@RestController
@RequestMapping("/messages")
@CrossOrigin(origins = "*")  // 允许跨域
public class MessageController {
    @Autowired
    private MessageDao messageDao;
    
    @Autowired
    private CommentDao commentDao;

    @Autowired
    private AuthService authService;
    
    // 获取用户未读消息数量
    @GetMapping("/unread/count/{userId}")
    @ResponseBody
    public Map<String, Object> getUnreadCount(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            Users user = authService.getAuthenticatedUser(token);
            if (user == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return result;
            }
            Long count = messageDao.countUnreadByUserId(resolveMessageUserId(user));
            result.put("success", true);
            result.put("count", count);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取未读消息数量失败：" + e.getMessage());
        }
        return result;
    }
    
    // 获取用户未读消息列表
    @GetMapping("/unread/{userId}")
    @ResponseBody
    public Map<String, Object> getUnreadMessages(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            Users user = authService.getAuthenticatedUser(token);
            if (user == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return result;
            }
            List<Message> messages = messageDao.findByUserIdAndIsReadFalse(resolveMessageUserId(user));
            
            // 处理消息列表，添加资源ID信息
            List<Map<String, Object>> messageList = new ArrayList<>();
            for (Message message : messages) {
                Map<String, Object> messageMap = new HashMap<>();
                messageMap.put("id", message.getId());
                messageMap.put("userId", message.getUserId());
                messageMap.put("content", message.getContent());
                messageMap.put("type", message.getType());
                messageMap.put("relatedId", message.getRelatedId());
                messageMap.put("isRead", message.getIsRead());
                messageMap.put("createdAt", message.getCreatedAt());
                
                // 如果是评论回复类型的消息，获取相关的资源ID
                if ("comment_reply".equals(message.getType()) && message.getRelatedId() != null) {
                    Comment comment = commentDao.findById(message.getRelatedId()).orElse(null);
                    if (comment != null) {
                        messageMap.put("resourceId", comment.getResourceId());
                    }
                }
                
                messageList.add(messageMap);
            }
            
            result.put("success", true);
            result.put("messages", messageList);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取未读消息列表失败：" + e.getMessage());
        }
        return result;
    }
    
    // 标记消息为已读
    @PostMapping("/read/{id}")
    @ResponseBody
    @Transactional
    public Map<String, Object> markAsRead(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            Users user = authService.getAuthenticatedUser(token);
            if (user == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return result;
            }
            Message message = messageDao.findById(id).orElse(null);
            if (message == null || !resolveMessageUserId(user).equals(message.getUserId())) {
                result.put("success", false);
                result.put("message", "无权操作该消息");
                return result;
            }
            messageDao.markAsRead(id);
            result.put("success", true);
            result.put("message", "消息已标记为已读");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "标记消息为已读失败：" + e.getMessage());
        }
        return result;
    }

    private String resolveMessageUserId(Users user) {
        if (user == null) {
            return "";
        }
        return user.getUserName() != null && !user.getUserName().trim().isEmpty()
                ? user.getUserName()
                : user.getAccount();
    }
}
