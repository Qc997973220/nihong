package com.neon.controller;

import com.neon.pojo.Comment;
import com.neon.pojo.CardKey;
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
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/resources")
@CrossOrigin(origins = "*")  // 允许跨域
public class ResourceController {
    private static final long MAX_UPLOAD_IMAGE_SIZE = 3L * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 1600;
    private static final float JPEG_QUALITY = 0.82f;
    private static final int FREE_DAILY_UNLOCK_LIMIT = 2;
    private static final String FREE_QUOTA_EXCEEDED_MESSAGE = "您今日免费额度已用完,请明天再来吧!";

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
    public Map<String, Object> uploadImage(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        try {
            Users user = authService.getAuthenticatedUser(token);
            if (user == null) {
                result.put("success", false);
                result.put("message", "请先登录后再上传图片");
                return result;
            }

            if (file.isEmpty()) {
                result.put("success", false);
                result.put("message", "请选择要上传的图片");
                return result;
            }

            String contentType = file.getContentType();
            if (!isAllowedImageType(contentType)) {
                result.put("success", false);
                result.put("message", "只支持 JPG、PNG、WebP 格式的图片");
                return result;
            }

            if (file.getSize() > MAX_UPLOAD_IMAGE_SIZE) {
                result.put("success", false);
                result.put("message", "图片大小不能超过 3MB");
                return result;
            }

            String filenameBase = System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
            String newFilename = filenameBase + ".jpg";

            String uploadPath = System.getProperty("user.dir") + "/uploads";
            File uploadDir = new File(uploadPath);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            File destFile = new File(uploadPath, newFilename);
            boolean compressed = compressAndSaveImage(file, destFile);
            if (!compressed) {
                if (!"image/webp".equals(contentType)) {
                    throw new IOException("无法解析图片，请上传标准 JPG、PNG 或 WebP 图片");
                }
                newFilename = filenameBase + ".webp";
                destFile = new File(uploadPath, newFilename);
                file.transferTo(destFile);
            }

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

    private boolean isAllowedImageType(String contentType) {
        return "image/jpeg".equals(contentType)
                || "image/png".equals(contentType)
                || "image/webp".equals(contentType);
    }

    private boolean compressAndSaveImage(MultipartFile file, File destFile) throws IOException {
        BufferedImage sourceImage;
        try (InputStream input = file.getInputStream()) {
            sourceImage = ImageIO.read(input);
        }
        if (sourceImage == null) {
            return false;
        }

        int sourceWidth = sourceImage.getWidth();
        int sourceHeight = sourceImage.getHeight();
        double scale = Math.min(
                1.0,
                (double) MAX_IMAGE_DIMENSION / Math.max(sourceWidth, sourceHeight)
        );
        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));

        BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = outputImage.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(sourceImage, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }

        writeJpeg(outputImage, destFile, JPEG_QUALITY);
        return true;
    }

    private void writeJpeg(BufferedImage image, File destFile, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("当前运行环境不支持 JPG 图片压缩");
        }

