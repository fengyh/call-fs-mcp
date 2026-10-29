/*
 * Copyright [2022] [https://www.xiaonuo.vip]
 *
 * Snowy采用APACHE LICENSE 2.0开源协议，您在使用过程中，需要注意以下几点：
 *
 * 1.请不要删除和修改根目录下的LICENSE文件。
 * 2.请不要删除和修改Snowy源码头部的版权声明。
 * 3.本项目代码可免费商业使用，商业使用请保留源码和相关描述文件的项目出处，作者声明等。
 * 4.分发源码时候，请注明软件出处 https://www.xiaonuo.vip
 * 5.不可二次分发开源参与同类竞品，如有想法可联系团队xiaonuobase@qq.com商议合作。
 * 6.若您的项目无法满足以上几点，需要更多功能代码，获取Snowy商业授权许可，请在官网购买授权，地址为 https://www.xiaonuo.vip
 */
package vip.xiaonuo.fscloud.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import vip.xiaonuo.fscloud.core.apilog.ApiLogInterceptor;
import vip.xiaonuo.fscloud.core.apilog.ApiLogRecorder;

/**
 * API 访问日志拦截器注册，仅在 fscloud.api-log.enabled=true 时生效
 *
 * @author fengyuhe
 * @date  2026/10/29
 */
@Configuration
@ConditionalOnProperty(prefix = "fscloud.api-log", name = "enabled", havingValue = "true")
public class ApiLogConfigure implements WebMvcConfigurer {

    /**
     * 静态资源、文档、监控与错误页不记录
     */
    private static final String[] EXCLUDED_PATH_ARR = {
            "/static/**",
            "/mobile/**",
            "/favicon.ico",
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/druid/**",
            "/error"
    };

    private final ApiLogRecorder apiLogRecorder;

    public ApiLogConfigure(ApiLogRecorder apiLogRecorder) {
        this.apiLogRecorder = apiLogRecorder;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ApiLogInterceptor(apiLogRecorder))
                .addPathPatterns("/**")
                .excludePathPatterns(EXCLUDED_PATH_ARR);
    }
}
