package com.momknpay;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ApplicationContextIT {

    @Test
    void contextStartsAgainstPostgres() {
        // passes when the context (datasource, Flyway, JPA validation) starts
    }
}
