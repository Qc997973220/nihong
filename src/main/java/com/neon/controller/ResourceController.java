package com.neon.controller;

import com.neon.pojo.Comment;
import com.neon.pojo.Resource;
import com.neon.service.ResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/resources")
@CrossOrigin(origins = "*")  // 允许跨域
public class ResourceController {
    @Autowired
    private ResourceService resourceService;

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
        Map<String, Object> result = new HashMap<>();
        result.put("resource", resource);
        result.put("comments", comments);
        return result;
    }
}