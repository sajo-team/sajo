package com.sajo.common.config;

import com.sajo.common.web.CommonPageableArgumentResolver;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.data.autoconfigure.web.DataWebAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(PageableHandlerMethodArgumentResolver.class)
@AutoConfigureBefore(DataWebAutoConfiguration.class)
public class CommonPageableAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PageableHandlerMethodArgumentResolver.class)
    PageableHandlerMethodArgumentResolver pageableHandlerMethodArgumentResolver() {
        return new CommonPageableArgumentResolver();
    }

    @Bean
    WebMvcConfigurer commonPageableWebMvcConfigurer(PageableHandlerMethodArgumentResolver pageableHandlerMethodArgumentResolver) {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(@NonNull List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(pageableHandlerMethodArgumentResolver);
            }
        };
    }
}
