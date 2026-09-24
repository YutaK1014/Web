package com.example.cashflow.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PhotoConfiguration implements WebMvcConfigurer {
    private final String location;

    public PhotoConfiguration(@Value("${app.photos.directory:./uploads/images}") String directory) {
        String uri = Path.of(directory).toAbsolutePath().normalize().toUri().toString();
        location = uri.endsWith("/") ? uri : uri + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**").addResourceLocations(location, "classpath:/static/images/");
    }
}
