# 使用 OpenJDK 17 作为基础镜像
FROM openjdk:17-jdk-alpine

# 设置工作目录
WORKDIR /app

# 复制 jar 包到容器
COPY neon-0.0.1-SNAPSHOT.jar app.jar

# 创建 avatars 目录
RUN mkdir -p /app/avatars

# 暴露端口
EXPOSE 8060

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]