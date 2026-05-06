package com.neon.controller;

import com.neon.pojo.Comment;
import com.neon.pojo.DownloadRecord;
import com.neon.pojo.Message;
import com.neon.pojo.Resource;
import com.neon.pojo.Users;
import com.neon.service.ResourceService;
import com.neon.service.AsyncService;
import com.neon.service.AuthService;
import com.neon.service.ViewCountScheduler;
import com.neon.dao.DownloadRecordDao;
import com.neon.dao.MessageDao;
import com.neon.dao.UsersDao;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    
    @Autowired
    private AuthService authService;
    
    @Autowired
    private DownloadRecordDao downloadRecordDao;
    
    @Autowired
    private ViewCountScheduler viewCountScheduler;

    // 图片上传
    @PostMapping("/upload")
    @ResponseBody
    public Map<String, Object> uploadImage(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (file.isEmpty()) {
                result.put("success", false);
                result.put("message", "请选择要上传的图片");
                return result;
            }

            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                result.put("success", false);
                result.put("message", "只支持上传图片文件");
                return result;
            }

            long maxSize = 5 * 1024 * 1024;
            if (file.getSize() > maxSize) {
                result.put("success", false);
                result.put("message", "图片大小不能超过 5MB");
                return result;
            }

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFilename = System.currentTimeMillis() + "_" + (int)(Math.random() * 10000) + extension;

            String uploadPath = System.getProperty("user.dir") + "/uploads";
            java.io.File uploadDir = new java.io.File(uploadPath);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            java.io.File destFile = new java.io.File(uploadPath, newFilename);
            file.transferTo(destFile);

            String imageUrl = "/uploads/" + newFilename;
            result.put("success", true);
            result.put("url", imageUrl);
            result.put("message", "图片上传成功");
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "图片上传失败：" + e.getMessage());
        }
        return result;
    }

    // 获取资源列表（只返回卡片所需字段，也可直接返回完整Resource，前端自行提取）
    @GetMapping("/list")
    @ResponseBody
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int pageSize) {
        try {
            return resourceService.getResourcesByPage(page, pageSize);
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "获取资源列表失败: " + e.getMessage());
            return result;
        }
    }

    @GetMapping("/search")
    @ResponseBody
    public List<Resource> search(@RequestParam String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return resourceService.getAllResources();
        }
        return resourceService.searchResources(keyword.trim());
    }

    // 获取资源详情（包含评论）
    @GetMapping("/{id}")
    @ResponseBody
    public Map<String, Object> detail(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 获取用户标识（已登录用户使用用户名，未登录用户使用IP）
            String userIdentifier = getUserIdentifier(token, request);
            
            // 增加访问量（带去重）
            resourceService.incrementViewCount(id, userIdentifier);
            
            Resource resource = resourceService.getResourceDetail(id);
            if (resource == null) {
                result.put("success", false);
                result.put("message", "资源不存在");
                return result;
            }
            List<Comment> comments = resourceService.getCommentsByResourceId(id);
        
            // 简化评论数据，不再包含Base64头像（头像数据量太大）
            List<Map<String, Object>> simplifiedComments = new ArrayList<>();
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
                
                // 处理回复（同样不返回Base64头像）
                List<Map<String, Object>> replies = new ArrayList<>();
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
                    replies.add(replyMap);
                }
                commentMap.put("replies", replies);
                simplifiedComments.add(commentMap);
            }
            
            result.put("success", true);
            result.put("resource", resource);
            result.put("comments", simplifiedComments);
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

    // 更新资源
    @PostMapping("/update")
    @ResponseBody
    public Map<String, Object> updateResource(@RequestBody Resource resource) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (resource.getId() == null) {
                result.put("success", false);
                result.put("message", "资源ID不能为空");
                return result;
            }
            
            Resource existingResource = resourceService.getResourceById(resource.getId());
            if (existingResource == null) {
                result.put("success", false);
                result.put("message", "资源不存在");
                return result;
            }
            
            Resource updatedResource = resourceService.updateResource(resource);
            result.put("success", true);
            result.put("message", "资源更新成功");
            result.put("resource", updatedResource);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "资源更新失败：" + e.getMessage());
        }
        return result;
    }

    // 获取所有资源列表（管理员用，包含所有状态，支持分页）
    @GetMapping("/all")
    @ResponseBody
    public Map<String, Object> getAllResources(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> data = resourceService.getAllResourcesForAdmin(page, pageSize, keyword);
            result.put("success", true);
            result.putAll(data);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取资源列表失败：" + e.getMessage());
        }
        return result;
    }

    // 删除资源
    @DeleteMapping("/delete/{id}")
    @ResponseBody
    public Map<String, Object> deleteResource(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            Resource resource = resourceService.getResourceById(id);
            if (resource == null) {
                result.put("success", false);
                result.put("message", "资源不存在");
                return result;
            }
            resourceService.deleteResource(id);
            result.put("success", true);
            result.put("message", "资源删除成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除资源失败：" + e.getMessage());
        }
        return result;
    }

    // 发表评论
    @PostMapping("/comment")
    @ResponseBody
    public Map<String, Object> addComment(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Comment comment) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 验证token并获取用户信息
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            System.out.println("=== 评论接口调试 ===");
            System.out.println("收到的token: " + token);
            Users user = usersDao.findByToken(token);
            System.out.println("查询到的用户: " + (user != null ? user.getUserName() : "null"));
            if (user == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return result;
            }

            // 检查是否为VIP会员（memberType > 0 表示会员）
            System.out.println("用户memberType: " + user.getMemberType());
            System.out.println("用户memberStatus: " + user.getMemberStatus());
            if (user.getMemberType() == null || user.getMemberType() == 0) {
                result.put("success", false);
                result.put("message", "权限不足，请加入霓虹之都会员后再试");
                return result;
            }

            // 检查资源的评论开关状态
            Resource targetResource = resourceService.getResourceById(comment.getResourceId());
            if (targetResource == null) {
                result.put("success", false);
                result.put("message", "资源不存在");
                return result;
            }
            if (targetResource.getCommentEnabled() == null || targetResource.getCommentEnabled() != 1) {
                result.put("success", false);
                result.put("message", "该资源作者未开启评论功能哦~");
                return result;
            }

            System.out.println("准备保存评论: resourceId=" + comment.getResourceId() + ", author=" + comment.getAuthor() + ", content=" + comment.getContent());
            comment.setCreatedAt(java.time.LocalDateTime.now());
            Comment savedComment = resourceService.saveComment(comment);
            System.out.println("评论已保存, id=" + savedComment.getId());
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

    // 获取待审核资源列表（status=0）
    @GetMapping("/pending")
    @ResponseBody
    public Map<String, Object> getPendingResources() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Resource> pendingResources = resourceService.getPendingResources();
            result.put("success", true);
            result.put("resources", pendingResources);
            result.put("count", pendingResources.size());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取待审核资源失败：" + e.getMessage());
        }
        return result;
    }

    // 审核资源（通过或不通过）
    @PostMapping("/audit")
    @ResponseBody
    public Map<String, Object> auditResource(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long id = Long.valueOf(request.get("id").toString());
            Integer status = Integer.valueOf(request.get("status").toString());
            resourceService.updateResourceStatus(id, status);
            
            // 如果审核通过（status=1），启动访问量自动增加任务
            if (status == 1) {
                viewCountScheduler.startAutoIncrement(id);
            }
            
            result.put("success", true);
            result.put("message", status == 1 ? "资源已通过审核" : "资源已拒绝");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "审核操作失败：" + e.getMessage());
        }
        return result;
    }

    // 根据状态获取资源列表
    @GetMapping("/status/{status}")
    @ResponseBody
    public List<Resource> getResourcesByStatus(@PathVariable Integer status) {
        return resourceService.getResourcesByStatus(status);
    }

    // 获取下载额度
    @GetMapping("/download-quota")
    @ResponseBody
    public Map<String, Object> getDownloadQuota(@RequestParam String account) {
        Map<String, Object> result = new HashMap<>();
        try {
            Users user = usersDao.findByAccount(account);
            if (user == null) {
                result.put("success", false);
                result.put("message", "用户不存在");
                return result;
            }
            
            LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
            Long todayDownloads = downloadRecordDao.countTodayDownloads(account, startOfDay);
            
            // 根据会员类型设置每日下载限制
            // 月度会员(1): 2次/天, 季度会员(2): 3次/天, 年度会员(3)/永久会员(4): 无限制
            int dailyLimit = getDailyLimitByMemberType(user.getMemberType());
            
            if (dailyLimit == -1) {
                // 无限制会员
                result.put("success", true);
                result.put("hasQuota", true);
                result.put("remaining", -1); // -1表示无限制
                result.put("todayDownloads", todayDownloads);
                result.put("unlimited", true);
            } else {
                int remaining = dailyLimit - todayDownloads.intValue();
                result.put("success", true);
                result.put("hasQuota", remaining > 0);
                result.put("remaining", remaining);
                result.put("todayDownloads", todayDownloads);
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "获取下载额度失败：" + e.getMessage());
        }
        return result;
    }

    // 验证下载权限
    @PostMapping("/verify-download")
    @ResponseBody
    @Transactional
    public Map<String, Object> verifyDownload(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 验证token
            Map<String, Object> tokenResult = authService.validateToken(token);
            if (!((Boolean) tokenResult.get("valid"))) {
                result.put("success", false);
                result.put("message", tokenResult.get("message").toString());
                return result;
            }
            
            Users user = (Users) tokenResult.get("user");
            Long resourceId = Long.valueOf(request.get("resourceId").toString());
            String resourceTitle = (String) request.get("resourceTitle");
            
            // 检查用户是否已经下载过该资源
            boolean alreadyDownloaded = downloadRecordDao.existsByAccountAndResourceId(user.getAccount(), resourceId);
            
            if (alreadyDownloaded) {
                result.put("success", true);
                result.put("alreadyDownloaded", true);
                result.put("message", "您已经下载过该资源");
                return result;
            }
            
            // 检查今日下载额度（使用悲观锁防止并发超限）
            LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
            Long todayDownloads = downloadRecordDao.countTodayDownloadsWithLock(user.getAccount(), startOfDay);
            
            // 根据会员类型设置每日下载限制
            // 月度会员(1): 2次/天, 季度会员(2): 3次/天, 年度会员(3)/永久会员(4): 无限制
            int dailyLimit = getDailyLimitByMemberType(user.getMemberType());
            
            if (dailyLimit != -1 && todayDownloads >= dailyLimit) {
                result.put("success", false);
                result.put("quotaExceeded", true);
                result.put("message", "今日下载额度已用完");
                return result;
            }
            
            // 记录下载记录
            DownloadRecord record = new DownloadRecord();
            record.setAccount(user.getAccount());
            record.setResourceId(resourceId);
            record.setResourceTitle(resourceTitle);
            downloadRecordDao.save(record);
            
            result.put("success", true);
            result.put("alreadyDownloaded", false);
            if (dailyLimit == -1) {
                result.put("remaining", -1);
                result.put("unlimited", true);
            } else {
                result.put("remaining", dailyLimit - todayDownloads.intValue() - 1);
            }
            result.put("message", "下载成功");
            
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "验证下载权限失败：" + e.getMessage());
        }
        
        return result;
    }
    
    private int getDailyLimitByMemberType(Integer memberType) {
        if (memberType == null) {
            return 0; // 非会员无下载权限
        }
        switch (memberType) {
            case 1: // 月度会员
                return 2;
            case 2: // 季度会员
                return 3;
            case 3: // 年度会员
            case 4: // 永久会员
                return -1; // -1表示无限制
            default: // 非会员或其他
                return 0;
        }
    }
    
    /**
     * 获取用户标识（用于访问量去重）
     * 已登录用户使用用户名，未登录用户使用IP地址
     */
    private String getUserIdentifier(String token, HttpServletRequest request) {
        // 尝试从token获取用户
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        
        if (token != null && !token.isEmpty()) {
            Users user = usersDao.findByToken(token);
            if (user != null && user.getUserName() != null) {
                return "user:" + user.getUserName();
            }
        }
        
        // 未登录用户使用IP地址
        String ip = getClientIp(request);
        return "ip:" + ip;
    }
    
    /**
     * 获取客户端真实IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 对于多个代理的情况，取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }
}