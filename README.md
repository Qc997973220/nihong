# nihong

#### 介绍
NEON 霓虹之都资源网，一个基于 Spring Boot 的资源分享 / 下载会员平台。用户注册后浏览资源，VIP 会员可下载；支持卡密开通会员、邀请返利（N 币）、提现等完整的会员运营体系。

主要功能：
- 用户注册 / 登录 / 邮箱验证 / 找回密码
- 会员体系（卡密激活：年费 360 天、永久、霓虹代理）
- 资源发布 → 审核 → 展示 / 搜索 / 推荐 / 轮播图
- 下载权限控制、免费资源每日解锁额度
- 嵌套评论、点赞、回复通知
- 钱包（N 币）、邀请返利、支付宝提现
- 站点访问量统计、监控告警

#### 软件架构
采用 Spring Boot 标准分层架构：

| 层次 | 技术 |
| --- | --- |
| 语言 / 框架 | Java 17 + Spring Boot 3.2.1 |
| Web 层 | Spring Web MVC、WebSocket |
| 持久层 | Spring Data JPA（Hibernate）+ MySQL 8 |
| 缓存 | Spring Data Redis |
| 邮件 | Spring Mail（QQ SMTP） |
| 监控 | Spring Boot Actuator + Prometheus |
| 工具 | Lombok、Jackson、springdoc-openapi（Swagger UI） |
| 构建 / 部署 | Maven、Docker、docker-compose、Nginx |

代码结构：

```
com.neon
├── controller/   # REST 控制器
├── service/      # 业务逻辑
├── dao/          # 数据访问层（JPA Repository）
├── pojo/         # 实体类
└── config/       # Redis / Web / 异步 / 监控配置
```

#### 安装教程

1. 环境要求：JDK 17+、Maven 3.6+、MySQL 8.0、Redis
2. 创建数据库并导入配置：

```sql
CREATE DATABASE resource_db DEFAULT CHARACTER SET utf8mb4;
```

3. 修改 `src/main/resources/application.properties` 中的数据库与 Redis 连接信息（本地敏感配置可放入 `application-local.properties`，该文件已被 `.gitignore` 忽略）。
4. 打包并运行：

```bash
./mvnw clean package
./mvnw spring-boot:run
```

5. 启动后访问：
   - 应用：http://localhost:8060
   - 接口文档：http://localhost:8060/swagger-ui/index.html
   - 健康检查：http://localhost:8060/actuator/health

#### 使用说明

1. 默认服务端口为 `8060`。
2. 首次使用需管理员登录后台，通过 `/cardkey/generate` 生成会员卡密。
3. 普通用户注册后，通过卡密激活会员，即可下载 VIP 资源。
4. 免费资源每天默认可解锁 2 次；年费 / 永久 / 代理会员不限下载次数。
5. Docker 部署：

```bash
docker build -t neon-app .
docker-compose up -d
```

6. 生产环境 Nginx 配置见 `nginx.conf`（含 HTTPS、静态缓存、`/api/` 反向代理）。

#### 参与贡献

1. Fork 本仓库
2. 新建 Feat_xxx 分支
3. 提交代码
4. 新建 Pull Request

#### 安全提示

分析代码时发现以下问题，上线前建议处理：

1. 密码为明文存储，未使用 BCrypt 哈希，建议引入 `spring-security-crypto` 加密。
2. 数据库 / Redis 密码硬编码在 `application.properties` 且已提交仓库，建议改用环境变量注入。
3. 邮件授权码默认值为空，需通过环境变量 `QQ_MAIL_AUTH_CODE` 提供。
4. 部分接口使用 `System.out.println` 打印 token 等调试信息，建议移除。
5. 所有控制器 CORS 全开（`@CrossOrigin(origins = "*")`），如需安全可收紧。
