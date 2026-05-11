package com.neon.controller;

import com.neon.pojo.CardKey;
import com.neon.pojo.Users;
import com.neon.service.AuthService;
import com.neon.service.CardKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/cardkey")
public class CardKeyController {

    private static final Logger log = LoggerFactory.getLogger(CardKeyController.class);

    @Autowired
    private CardKeyService cardKeyService;

    @Autowired
    private AuthService authService;

    @PostMapping("/generate")
    public Map<String, Object> generateCardKeys(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(defaultValue = "10") int count,
            @RequestParam(defaultValue = "1") int memberType) {
        log.info("收到生成卡密请求: count={}, memberType={}", count, memberType);
        Map<String, Object> result = new HashMap<>();
        if (!requireAdmin(token, result)) {
            return result;
        }
        try {
            if (count <= 0 || count > 100) {
                result.put("success", false);
                result.put("message", "生成数量必须在1-100之间");
                return result;
            }

            if (memberType < 1 || memberType > 4) {
                result.put("success", false);
                result.put("message", "会员类型无效");
                return result;
            }

            String typeName = getMemberTypeName(memberType);
            List<String> keys = cardKeyService.generateCardKeys(count, memberType);
            result.put("success", true);
            result.put("message", "成功生成" + count + "个" + typeName + "卡密");
            result.put("count", keys.size());
            result.put("keys", keys);
            result.put("memberType", memberType);
            result.put("memberTypeName", typeName);
            log.info("卡密生成成功: {} 个", keys.size());
            return result;
        } catch (Exception e) {
            log.error("生成卡密异常: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "生成失败: " + e.getMessage());
            return result;
        }
    }

    private String getMemberTypeName(int memberType) {
        switch (memberType) {
            case CardKey.TYPE_MONTHLY: return "月度会员(30天)";
            case CardKey.TYPE_QUARTERLY: return "季度会员(90天)";
            case CardKey.TYPE_YEARLY: return "年度会员(360天)";
            case CardKey.TYPE_PERMANENT: return "永久会员";
            default: return "未知";
        }
    }

    @GetMapping("/list")
    public Map<String, Object> listCardKeys(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(required = false) Integer memberType) {
        log.info("收到查询卡密列表请求: memberType={}", memberType);
        Map<String, Object> result = new HashMap<>();
        if (!requireAdmin(token, result)) {
            return result;
        }
        try {
            List<CardKey> keys;
            if (memberType != null && memberType >= 1 && memberType <= 4) {
                keys = cardKeyService.findByMemberType(memberType);
            } else {
                keys = cardKeyService.findAll();
            }
            result.put("success", true);
            result.put("data", keys);
            result.put("total", keys.size());
            log.info("卡密列表查询成功: {} 条记录", keys.size());
            return result;
        } catch (Exception e) {
            log.error("查询卡密列表异常: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
            return result;
        }
    }

    @GetMapping("/count")
    public Map<String, Object> countAvailable(
            @RequestHeader(value = "Authorization", required = false) String token) {
        Map<String, Object> result = new HashMap<>();
        if (!requireAdmin(token, result)) {
            return result;
        }
        long count = cardKeyService.countAvailable();
        result.put("success", true);
        result.put("availableCount", count);
        return result;
    }

    @DeleteMapping("/delete/{id}")
    public Map<String, Object> deleteCardKey(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        if (!requireAdmin(token, result)) {
            return result;
        }
        try {
            cardKeyService.deleteById(id);
            result.put("success", true);
            result.put("message", "删除成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败：" + e.getMessage());
        }
        return result;
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
}
