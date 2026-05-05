package com.neon.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
@Entity
@Data
@Table(indexes = {
    @Index(name = "idx_users_userName", columnList = "userName"),
    @Index(name = "idx_users_lastLoginTime", columnList = "lastLoginTime")
})
public class Users {
    @Id
    String id;                  // 用户ID
    @Column(unique = true, nullable = false)
    String account;             // 账号（登录用，字母数字，唯一）
    String userName;            // 用户名（展示用，支持中文）
    String password;
    String nickname;
    String activationCode; //激活码
    String role;                // 用户权限
    String phone;               // 手机
    String email;               // 邮箱
    String gender;              //性别
    String token;              //令牌
    @Column(unique = true)
    String inviteCode;         //邀请码（每个用户唯一）
    String invitedBy;          //被谁邀请的（邀请人账号）

    Integer memberType;       //会员类型: 1月度 2季度 3年度 4永久 0非会员
    LocalDateTime memberExpiredAt;  //会员到期时间
    String memberStatus;     //会员状态: active, permanent, expired, none

    @Column(columnDefinition = "LONGBLOB")
    byte[] avatar;             //头像
    @ElementCollection
    List<String> roleIds;  //角色id列表

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    LocalDateTime createTime;          // 创建时间

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    LocalDateTime operatingTime;// 操作时间

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    LocalDateTime lastLoginTime;// 最近登录时间
}
