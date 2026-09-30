package com.momknpay.common.config;

import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Shared infrastructure beans: typed properties, the clock, the CSPRNG and the PIN hasher. */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class CoreConfig {

    /** The only source of "now" (CODING_STANDARDS §9.2). Tests replace it with a fixed clock. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** The only source of randomness for keys, IVs, nonces and ids (CODING_STANDARDS §10.2). */
    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }

    /** PINs are only ever stored and compared as bcrypt hashes, cost 12 (NFR-SEC-5). */
    @Bean
    PasswordEncoder pinEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
