package com.banksphere.account.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties({TransactionServiceProperties.class, CustomerServiceProperties.class})
public class RestClientConfig {

    // Without explicit timeouts the underlying HTTP client blocks
    // indefinitely if transaction-service is slow or unreachable, holding
    // open both a web server thread and the caller's local DB transaction
    // (deposit/withdraw call this synchronously while their own
    // @Transactional method is still active).
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public RestClient transactionServiceRestClient(TransactionServiceProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(CONNECT_TIMEOUT)
                .withReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }

    /** Phase 9D — step-up confirmation calls to customer-service. Same timeout rationale as above: this call happens synchronously inside AccountServiceImpl.transfer's own @Transactional method. */
    @Bean
    public RestClient customerServiceRestClient(CustomerServiceProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(CONNECT_TIMEOUT)
                .withReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }
}
