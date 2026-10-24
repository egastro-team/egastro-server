package com.egastro.config;

import com.egastro.config.properties.RecommendationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;

@Configuration
public class RestClientConfig {
    @Bean
    public RestClient recommendationRestClient(RecommendationProperties properties) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.ml().connectTimeout()).build());
        factory.setReadTimeout(properties.ml().readTimeout());
        return RestClient.builder().requestFactory(factory).build();
    }
}
