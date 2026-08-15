package com.banksphere.beneficiary.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * Forces the JVM default timezone to UTC so all internally generated
 * timestamps (JPA auditing, logging) are UTC regardless of host locale.
 */
@Configuration
public class TimeZoneConfig {

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}
