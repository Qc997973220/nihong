package com.neon.service;

import com.neon.dao.AgentWechatDao;
import com.neon.pojo.AgentWechat;
import com.neon.pojo.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AgentWechatService {

    private static final int MAX_WECHAT_ID_LENGTH = 80;
    private static final int MAX_REMARK_LENGTH = 200;

    @Autowired
    private AgentWechatDao agentWechatDao;

    public boolean verify(String wechatId) {
        String normalized = normalizeWechatId(wechatId);
        if (normalized.isEmpty()) {
            return false;
        }
        return agentWechatDao.existsByNormalizedWechatIdAndStatus(normalized, AgentWechat.STATUS_ACTIVE);
    }

    public List<AgentWechat> list(String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isEmpty()) {
            return agentWechatDao.findAllByOrderByUpdatedAtDesc();
        }
        return agentWechatDao.findByWechatIdContainingIgnoreCaseOrRemarkContainingIgnoreCaseOrderByUpdatedAtDesc(
                normalizedKeyword,
                normalizedKeyword
        );
    }

    @Transactional
    public AgentWechat save(String wechatId, String remark, Users admin) {
        String displayWechatId = sanitizeWechatId(wechatId);
        String normalized = normalizeWechatId(displayWechatId);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("请输入代理商微信号");
        }

        AgentWechat item = agentWechatDao.findByNormalizedWechatId(normalized).orElseGet(AgentWechat::new);
        item.setWechatId(displayWechatId);
        item.setNormalizedWechatId(normalized);
        item.setStatus(AgentWechat.STATUS_ACTIVE);
        item.setRemark(limitLength(remark == null ? "" : remark.trim(), MAX_REMARK_LENGTH));
        if (admin != null && (item.getCreatedBy() == null || item.getCreatedBy().isBlank())) {
            item.setCreatedBy(resolveAdminName(admin));
        }
        return agentWechatDao.save(item);
    }

    @Transactional
    public AgentWechat updateStatus(Long id, Integer status) {
        AgentWechat item = agentWechatDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("代理商微信号不存在"));
        int normalizedStatus = AgentWechat.STATUS_ACTIVE == (status == null ? AgentWechat.STATUS_ACTIVE : status)
                ? AgentWechat.STATUS_ACTIVE
                : AgentWechat.STATUS_DISABLED;
        item.setStatus(normalizedStatus);
        return agentWechatDao.save(item);
    }

    @Transactional
    public void delete(Long id) {
        if (!agentWechatDao.existsById(id)) {
            throw new IllegalArgumentException("代理商微信号不存在");
        }
        agentWechatDao.deleteById(id);
    }

    public String sanitizeWechatId(String wechatId) {
        if (wechatId == null) {
            return "";
        }
        return limitLength(wechatId.trim(), MAX_WECHAT_ID_LENGTH);
    }

    public String normalizeWechatId(String wechatId) {
        return sanitizeWechatId(wechatId);
    }

    private String limitLength(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String resolveAdminName(Users admin) {
        if (admin.getAccount() != null && !admin.getAccount().trim().isEmpty()) {
            return admin.getAccount();
        }
        if (admin.getUserName() != null && !admin.getUserName().trim().isEmpty()) {
            return admin.getUserName();
        }
        return "admin";
    }
}
