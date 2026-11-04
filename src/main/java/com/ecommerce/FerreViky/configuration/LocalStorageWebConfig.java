package com.ecommerce.FerreViky.configuration;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
@Profile("dev")
public class LocalStorageWebConfig implements WebMvcConfigurer {
    @Value("${app.storage.local-dir}") private String dir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(Path.of(dir).toAbsolutePath().normalize().toUri().toString());
    }
}