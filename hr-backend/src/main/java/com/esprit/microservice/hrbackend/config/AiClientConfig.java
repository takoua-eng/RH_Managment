package com.esprit.microservice.hrbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class AiClientConfig {

    @Bean
    public RestClient aiRestClient(@Value("${ai.service.url}") String url,
                                   @Value("${ai.service.api-key}") String apiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));   // microservice injoignable
        factory.setReadTimeout(Duration.ofSeconds(60));     // analyse trop longue

        return RestClient.builder()
                .baseUrl(url)
                .defaultHeader("X-API-Key", apiKey)
                .requestFactory(factory)
                .build();
    }
}