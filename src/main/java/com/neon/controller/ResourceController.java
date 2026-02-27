package com.neon.controller;

import com.neon.dao.UsersDao;
import com.neon.pojo.Comment;
import com.neon.pojo.Resource;
import com.neon.pojo.Users;
import com.neon.service.ResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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
        Resource resource = resourceService.getResourceDetail(id);
        if (resource == null) {
            return null;
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
            if (!users.isEmpty()) {
                commentMap.put("avatar", users.get(0).getAvatar());
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
                if (!replyUsers.isEmpty()) {
                    replyMap.put("avatar", replyUsers.get(0).getAvatar());
                } else {
                    replyMap.put("avatar", null);
                }
                
                repliesWithAvatar.add(replyMap);
            }
            commentMap.put("replies", repliesWithAvatar);
            commentsWithAvatar.add(commentMap);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("resource", resource);
        result.put("comments", commentsWithAvatar);
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