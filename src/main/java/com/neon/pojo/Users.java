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
    @Column(length = 80)
    String introduction;       //个人简介（最多80字）
    String token;              //令牌
    LocalDateTime tokenExpiredAt;  //令牌过期时间
    Integer tokenVersion;        //令牌版本（用于控制多设备登录）
    @Column(unique = true)
    String inviteCode;         //邀请码（每个用户唯一）
    String invitedBy;          //被谁邀请的（邀请人账号）

    Integer nCoinBalance = 0;  // N币可用余额
    Integer nCoinFrozen = 0;   // N币冻结中余额

    Integer memberType;       //会员类型: 1/2旧版(停用) 3年费 4永久 5霓虹代理 0非会员
    LocalDateTime memberExpiredAt;  //会员到期时间
    String memberStatus;     //会员状态: active, permanent, expired, none

    // 头像功能已禁用（所有接口均返回 null），移除 avatar LONGBLOB 字段，
    // 避免每次查询用户时都读取大字段。数据库中的旧 avatar 列已废弃，可后续手动 DROP。
    @ElementCollection
    List<String> roleIds;  //角色id列表

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    LocalDateTime createTime;          // 创建时间

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    LocalDateTime operatingTime;// 操作时间

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    LocalDateTime lastLoginTime;// 最近登录时间

    String registeredIp;       // 注册IP
    LocalDateTime registeredDate; // 注册日期
}
