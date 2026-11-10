package com.ecommerce.FerreViky.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PaypalConfig {

    @Bean
    public RestClient paypalRestClient(@Value("${paypal.base-url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }
}