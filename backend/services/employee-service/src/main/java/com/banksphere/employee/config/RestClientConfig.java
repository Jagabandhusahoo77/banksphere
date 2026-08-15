package com.banksphere.employee.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** Same timeout rationale as account-service's identically-shaped RestClientConfig: never block a request thread indefinitely on a slow/unreachable downstream service. */
@Configuration
@EnableConfigurationProperties(DownstreamServiceProperties.class)
public class RestClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public RestClient accountServiceRestClient(DownstreamServiceProperties properties) {
        return build(properties.accountServiceBaseUrl());
    }

    @Bean
    public RestClient customerServiceRestClient(DownstreamServiceProperties properties) {
        return build(properties.customerServiceBaseUrl());
    }

    @Bean
    public RestClient transactionServiceRestClient(DownstreamServiceProperties properties) {
        return build(properties.transactionServiceBaseUrl());
    }

    @Bean
    public RestClient beneficiaryServiceRestClient(DownstreamServiceProperties properties) {
        return build(properties.beneficiaryServiceBaseUrl());
    }

    @Bean
    public RestClient kycServiceRestClient(DownstreamServiceProperties properties) {
        return build(properties.kycServiceBaseUrl());
    }

    private RestClient build(String baseUrl) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(CONNECT_TIMEOUT)
                .withReadTimeout(READ_TIMEOUT);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }
}
