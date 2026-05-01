package com.neon.service;

import com.neon.pojo.Message;
import com.neon.dao.MessageDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AsyncService {

    @Autowired
    private MessageDao messageDao;

    /**
     * 异步发送消息通知
     * @param userId 接收消息的用户ID
     * @param content 消息内容
     * @param type 消息类型
     * @param relatedId 相关ID
     */
    @Async
    public void sendMessageNotification(String userId, String content, String type, Long relatedId) {
        try {
            // 创建消息
            Message message = new Message();
            message.setUserId(userId);
            message.setContent(content);
            message.setType(type);
            message.setRelatedId(relatedId);
            message.setIsRead(false);
            message.setCreatedAt(LocalDateTime.now());
            
            // 保存消息
            messageDao.save(message);
        } catch (Exception e) {
            // 记录异常，但不影响主流程
            e.printStackTrace();
        }
    }

    /**
     * 异步处理文件上传后的操作
     * @param userName 用户名
     * @param avatarUrl 头像URL
     */
    @Async
    public void processFileUpload(String userName, String avatarUrl) {
        try {
            // 这里可以添加一些文件上传后的处理逻辑
            // 比如生成缩略图、进行图片处理等
            System.out.println("异步处理文件上传: " + userName);
            // 模拟耗时操作
            Thread.sleep(1000);
        } catch (Exception e) {
            // 记录异常，但不影响主流程
            e.printStackTrace();
        }
    }

    /**
     * 异步更新用户操作记录
     * @param userId 用户ID
     * @param actionType 操作类型
     * @param relatedId 相关ID
     */
    @Async
    public void updateUserAction(String userId, String actionType, Long relatedId) {
        try {
            // 这里可以添加用户操作记录的更新逻辑
            System.out.println("异步更新用户操作: " + userId + " - " + actionType);
            // 模拟耗时操作
            Thread.sleep(500);
        } catch (Exception e) {
            // 记录异常，但不影响主流程
            e.printStackTrace();
        }
    }
}