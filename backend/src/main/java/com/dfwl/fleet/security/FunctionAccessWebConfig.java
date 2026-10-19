package com.dfwl.fleet.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class FunctionAccessWebConfig implements WebMvcConfigurer {
    private final FunctionAccessGuard guard;

    public FunctionAccessWebConfig(FunctionAccessGuard guard) { this.guard = guard; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(guard).addPathPatterns("/api/v1/**");
    }
}
