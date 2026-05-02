package com.neon.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

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
}