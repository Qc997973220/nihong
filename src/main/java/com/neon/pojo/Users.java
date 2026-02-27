package com.neon.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
@Entity
@Data
public class Users {
    @Id
    String id;                  // 用户ID
    String userName;
    String Password;
    String nickname;
    String activationCode; //激活码
    String role;                // 用户权限
    String phone;               // 手机
    String email;               // 邮箱
    String gender;              //性别
    String token;              //令牌
    @ElementCollection
    List<String> roleIds;  //角色id列表

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    LocalDateTime createTime;          // 创建时间

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    LocalDateTime operatingTime;// 操作时间
}
