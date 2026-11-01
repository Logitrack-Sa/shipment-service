package com.logitrack.shipment.client;

import feign.Feign;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.optionals.OptionalDecoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.concurrent.TimeUnit;

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
    public RequestInterceptor courierBearerRequestInterceptor() {
        return template -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
                throw new AccessDeniedException("Se requiere un Bearer validado para consultar repartidores");
            }
            template.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getToken().getTokenValue());
        };
    }

    CourierFeignClient createClient(CourierProperties properties) {
        ObjectFactory<HttpMessageConverters> converters = HttpMessageConverters::new;
        return Feign.builder()
                .contract(new SpringMvcContract())
                .decoder(new OptionalDecoder(new ResponseEntityDecoder(new SpringDecoder(converters))))
                .options(courierRequestOptions(properties))
                .retryer(courierRetryer())
                .requestInterceptor(courierBearerRequestInterceptor())
                .target(CourierFeignClient.class, properties.baseUrl().toString());
    }
}
