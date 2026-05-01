package com.neon.controller;

import com.neon.pojo.Comment;
import com.neon.pojo.Message;
import com.neon.pojo.Resource;
import com.neon.pojo.Users;
import com.neon.service.ResourceService;
import com.neon.service.AsyncService;
import com.neon.dao.MessageDao;
import com.neon.dao.UsersDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/resources")
@CrossOrigin(origins = "*")  // 允许跨域
public class ResourceController {
    @Autowired
    private ResourceService resourceService;
    
    @Autowired
    private UsersDao usersDao;
    
    @Autowired
    private MessageDao messageDao;
    
    @Autowired
    private AsyncService asyncService;

    // 获取资源列表（只返回卡片所需字段，也可直接返回完整Resource，前端自行提取）
    @GetMapping("/list")
    @ResponseBody
    public List<Resource> list() {
        return resourceService.getAllResources();
    }

    // 获取资源详情（包含评论）
    @GetMapping("/{id}")
    @ResponseBody
    public Map<String, Object> detail(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            Resource resource = resourceService.getResourceDetail(id);
            if (resource == null) {
                result.put("success", false);
                result.put("message", "资源不存在");
                return result;
            }
            List<Comment> comments = resourceService.getCommentsByResourceId(id);
        
            // 为每个评论添加用户头像信息
            List<Map<String, Object>> commentsWithAvatar = new ArrayList<>();
            for (Comment comment : comments) {
                Map<String, Object> commentMap = new HashMap<>();
                commentMap.put("id", comment.getId());
                commentMap.put("resourceId", comment.getResourceId());
                commentMap.put("author", comment.getAuthor());
                commentMap.put("content", comment.getContent());
                commentMap.put("createdAt", comment.getCreatedAt());
                commentMap.put("likes", comment.getLikes());
                commentMap.put("dislikes", comment.getDislikes());
                commentMap.put("parentId", comment.getParentId());
                
                // 获取用户头像
                List<Users> users = usersDao.findByUserName(comment.getAuthor());
                if (!users.isEmpty() && users.get(0).getAvatar() != null) {
                    String base64Avatar = Base64.getEncoder().encodeToString(users.get(0).getAvatar());
                    String avatarUrl = "data:image/jpeg;base64," + base64Avatar;
                    commentMap.put("avatar", avatarUrl);
                } else {
                    commentMap.put("avatar", null);
                }
                
                // 处理回复
                List<Map<String, Object>> repliesWithAvatar = new ArrayList<>();
                for (Comment reply : comment.getReplies()) {
                    Map<String, Object> replyMap = new HashMap<>();
                    replyMap.put("id", reply.getId());
                    replyMap.put("resourceId", reply.getResourceId());
                    replyMap.put("author", reply.getAuthor());
                    replyMap.put("content", reply.getContent());
                    replyMap.put("createdAt", reply.getCreatedAt());
                    replyMap.put("likes", reply.getLikes());
                    replyMap.put("dislikes", reply.getDislikes());
                    replyMap.put("parentId", reply.getParentId());
                    
                    // 获取回复用户的头像
                    List<Users> replyUsers = usersDao.findByUserName(reply.getAuthor());
                    if (!replyUsers.isEmpty() && replyUsers.get(0).getAvatar() != null) {
                        String base64Avatar = Base64.getEncoder().encodeToString(replyUsers.get(0).getAvatar());
                        String avatarUrl = "data:image/jpeg;base64," + base64Avatar;
                        replyMap.put("avatar", avatarUrl);
                    } else {
                        replyMap.put("avatar", null);
                    }
                    
                    repliesWithAvatar.add(replyMap);
                }
                commentMap.put("replies", repliesWithAvatar);
                commentsWithAvatar.add(commentMap);
            }
            
            result.put("success", true);
            result.put("resource", resource);
            result.put("comments", commentsWithAvatar);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "获取资源详情失败: " + e.getMessage());
        }
        return result;
    }

    // 发布资源
    @PostMapping("/publish")
    @ResponseBody
    public Map<String, Object> publish(@RequestBody Resource resource) {
        Map<String, Object> result = new HashMap<>();
        try {
            Resource savedResource = resourceService.saveResource(resource);
            result.put("success", true);
            result.put("message", "资源发布成功");
            result.put("resource", savedResource);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "资源发布失败：" + e.getMessage());
        }
        return result;
    }

    // 发表评论
    @PostMapping("/comment")
    @ResponseBody
    public Map<String, Object> addComment(@RequestBody Comment comment) {
        Map<String, Object> result = new HashMap<>();
        try {
            comment.setCreatedAt(java.time.LocalDateTime.now());
            Comment savedComment = resourceService.saveComment(comment);
            result.put("success", true);
            result.put("message", "评论发表成功");
            result.put("comment", savedComment);
            
            // 处理评论回复的消息通知
            if (comment.getParentId() != null && comment.getParentId() > 0) {
                // 查找父评论
                Comment parentComment = resourceService.getCommentById(comment.getParentId());
                if (parentComment != null && parentComment.getAuthor() != null && !parentComment.getAuthor().equals(comment.getAuthor())) {
                    // 异步发送消息通知
                    asyncService.sendMessageNotification(
                            parentComment.getAuthor(),
                            comment.getAuthor() + " 回复了你的评论：" + comment.getContent(),
                            "comment_reply",
                            comment.getId()
                    );
                }
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "评论发表失败：" + e.getMessage());
        }
        return result;
    }

    // 点赞评论
    @PostMapping("/comment/{id}/like")
    @ResponseBody
    public Map<String, Object> likeComment(@PathVariable Long id, @RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String userId = request.get("userId");
            if (userId == null || userId.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "用户ID不能为空");
                return result;
            }
            
            Map<String, Object> serviceResult = resourceService.likeComment(userId, id);
            result.put("success", true);
            result.putAll(serviceResult);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "点赞失败：" + e.getMessage());
        }
        return result;
    }

    // 检查用户是否点赞过某个评论
    @GetMapping("/comment/{id}/user/{userId}/liked")
    @ResponseBody
    public Map<String, Object> checkUserLiked(@PathVariable Long id, @PathVariable String userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean hasLiked = resourceService.hasUserLikedComment(userId, id);
            Long likes = resourceService.getCommentLikes(id);
            result.put("success", true);
            result.put("hasLiked", hasLiked);
            result.put("likes", likes);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "检查失败：" + e.getMessage());
        }
        return result;
    }
}