        ImageWriter writer = writers.next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(destFile)) {
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(quality);
            }
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
    }

    // 获取资源列表（只返回卡片所需字段，也可直接返回完整Resource，前端自行提取）
    @GetMapping("/list")
    @ResponseBody
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int pageSize,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        try {
            return resourceService.getResourcesByPage(page, pageSize, category, keyword);
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
            @RequestParam(defaultValue = "1") int commentPage,
            @RequestParam(defaultValue = "5") int commentSize,
            @RequestHeader(value = "Authorization", required = false) String token,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 获取用户标识（已登录用户使用用户名，未登录用户使用IP）
            String userIdentifier = getUserIdentifier(token, request);
            
            Resource resource = resourceService.getResourceDetail(id);
            if (resource == null) {
                result.put("success", false);
                result.put("message", "资源不存在");
                return result;
            }
            
            // 保存原始下载链接，用于后续判断
            String originalDownloadLink = resource.getDownloadLink();
            boolean resourceHasDownload = originalDownloadLink != null && !originalDownloadLink.isEmpty();

            boolean freeResource = isFreeResource(resource);
            boolean loginRequired = false;
            Users tokenUser = getValidTokenUser(token);
            if (!isVisibleResource(resource) && !authService.isAdminUser(tokenUser)) {
                result.put("success", false);
                result.put("message", "资源不存在");
                return result;
            }
            resourceService.incrementViewCount(id, userIdentifier);
            Users user = freeResource ? tokenUser : checkDownloadPermission(tokenUser);
            Resource responseResource = copyResourceForResponse(resource);

            // 如果用户没有下载权限，隐藏下载链接和密码
            boolean quotaExceeded = false;
            boolean freeQuotaExceeded = false;
            int freeRemaining = -1;
            if (freeResource && user != null && resourceHasDownload) {
                Map<String, Object> freeUnlockResult = unlockFreeResourceIfAllowed(user, resource);
                if (!Boolean.TRUE.equals(freeUnlockResult.get("allowed"))) {
                    user = null;
                    freeQuotaExceeded = true;
                }
                Object remainingValue = freeUnlockResult.get("remaining");
                if (remainingValue instanceof Number) {
                    freeRemaining = ((Number) remainingValue).intValue();
                }
            }
            if (user == null) {
                responseResource.setDownloadLink(null);
                responseResource.setDownloadPassword(null);

                if (freeResource) {
                    loginRequired = resourceHasDownload && !freeQuotaExceeded;
                } else if (isDownloadQuotaExceeded(tokenUser)) {
                    quotaExceeded = true; // 只是下载额度用完
                }
            }
            
            // 添加 hasAccess 字段，表示资源是否有下载链接（不管用户是否能访问）
            // 用于前端判断是否渲染下载区域
            result.put("hasAccess", resourceHasDownload);
            result.put("freeResource", freeResource);
            result.put("loginRequired", loginRequired);
            // 添加 quotaExceeded 字段，表示用户是否只是下载额度用完（但会员未到期）
            result.put("quotaExceeded", quotaExceeded);
            result.put("freeQuotaExceeded", freeQuotaExceeded);
            result.put("freeQuotaMessage", FREE_QUOTA_EXCEEDED_MESSAGE);
            result.put("freeRemaining", freeRemaining);
            
            Map<String, Object> commentResult = resourceService.getCommentsByResourceId(id, commentPage, commentSize, userIdentifier);
        
            List<Comment> comments = (List<Comment>) commentResult.get("comments");
        
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
            result.put("resource", responseResource);
            result.put("comments", simplifiedComments);
            result.put("commentPageInfo", commentResult);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "获取资源详情失败: " + e.getMessage());
        }
        return result;
    }
    
    /**
     * 检查用户是否为VIP会员且有下载权限
     * @return 如果用户有下载权限，返回用户信息；否则返回null
     */
    private Users checkDownloadPermission(String token) {
        Users user = getValidTokenUser(token);
        return checkDownloadPermission(user);
    }

    private Users checkDownloadPermission(Users user) {
        if (user == null) {
            return null;
        }

        if (!hasActiveVip(user)) {
            return null;
        }

        // 检查每日下载额度
        int dailyLimit = getDailyLimitByMemberType(user.getMemberType());
        if (dailyLimit != -1) {
            LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
            Long todayDownloads = downloadRecordDao.countTodayDownloads(user.getAccount(), startOfDay);
            if (todayDownloads >= dailyLimit) {
                return null; // 下载额度用完
            }
        }
        
        return user;
    }

    private Users getValidTokenUser(String token) {
        Map<String, Object> tokenResult = authService.validateToken(token);
        if (!Boolean.TRUE.equals(tokenResult.get("valid"))) {
            return null;
        }
        return (Users) tokenResult.get("user");
    }

    private boolean hasActiveVip(Users user) {
        if (user == null || user.getMemberType() == null || user.getMemberType() <= 0) {
            return false;
        }
        if (user.getMemberType() == CardKey.TYPE_PERMANENT || user.getMemberType() == CardKey.TYPE_AGENT) {
            return true;
        }
        return user.getMemberExpiredAt() != null && user.getMemberExpiredAt().isAfter(LocalDateTime.now());
    }

    private boolean isDownloadQuotaExceeded(Users user) {
        if (!hasActiveVip(user)) {
            return false;
        }
        int dailyLimit = getDailyLimitByMemberType(user.getMemberType());
        if (dailyLimit == -1) {
            return false;
        }
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        Long todayDownloads = downloadRecordDao.countTodayDownloads(user.getAccount(), startOfDay);
        return todayDownloads >= dailyLimit;
    }

    private Map<String, Object> unlockFreeResourceIfAllowed(Users user, Resource resource) {
        Map<String, Object> result = new HashMap<>();
        result.put("allowed", false);
        result.put("remaining", 0);
        result.put("unlimited", false);
        result.put("alreadyUnlocked", false);

        if (user == null || user.getAccount() == null || resource == null || resource.getId() == null) {
            result.put("message", "请先登录");
            return result;
        }

        if (hasActiveVip(user)) {
            result.put("allowed", true);
            result.put("remaining", -1);
            result.put("unlimited", true);
            return result;
        }

        if (downloadRecordDao.existsByAccountAndResourceId(user.getAccount(), resource.getId())) {
            result.put("allowed", true);
            result.put("alreadyUnlocked", true);
            result.put("remaining", getRemainingFreeUnlocks(user.getAccount()));
            return result;
        }

        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        long todayUnlocks = downloadRecordDao.countTodayFreeUnlocks(user.getAccount(), startOfDay, "免费");
        if (todayUnlocks >= FREE_DAILY_UNLOCK_LIMIT) {
            result.put("message", FREE_QUOTA_EXCEEDED_MESSAGE);
            return result;
        }

        DownloadRecord record = new DownloadRecord();
        record.setAccount(user.getAccount());
        record.setResourceId(resource.getId());
        record.setResourceTitle(resource.getTitle());
        downloadRecordDao.save(record);

        result.put("allowed", true);
        result.put("remaining", Math.max(0, FREE_DAILY_UNLOCK_LIMIT - (int) todayUnlocks - 1));
        return result;
    }

    private int getRemainingFreeUnlocks(String account) {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        long todayUnlocks = downloadRecordDao.countTodayFreeUnlocks(account, startOfDay, "免费");
        return Math.max(0, FREE_DAILY_UNLOCK_LIMIT - (int) todayUnlocks);
    }

    private boolean isFreeResource(Resource resource) {
        return resource != null
                && resource.getCategory() != null
                && "免费".equals(resource.getCategory().trim());
    }

    private boolean isVisibleResource(Resource resource) {
        if (resource == null || resource.getStatus() == null) {
            return false;
        }
        return resource.getStatus() == 1 || resource.getStatus() == 2;
    }

    private boolean isVisibleStatus(Integer status) {
        return status != null && (status == 1 || status == 2);
    }

    private Resource copyResourceForResponse(Resource resource) {
        Resource responseResource = new Resource();
        BeanUtils.copyProperties(resource, responseResource);
        return responseResource;
    }

    // 发布资源
    @PostMapping("/publish")
    @ResponseBody
    public Map<String, Object> publish(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Resource resource) {
        Map<String, Object> result = new HashMap<>();
        try {
            Users user = authService.getAuthenticatedUser(token);
            if (user == null) {
                result.put("success", false);
                result.put("message", "请先登录后再发布资源");
                return result;
            }

            if (resource.getTitle() != null && resource.getTitle().length() > 50) {
                result.put("success", false);
                result.put("message", "资源标题不能超过50个字符");
                return result;
            }
            resource.setId(null);
            resource.setAuthor(resolveUserDisplayName(user));
            resource.setStatus(0);
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
    public Map<String, Object> updateResource(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Resource resource) {
        Map<String, Object> result = new HashMap<>();
        if (!requireAdmin(token, result)) {
            return result;
        }
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
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        Map<String, Object> result = new HashMap<>();
        if (!requireAdmin(token, result)) {
            return result;
        }
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
    public Map<String, Object> deleteResource(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        if (!requireAdmin(token, result)) {
            return result;
        }
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
            comment.setAuthor(resolveUserDisplayName(user));

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
    public Map<String, Object> likeComment(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        try {
            Users user = authService.getAuthenticatedUser(token);
            if (user == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return result;
            }
            String userId = resolveUserDisplayName(user);
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
    public Map<String, Object> checkUserLiked(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id,
            @PathVariable String userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            Users user = authService.getAuthenticatedUser(token);
            if (user == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return result;
            }
            boolean hasLiked = resourceService.hasUserLikedComment(resolveUserDisplayName(user), id);
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
    public Map<String, Object> getPendingResources(
            @RequestHeader(value = "Authorization", required = false) String token) {
        Map<String, Object> result = new HashMap<>();
        if (!requireAdmin(token, result)) {
            return result;
        }
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
    public Map<String, Object> auditResource(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        if (!requireAdmin(token, result)) {
            return result;
        }
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
    public Object getResourcesByStatus(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Integer status) {
        if (isVisibleStatus(status)) {
            return resourceService.getResourcesByStatus(status);
        }

        Map<String, Object> result = new HashMap<>();
        if (!requireAdmin(token, result)) {
            return result;
        }
        result.put("success", true);
        result.put("resources", resourceService.getResourcesByStatus(status));
        return result;
    }

    // 获取下载额度
    @GetMapping("/download-quota")
    @ResponseBody
    public Map<String, Object> getDownloadQuota(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(required = false) String account) {
        Map<String, Object> result = new HashMap<>();
        try {
            Users user = authService.getAuthenticatedUser(token);
            if (user == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return result;
            }
            if (account != null && !account.trim().isEmpty() && !account.equals(user.getAccount())) {
                result.put("success", false);
                result.put("message", "无权查看其他用户下载额度");
                return result;
            }
            account = user.getAccount();
            
            LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
            Long todayDownloads = downloadRecordDao.countTodayDownloads(account, startOfDay);
            
            // 根据会员类型设置每日下载限制
            // 旧版会员(1/2)保留历史下载额度，年费会员(3)/永久会员(4)/霓虹代理(5): 无限制
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

            Resource resource = resourceService.getResourceById(resourceId);
            if (resource == null) {
                result.put("success", false);
                result.put("message", "资源不存在");
                return result;
            }

            if (isFreeResource(resource)) {
                Map<String, Object> freeUnlockResult = unlockFreeResourceIfAllowed(user, resource);
                if (!Boolean.TRUE.equals(freeUnlockResult.get("allowed"))) {
                    result.put("success", false);
                    result.put("freeQuotaExceeded", true);
                    result.put("quotaExceeded", true);
                    result.put("message", FREE_QUOTA_EXCEEDED_MESSAGE);
                    return result;
                }

                result.put("success", true);
                result.put("alreadyDownloaded", Boolean.TRUE.equals(freeUnlockResult.get("alreadyUnlocked")));
                result.put("freeResource", true);
                result.put("remaining", freeUnlockResult.get("remaining"));
                result.put("unlimited", freeUnlockResult.get("unlimited"));
                result.put("message", "免费资源获取成功");
                return result;
            }

            if (!hasActiveVip(user)) {
                result.put("success", false);
                result.put("message", "权限不足，请加入霓虹之都会员后再试");
                return result;
            }
            
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
            // 旧版会员(1/2)保留历史下载额度，年费会员(3)/永久会员(4)/霓虹代理(5): 无限制
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
            case 1: // 旧版会员
                return 2;
            case 2: // 旧版会员
                return 3;
            case 3: // 年费会员
            case 4: // 永久会员
            case 5: // 霓虹代理
                return -1; // -1表示无限制
            default: // 非会员或其他
                return 0;
        }
    }

    private boolean requireAdmin(String token, Map<String, Object> result) {
        Users admin = authService.getAdminUser(token);
        if (admin == null) {
            result.put("success", false);
            result.put("message", "管理员权限不足");
            return false;
        }
        return true;
    }

    private String resolveUserDisplayName(Users user) {
        if (user == null) {
            return "";
        }
        return user.getUserName() != null && !user.getUserName().trim().isEmpty()
                ? user.getUserName()
                : user.getAccount();
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
