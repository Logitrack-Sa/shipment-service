package com.logitrack.shipment.client;

import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = CourierFeignClient.class)
@EnableConfigurationProperties(CourierProperties.class)
public class CourierClientConfig {

    @Bean
    public Request.Options courierRequestOptions(CourierProperties properties) {
        return new Request.Options(
                properties.connectTimeout().toMillis(), TimeUnit.MILLISECONDS,
                properties.readTimeout().toMillis(), TimeUnit.MILLISECONDS,
                false
        );
    }

    @Bean
    public Retryer courierRetryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    public ErrorDecoder courierErrorDecoder() {
        return new CourierErrorDecoder();
    }

    @Bean
    public RequestInterceptor courierBearerRequestInterceptor() {
        return template -> {
            if (template.headers().containsKey(HttpHeaders.AUTHORIZATION)) {
                return;
            }
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
                throw new AccessDeniedException("Se requiere un Bearer validado para consultar repartidores");
            }
            template.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getToken().getTokenValue());
        };
    }
}
