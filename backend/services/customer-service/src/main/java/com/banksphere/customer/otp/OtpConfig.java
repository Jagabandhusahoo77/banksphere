package com.banksphere.customer.otp;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({OtpProperties.class, DevOtpInboxProperties.class, RefreshTokenProperties.class})
public class OtpConfig {
}
