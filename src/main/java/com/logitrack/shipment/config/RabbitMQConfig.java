package com.logitrack.shipment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de mensajería asíncrona con RabbitMQ para la publicación de eventos de dominio
 * de envíos hacia el ecosistema LogiTrack.
 */
@Configuration
public class RabbitMQConfig {

    public static final String SHIPMENT_EXCHANGE = "shipment.topic.exchange";
    public static final String SHIPMENT_STATUS_CHANGED_ROUTING_KEY = "shipment.status.changed";

    @Bean
    public TopicExchange shipmentTopicExchange() {
        return ExchangeBuilder.topicExchange(SHIPMENT_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
