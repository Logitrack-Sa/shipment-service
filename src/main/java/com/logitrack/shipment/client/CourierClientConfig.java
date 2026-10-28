package com.logitrack.shipment.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CourierProperties.class)
public class CourierClientConfig {
    @Bean
    RestClient courierRestClient(RestClient.Builder builder, CourierProperties properties) {
        var client = HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.readTimeout());
        return builder.baseUrl(properties.baseUrl().toString()).requestFactory(factory).build();
    }
}
