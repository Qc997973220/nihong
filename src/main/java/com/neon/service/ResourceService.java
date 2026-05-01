package com.neon.service;

import com.neon.dao.CommentDao;
import com.neon.dao.ResourceDao;
import com.neon.dao.UserActionDao;
import com.neon.pojo.Comment;
import com.neon.pojo.Resource;
import com.neon.pojo.UserAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ResourceService {
    @Autowired
    private ResourceDao resourceRepository;
    @Autowired
    private CommentDao commentRepository;
    @Autowired
    private UserActionDao userActionRepository;
    @Autowired
    private CacheService cacheService;

    public List<Resource> getAllResources() {
        // 尝试从缓存获取
        String cacheKey = cacheService.getResourceListKey();
        List<Resource> resources = (List<Resource>) cacheService.get(cacheKey);
        
        if (resources != null) {
            return resources;
        }
        
        // 从数据库获取
        resources = resourceRepository.findAll();
        
        // 缓存结果，过期时间30分钟
        cacheService.set(cacheKey, resources, 1800);
        
        return resources;
    }

    public Resource getResourceDetail(Long id) {
        // 尝试从缓存获取
        String cacheKey = cacheService.getResourceDetailKey(id);
        Resource resource = (Resource) cacheService.get(cacheKey);
        
        if (resource != null) {
            return resource;
        }
        
        // 从数据库获取
        resource = resourceRepository.findById(id).orElse(null);
        
        // 缓存结果，过期时间30分钟
        if (resource != null) {
            cacheService.set(cacheKey, resource, 1800);
        }
        
        return resource;
    }

    public List<Comment> getCommentsByResourceId(Long resourceId) {
        // 获取所有评论（包括回复）
        List<Comment> allComments = commentRepository.findByResourceIdOrderByCreatedAtDesc(resourceId);
        
        // 分离顶级评论和回复
        List<Comment> topLevelComments = new ArrayList<>();
        Map<Long, List<Comment>> repliesMap = new HashMap<>();
        
        for (Comment comment : allComments) {
            if (comment.getParentId() == null || comment.getParentId() == 0) {
                // 顶级评论
                topLevelComments.add(comment);
            } else {
                // 回复
                Long parentId = comment.getParentId();
                repliesMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(comment);
            }
        }
        
        // 将回复关联到对应的顶级评论
        for (Comment topComment : topLevelComments) {
            List<Comment> replies = repliesMap.get(topComment.getId());
            if (replies != null) {
                topComment.setReplies(replies);
            }
        }
        
        return topLevelComments;
    }

    public Resource saveResource(Resource resource) {
        resource.setCreatedAt(LocalDateTime.now());
        resource.setUpdatedAt(LocalDateTime.now());
        Resource savedResource = resourceRepository.save(resource);
        
        // 清除资源列表缓存
        String listCacheKey = cacheService.getResourceListKey();
        cacheService.delete(listCacheKey);
        
        // 清除该资源详情缓存
        if (savedResource.getId() != null) {
            String detailCacheKey = cacheService.getResourceDetailKey(savedResource.getId());
            cacheService.delete(detailCacheKey);
        }
        
        return savedResource;
    }

    public Comment saveComment(Comment comment) {
        return commentRepository.save(comment);
    }

    public Map<String, Object> likeComment(String userId, Long commentId) {
        Map<String, Object> result = new HashMap<>();
        
        // 检查用户是否已经点赞过
        Optional<UserAction> existingAction = userActionRepository.findByUserIdAndCommentId(userId, commentId);
        
        if (existingAction.isPresent()) {
            // 取消点赞
            userActionRepository.delete(existingAction.get());
            result.put("action", "cancel");
            result.put("message", "取消点赞成功");
        } else {
            // 添加点赞
            UserAction likeAction = new UserAction();
            likeAction.setUserId(userId);
            likeAction.setCommentId(commentId);
            userActionRepository.save(likeAction);
            result.put("action", "like");
            result.put("message", "点赞成功");
        }
        
        // 获取更新后的点赞数
        Long likes = userActionRepository.countLikesByCommentId(commentId);
        result.put("likes", likes);
        
        return result;
    }

    // 检查用户是否点赞过某个评论
    public boolean hasUserLikedComment(String userId, Long commentId) {
        return userActionRepository.findByUserIdAndCommentId(userId, commentId).isPresent();
    }

    // 获取评论的点赞数
    public Long getCommentLikes(Long commentId) {
        return userActionRepository.countLikesByCommentId(commentId);
    }
    
    // 根据评论ID查找评论
    public Comment getCommentById(Long commentId) {
        return commentRepository.findById(commentId).orElse(null);
    }
}