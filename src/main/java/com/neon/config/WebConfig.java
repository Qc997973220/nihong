package com.neon.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadPath = System.getProperty("user.dir") + "/uploads";
        Path uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir.toString() + "/")
                .setCachePeriod(3600);

        String avatarPath = System.getProperty("user.dir") + "/avatars";
        Path avatarDir = Paths.get(avatarPath).toAbsolutePath().normalize();

        registry.addResourceHandler("/avatar/**")
                .addResourceLocations("file:" + avatarDir.toString() + "/")
                .setCachePeriod(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns(
                        // 需要验证的接口
                        "/login/updateUserInfo",
                        "/login/activate",
                        "/login/logout",
                        "/resources/publish",
                        "/resources/comment",
                        "/resources/comment/*/like",
                        "/resources/pending",
                        "/resources/audit",
                        "/resources/verify-download"
                )
                .excludePathPatterns(
                        // 排除不需要验证的接口
                        "/login/first",
                        "/login/registered",
                        "/login/recover",
                        "/resources/list",
                        "/resources/search",
                        "/resources/*",
                        "/resources/download-quota",
                        "/uploads/**",
                        "/avatar/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                );
    }
}