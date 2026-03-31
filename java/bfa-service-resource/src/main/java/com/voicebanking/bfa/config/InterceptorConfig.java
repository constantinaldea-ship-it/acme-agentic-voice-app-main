package com.voicebanking.bfa.config;

import com.voicebanking.bfa.interceptor.AuditInterceptor;
import com.voicebanking.bfa.interceptor.ConsentInterceptor;
import com.voicebanking.bfa.interceptor.LegitimationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration for Spring MVC interceptors.
 * 
 * <p>Registers the security interceptors in the correct order:</p>
 * <ol>
 *   <li>ConsentInterceptor - Verify required consents</li>
 *   <li>LegitimationInterceptor - Verify resource access</li>
 *   <li>AuditInterceptor - Log all operations</li>
 * </ol>
 * 
 * @author Augment Agent
 * @since 2026-02-02
 */
@Configuration
public class InterceptorConfig implements WebMvcConfigurer {
    
    private final ConsentInterceptor consentInterceptor;
    private final LegitimationInterceptor legitimationInterceptor;
    private final AuditInterceptor auditInterceptor;
    
    public InterceptorConfig(ConsentInterceptor consentInterceptor,
                              LegitimationInterceptor legitimationInterceptor,
                              AuditInterceptor auditInterceptor) {
        this.consentInterceptor = consentInterceptor;
        this.legitimationInterceptor = legitimationInterceptor;
        this.auditInterceptor = auditInterceptor;
    }
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Order matters: consent first, then legitimation, then audit
        registry.addInterceptor(consentInterceptor)
            .addPathPatterns("/api/v1/**")
            .excludePathPatterns("/api/v1/health", "/actuator/**", "/swagger-ui/**", "/api-docs/**");
        
        registry.addInterceptor(legitimationInterceptor)
            .addPathPatterns("/api/v1/**")
            .excludePathPatterns("/api/v1/health", "/actuator/**", "/swagger-ui/**", "/api-docs/**");
        
        registry.addInterceptor(auditInterceptor)
            .addPathPatterns("/api/v1/**")
            .excludePathPatterns("/actuator/**", "/swagger-ui/**", "/api-docs/**");
    }
}
