package com.banksphere.customer.otp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the dev-only OTP inbox route is genuinely ABSENT from the
 * application context when disabled — not merely returning 403/404 at
 * request time from a permission check that a future change could
 * accidentally weaken, but the bean/route never being registered at all.
 * See DevOtpInboxController's own javadoc and ADR-009.
 *
 * <p>The component scan below is deliberately restricted (via {@code
 * includeFilters} + {@code useDefaultFilters = false}) to only {@link
 * DevOtpInbox} and {@link DevOtpInboxController} out of the whole {@code
 * otp} package — every other class there (JPA-repository-backed services,
 * etc.) has nothing to do with this test and would otherwise fail to
 * construct in this minimal context. Restricting via an include filter
 * (rather than hand-registering a {@code @Bean} for the controller) is
 * what preserves {@code DevOtpInboxController}'s own {@code
 * @ConditionalOnProperty} — a real component-scan candidate still has its
 * condition evaluated; a bean manually returned from a factory method
 * would not.
 */
class DevOtpInboxControllerTest {

    @Configuration
    @EnableConfigurationProperties(DevOtpInboxProperties.class)
    @ComponentScan(basePackageClasses = DevOtpInboxController.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {DevOtpInbox.class, DevOtpInboxController.class}))
    static class TestConfig {
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void controllerBean_doesNotExist_whenDevInboxIsDisabled() {
        contextRunner.withPropertyValues("banksphere.otp.dev-inbox.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DevOtpInboxController.class));
    }

    @Test
    void controllerBean_doesNotExist_whenPropertyIsAbsentEntirely() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(DevOtpInboxController.class));
    }

    @Test
    void controllerBean_exists_whenDevInboxIsExplicitlyEnabled() {
        contextRunner.withPropertyValues("banksphere.otp.dev-inbox.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(DevOtpInboxController.class));
    }
}
