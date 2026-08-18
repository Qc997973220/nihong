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

# 启动应用（限制 JVM 堆内存，避免在小内存服务器上被 OOM Killer 终止）
# -Xmx512m：最大堆 512MB；-XX:MaxMetaspaceSize 限制元空间。可按服务器内存酌情调整
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-XX:MaxMetaspaceSize=160m", "-XX:+UseG1GC", "-jar", "app.jar"]