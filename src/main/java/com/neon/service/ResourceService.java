package com.neon.service;

import com.neon.dao.CommentDao;
import com.neon.dao.ResourceDao;
import com.neon.dao.UserActionDao;
import com.neon.pojo.Comment;
import com.neon.pojo.Resource;
import com.neon.pojo.UserAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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

    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 50;

    public List<Resource> getAllResources() {
        String cacheKey = cacheService.getResourceListKey();
        List<Resource> resources = (List<Resource>) cacheService.get(cacheKey);

        if (resources != null) {
            return resources;
        }

        resources = resourceRepository.findByStatusInOrderByTopDescCreatedAtDesc(Arrays.asList(1, 2));
        cacheService.set(cacheKey, resources, 180);

        return resources;
    }

    public List<Resource> getAllResourcesIncludingPending() {
        return resourceRepository.findByStatusInOrderByTopDescCreatedAtDesc(Arrays.asList(0, 1, 2));
    }

    public Map<String, Object> getAllResourcesForAdmin(int page, int pageSize, String keyword) {
        int size = Math.min(pageSize, MAX_PAGE_SIZE);
        if (size <= 0) size = 10;
        if (page <= 0) page = 1;

        Pageable pageable = PageRequest.of(page - 1, size);
        Page<Resource> resourcePage;

        if (keyword != null && !keyword.trim().isEmpty()) {
            resourcePage = resourceRepository.findByTitleContainingIgnoreCaseAndStatusInOrderByTopDescCreatedAtDesc(
                    keyword.trim(), Arrays.asList(0, 1, 2), pageable);
        } else {
            resourcePage = resourceRepository.findByStatusInOrderByTopDescCreatedAtDesc(Arrays.asList(0, 1, 2), pageable);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("resources", resourcePage.getContent());
        result.put("currentPage", page);
        result.put("pageSize", size);
        result.put("totalElements", resourcePage.getTotalElements());
        result.put("totalPages", resourcePage.getTotalPages());
        result.put("hasNext", resourcePage.hasNext());
        result.put("hasPrevious", resourcePage.hasPrevious());

        return result;
    }

    public Map<String, Object> getResourcesByPage(int page, int size) {
        return getResourcesByPage(page, size, null, null);
    }

    public Map<String, Object> getResourcesByPage(int page, int size, String category, String keyword) {
        size = Math.min(size, MAX_PAGE_SIZE);
        if (size <= 0) size = DEFAULT_PAGE_SIZE;
        if (page <= 0) page = 1;

        String normalizedCategory = normalizeCategory(category);
        String normalizedKeyword = keyword != null && !keyword.trim().isEmpty() ? keyword.trim() : null;

        String cacheKey = cacheService.getResourceListKey() + ":page:" + page + ":" + size
                + ":category:" + (normalizedCategory != null ? normalizedCategory : "all")
                + ":keyword:" + (normalizedKeyword != null ? normalizedKeyword : "none");
        Map<String, Object> cachedData = (Map<String, Object>) cacheService.get(cacheKey);

        if (cachedData != null) {
            // 更新缓存数据中的访问量（包含Redis增量）
            List<Resource> cachedResources = (List<Resource>) cachedData.get("content");
            if (cachedResources != null) {
                cachedResources.forEach(resource -> {
                    resource.setViewCount(getViewCount(resource.getId()));
                });
            }
            return cachedData;
        }

        Pageable pageable = PageRequest.of(page - 1, size);
        List<Integer> visibleStatuses = Arrays.asList(1, 2);
        Page<Resource> resourcePage;

        if (normalizedKeyword != null && normalizedCategory != null) {
            resourcePage = resourceRepository.findByTitleContainingIgnoreCaseAndCategoryAndStatusInOrderByTopDescCreatedAtDesc(
                    normalizedKeyword, normalizedCategory, visibleStatuses, pageable);
        } else if (normalizedKeyword != null) {
            resourcePage = resourceRepository.findByTitleContainingIgnoreCaseAndStatusInOrderByTopDescCreatedAtDesc(
                    normalizedKeyword, visibleStatuses, pageable);
        } else if (normalizedCategory != null) {
            resourcePage = resourceRepository.findByCategoryAndStatusInOrderByTopDescCreatedAtDesc(
                    normalizedCategory, visibleStatuses, pageable);
        } else {
            resourcePage = resourceRepository.findByStatusInOrderByTopDescCreatedAtDesc(visibleStatuses, pageable);
        }

        // 更新每个资源的访问量（包含Redis增量）
        List<Resource> resources = resourcePage.getContent();
        resources.forEach(resource -> {
            resource.setViewCount(getViewCount(resource.getId()));
        });

        Map<String, Object> result = new HashMap<>();
        result.put("content", resources);
        result.put("currentPage", page);
        result.put("pageSize", size);
        result.put("totalElements", resourcePage.getTotalElements());
        result.put("totalPages", resourcePage.getTotalPages());
        result.put("hasNext", resourcePage.hasNext());
        result.put("hasPrevious", resourcePage.hasPrevious());

        cacheService.set(cacheKey, result, 180);

        return result;
    }

    private String normalizeCategory(String category) {
        if (category == null || category.trim().isEmpty() || "全部".equals(category.trim())) {
            return null;
        }
        return category.trim();
    }

    public List<Resource> searchResources(String keyword) {
        List<Resource> resources = resourceRepository.findByTitleContainingIgnoreCaseAndStatusInOrderByTopDescCreatedAtDesc(keyword, Arrays.asList(1, 2));
        // 更新每个资源的访问量（包含Redis增量）
        resources.forEach(resource -> {
            resource.setViewCount(getViewCount(resource.getId()));
        });
        return resources;
    }

    public Resource getResourceDetail(Long id) {
        // 尝试从缓存获取
        String cacheKey = cacheService.getResourceDetailKey(id);
        Resource resource = (Resource) cacheService.get(cacheKey);
        
        if (resource != null) {
            // 更新访问量（包含Redis增量）
            resource.setViewCount(getViewCount(id));
            return resource;
        }
        
        // 从数据库获取
        resource = resourceRepository.findById(id).orElse(null);
        
        // 缓存结果，过期时间3分钟
        if (resource != null) {
            // 更新访问量（包含Redis增量）
            resource.setViewCount(getViewCount(id));
            cacheService.set(cacheKey, resource, 180);
        }
        
        return resource;
    }

    public List<Comment> getCommentsByResourceId(Long resourceId) {
        @SuppressWarnings("unchecked")
        List<Comment> comments = (List<Comment>) getCommentsByResourceId(resourceId, 1, 10).get("comments");
        return comments;
    }

    public Map<String, Object> getCommentsByResourceId(Long resourceId, int page, int size) {
        return getCommentsByResourceId(resourceId, page, size, null);
    }
    
    public Map<String, Object> getCommentsByResourceId(Long resourceId, int page, int size, String currentUser) {
        size = Math.min(size, 10);

        // 先获取所有顶层评论（不分页），用于按实际点赞数排序后分页
        List<Comment> allTopLevelComments = commentRepository.findAllTopLevelCommentsByLikesAndCreatedAt(resourceId);
        int totalElements = allTopLevelComments.size();

        if (allTopLevelComments.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("comments", new ArrayList<>());
            result.put("currentPage", page);
            result.put("pageSize", size);
            result.put("totalElements", 0);
            result.put("totalPages", 0);
            result.put("hasNext", false);
            return result;
        }

        List<Comment> allReplies = commentRepository.findRepliesByResourceId(resourceId);
        Map<Long, List<Comment>> repliesMap = new HashMap<>();

        for (Comment reply : allReplies) {
            if (reply.getParentId() != null && reply.getParentId() > 0) {
                repliesMap.computeIfAbsent(reply.getParentId(), k -> new ArrayList<>()).add(reply);
            }
        }

        // 更新所有评论的点赞数
        for (Comment topComment : allTopLevelComments) {
            topComment.setLikes(getCommentLikes(topComment.getId()).intValue());

            List<Comment> replies = repliesMap.get(topComment.getId());
            if (replies != null) {
                for (Comment reply : replies) {
                    reply.setLikes(getCommentLikes(reply.getId()).intValue());

                    List<Comment> nestedReplies = repliesMap.get(reply.getId());
                    if (nestedReplies != null) {
                        for (Comment nestedReply : nestedReplies) {
                            nestedReply.setLikes(getCommentLikes(nestedReply.getId()).intValue());
                        }
                        nestedReplies.sort((a, b) -> {
                            if (!b.getLikes().equals(a.getLikes())) {
                                return b.getLikes().compareTo(a.getLikes());
                            }
                            return b.getCreatedAt().compareTo(a.getCreatedAt());
                        });
                        reply.setReplies(nestedReplies);
                    } else {
                        reply.setReplies(new ArrayList<>());
                    }
                }
                replies.sort((a, b) -> {
                    if (!b.getLikes().equals(a.getLikes())) {
                        return b.getLikes().compareTo(a.getLikes());
                    }
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                });
                topComment.setReplies(replies);
            } else {
                topComment.setReplies(new ArrayList<>());
            }
        }

        // 按点赞数重新排序（因为Redis中的点赞数可能与数据库不同）
        allTopLevelComments.sort((a, b) -> {
            if (!b.getLikes().equals(a.getLikes())) {
                return b.getLikes().compareTo(a.getLikes());
            }
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });

        // 如果有当前用户，将当前用户的评论优先展示在最前面
        if (currentUser != null && !currentUser.isEmpty()) {
            // 从 currentUser 中提取实际的用户名（格式为 "user:username" 或 "ip:xxx"）
            final String actualUserName = currentUser.startsWith("user:")
                    ? currentUser.substring(5)
                    : currentUser;

            allTopLevelComments.sort((a, b) -> {
                boolean aIsCurrentUser = actualUserName.equals(a.getAuthor());
                boolean bIsCurrentUser = actualUserName.equals(b.getAuthor());

                if (aIsCurrentUser && !bIsCurrentUser) {
                    return -1; // a排在前面
                } else if (!aIsCurrentUser && bIsCurrentUser) {
                    return 1; // b排在前面
                } else {
                    // 保持原有的点赞数+日期排序
                    if (!b.getLikes().equals(a.getLikes())) {
                        return b.getLikes().compareTo(a.getLikes());
                    }
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                }
            });
        }

        // 计算分页信息
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, totalElements);

        // 分页获取当前页的评论
        List<Comment> topLevelComments;
        if (fromIndex >= totalElements) {
            topLevelComments = new ArrayList<>();
        } else {
            topLevelComments = allTopLevelComments.subList(fromIndex, toIndex);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("comments", topLevelComments);
        result.put("currentPage", page);
        result.put("pageSize", size);
        result.put("totalElements", totalElements);
        result.put("totalPages", totalPages);
        result.put("hasNext", page < totalPages);

        return result;
    }

    public Resource saveResource(Resource resource) {
        resource.setCreatedAt(LocalDateTime.now());
        resource.setUpdatedAt(LocalDateTime.now());
        Resource savedResource = resourceRepository.save(resource);
        
        // 清除资源列表缓存
        clearResourceListCache();
        
        // 清除该资源详情缓存
        if (savedResource.getId() != null) {
            String detailCacheKey = cacheService.getResourceDetailKey(savedResource.getId());
            cacheService.delete(detailCacheKey);
        }
        
        return savedResource;
    }

    public Comment saveComment(Comment comment) {
        System.out.println("=== saveComment 调试 ===");
        System.out.println("resourceId: " + comment.getResourceId());
        System.out.println("author: " + comment.getAuthor());
        System.out.println("content: " + comment.getContent());
        System.out.println("parentId: " + comment.getParentId());
        Comment savedComment = commentRepository.save(comment);
        System.out.println("保存后的评论ID: " + savedComment.getId());
        System.out.println("保存后的parentId: " + savedComment.getParentId());
        cacheService.delete(cacheService.getResourceDetailKey(comment.getResourceId()));
        return savedComment;
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

    // 获取待审核资源列表
    public List<Resource> getPendingResources() {
        return resourceRepository.findByStatusOrderByCreatedAtDesc(0);
    }

    // 根据状态获取资源列表
    public List<Resource> getResourcesByStatus(Integer status) {
        return resourceRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    // 更新资源审核状态
    public void updateResourceStatus(Long id, Integer status) {
        Resource resource = resourceRepository.findById(id).orElse(null);
        if (resource != null) {
            resource.setStatus(status);
            resource.setUpdatedAt(LocalDateTime.now());
            resourceRepository.save(resource);
            // 清除缓存
            clearResourceListCache();
            cacheService.delete(cacheService.getResourceDetailKey(id));
        }
    }

    // 根据ID获取资源
    public Resource getResourceById(Long id) {
        return resourceRepository.findById(id).orElse(null);
    }

    // 更新资源
    public Resource updateResource(Resource resource) {
        Resource existingResource = resourceRepository.findById(resource.getId()).orElse(null);
        if (existingResource == null) {
            return null;
        }
        
        // 更新字段
        if (resource.getTitle() != null) {
            existingResource.setTitle(resource.getTitle());
        }
        if (resource.getSummary() != null) {
            existingResource.setSummary(resource.getSummary());
        }
        if (resource.getContent() != null) {
            existingResource.setContent(resource.getContent());
        }
        if (resource.getImage() != null) {
            existingResource.setImage(resource.getImage());
        }
        if (resource.getCategory() != null) {
            existingResource.setCategory(resource.getCategory());
        }
        if (resource.getDownloadLink() != null) {
            existingResource.setDownloadLink(resource.getDownloadLink());
        }
        if (resource.getDownloadPassword() != null) {
            existingResource.setDownloadPassword(resource.getDownloadPassword());
        }
        if (resource.getStatus() != null) {
            existingResource.setStatus(resource.getStatus());
        }
        
        existingResource.setUpdatedAt(LocalDateTime.now());
        Resource savedResource = resourceRepository.save(existingResource);
        
        // 清除缓存
        clearResourceListCache();
        cacheService.delete(cacheService.getResourceDetailKey(resource.getId()));
        
        return savedResource;
    }

    // 删除资源
    public void deleteResource(Long id) {
        resourceRepository.deleteById(id);
        // 清除缓存
        clearResourceListCache();
        cacheService.delete(cacheService.getResourceDetailKey(id));
    }

    private void clearResourceListCache() {
        String listCacheKey = cacheService.getResourceListKey();
        cacheService.delete(listCacheKey);
        Set<String> pageKeys = cacheService.keys(listCacheKey + ":*");
        if (pageKeys != null) {
            for (String key : pageKeys) {
                cacheService.delete(key);
            }
        }
    }

    // 增加访问量（用户访问时调用）- 使用 Redis 缓存，带去重
    public boolean incrementViewCount(Long resourceId, String userIdentifier) {
        // 检查用户是否已访问过该资源
        if (cacheService.hasUserViewedResource(userIdentifier, resourceId)) {
            return false; // 已访问过，不增加
        }
        
        // 使用 Redis 原子递增，记录增量
        String deltaKey = cacheService.getResourceViewCountDeltaKey(resourceId);
        cacheService.increment(deltaKey);
        
        // 标记用户已访问
        cacheService.markUserViewedResource(userIdentifier, resourceId);
        
        // 清除该资源详情缓存
        cacheService.delete(cacheService.getResourceDetailKey(resourceId));
        
        return true; // 增加成功
    }

    // 增加访问量（无去重，用于自动增加等场景）
    public void incrementViewCount(Long resourceId) {
        // 使用 Redis 原子递增，记录增量
        String deltaKey = cacheService.getResourceViewCountDeltaKey(resourceId);
        cacheService.increment(deltaKey);
        
        // 清除该资源详情缓存
        cacheService.delete(cacheService.getResourceDetailKey(resourceId));
    }

    // 获取资源访问量（从缓存或数据库）
    public Integer getViewCount(Long resourceId) {
        // 先从缓存获取增量
        String deltaKey = cacheService.getResourceViewCountDeltaKey(resourceId);
        int delta = cacheService.getInt(deltaKey);
        
        // 从数据库获取基础访问量
        Resource resource = resourceRepository.findById(resourceId).orElse(null);
        int baseCount = resource != null && resource.getViewCount() != null ? resource.getViewCount() : 0;
        
        // 返回总和
        return baseCount + delta;
    }

    // 批量将缓存的访问量增量持久化到数据库
    public void flushViewCountsToDatabase() {
        String pattern = cacheService.getResourceViewCountDeltaKey(0L).replace(":0", ":*");
        Set<String> deltaKeys = cacheService.keys(pattern);
        
        if (deltaKeys == null || deltaKeys.isEmpty()) {
            return;
        }
        
        for (String deltaKey : deltaKeys) {
            try {
                // 解析资源ID
                Long resourceId = Long.parseLong(deltaKey.split(":")[3]);
                int delta = cacheService.getInt(deltaKey);
                
                if (delta > 0) {
                    Resource resource = resourceRepository.findById(resourceId).orElse(null);
                    if (resource != null) {
                        int currentCount = resource.getViewCount() != null ? resource.getViewCount() : 0;
                        resource.setViewCount(currentCount + delta);
                        resource.setUpdatedAt(LocalDateTime.now());
                        resourceRepository.save(resource);
                        
                        // 清除增量缓存
                        cacheService.delete(deltaKey);
                        
                        // 清除资源详情缓存
                        cacheService.delete(cacheService.getResourceDetailKey(resourceId));
                    }
                } else {
                    // 增量为0或负数，直接删除缓存
                    cacheService.delete(deltaKey);
                }
            } catch (Exception e) {
                System.out.println("Flush view count failed for key " + deltaKey + ": " + e.getMessage());
            }
        }
    }

    // 获取资源的真实访问量（仅数据库值，不包含缓存增量）
    public Integer getViewCountFromDatabase(Long resourceId) {
        Resource resource = resourceRepository.findById(resourceId).orElse(null);
        if (resource != null && resource.getViewCount() != null) {
            return resource.getViewCount();
        }
        return 0;
    }

    // 设置停止阈值
    public void setStopThreshold(Long resourceId, Integer threshold) {
        Resource resource = resourceRepository.findById(resourceId).orElse(null);
        if (resource != null) {
            resource.setStopThreshold(threshold);
            resource.setUpdatedAt(LocalDateTime.now());
            resourceRepository.save(resource);
            cacheService.delete(cacheService.getResourceDetailKey(resourceId));
        }
    }

    // 获取停止阈值
    public Integer getStopThreshold(Long resourceId) {
        Resource resource = resourceRepository.findById(resourceId).orElse(null);
        if (resource != null && resource.getStopThreshold() != null) {
            return resource.getStopThreshold();
        }
        return null;
    }

    // 检查是否需要继续自动增加（访问量是否小于阈值）
    public boolean shouldContinueAutoIncrement(Long resourceId) {
        Resource resource = resourceRepository.findById(resourceId).orElse(null);
        if (resource == null) {
            return false;
        }
        Integer threshold = resource.getStopThreshold();
        if (threshold == null) {
            return false;
        }
        // 使用包含缓存增量的总访问量
        int totalViewCount = getViewCount(resourceId);
        return totalViewCount < threshold;
    }
}
