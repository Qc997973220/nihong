package com.neon.controller;

import com.neon.pojo.AgentWechat;
import com.neon.pojo.Users;
import com.neon.service.AgentWechatService;
import com.neon.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/agent/wechat")
public class AgentWechatController {

    @Autowired
    private AgentWechatService agentWechatService;

    @Autowired
    private AuthService authService;

    @PostMapping("/verify")
    public Map<String, Object> verify(@RequestBody(required = false) Map<String, String> request,
                                      @RequestParam(required = false) String wechatId) {
        Map<String, Object> result = new HashMap<>();
        String input = wechatId;
        if ((input == null || input.trim().isEmpty()) && request != null) {
            input = request.get("wechatId");
        }

        if (input == null || input.trim().isEmpty()) {
            result.put("success", false);
            result.put("verified", false);
            result.put("message", "请输入代理商微信号");
            return result;
        }

        boolean verified = agentWechatService.verify(input);
        result.put("success", true);
        result.put("verified", verified);
        result.put("message", verified ? "代理身份校验成功，可放心交易" : "代理身份校验失败，没有代理资格，请谨慎交易");
        return result;
    }

    @GetMapping("/admin/list")
    public Map<String, Object> list(@RequestHeader(value = "Authorization", required = false) String token,
                                    @RequestParam(required = false) String keyword) {
        Map<String, Object> result = new HashMap<>();
        if (!requireAdmin(token, result)) {
            return result;
        }

        List<Map<String, Object>> agents = agentWechatService.list(keyword).stream()
                .map(this::toAgentItem)
                .collect(Collectors.toList());
        result.put("success", true);
        result.put("agents", agents);
        result.put("total", agents.size());
        return result;
    }

    @PostMapping("/admin/save")
    public Map<String, Object> save(@RequestHeader(value = "Authorization", required = false) String token,
                                    @RequestBody(required = false) Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        Users admin = authService.getAdminUser(token);
        if (admin == null) {
            result.put("success", false);
            result.put("message", "管理员权限不足");
            return result;
        }

        try {
            String wechatId = request != null ? request.get("wechatId") : null;
            String remark = request != null ? request.get("remark") : null;
            AgentWechat saved = agentWechatService.save(wechatId, remark, admin);
            result.put("success", true);
            result.put("message", "代理商微信号已保存");
            result.put("agent", toAgentItem(saved));
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "保存失败: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/admin/{id}/status")
    public Map<String, Object> updateStatus(@RequestHeader(value = "Authorization", required = false) String token,
                                            @PathVariable Long id,
                                            @RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        if (!requireAdmin(token, result)) {
            return result;
        }

        try {
            Integer status = parseInteger(request != null ? request.get("status") : null);
            AgentWechat updated = agentWechatService.updateStatus(id, status);
            result.put("success", true);
            result.put("message", updated.getStatus() == AgentWechat.STATUS_ACTIVE ? "已启用" : "已禁用");
            result.put("agent", toAgentItem(updated));
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "状态更新失败: " + e.getMessage());
        }
        return result;
    }

    @DeleteMapping("/admin/{id}")
    public Map<String, Object> delete(@RequestHeader(value = "Authorization", required = false) String token,
                                      @PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        if (!requireAdmin(token, result)) {
            return result;
        }

        try {
            agentWechatService.delete(id);
            result.put("success", true);
            result.put("message", "删除成功");
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
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

    private Map<String, Object> toAgentItem(AgentWechat agent) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", agent.getId());
        item.put("wechatId", agent.getWechatId());
        item.put("status", agent.getStatus());
        item.put("statusText", agent.getStatus() == AgentWechat.STATUS_ACTIVE ? "启用" : "禁用");
        item.put("remark", agent.getRemark());
        item.put("createdBy", agent.getCreatedBy());
        item.put("createdAt", agent.getCreatedAt());
        item.put("updatedAt", agent.getUpdatedAt());
        return item;
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return AgentWechat.STATUS_ACTIVE;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return AgentWechat.STATUS_ACTIVE;
        }
    }
}
