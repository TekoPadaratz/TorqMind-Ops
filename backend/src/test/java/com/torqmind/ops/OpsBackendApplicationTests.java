package com.torqmind.ops;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ops_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.task.scheduling.enabled=false",
        "app.jwt.secret=test-only-secret-with-at-least-32-characters"
})
class OpsBackendApplicationTests {

    @Test
    void contextLoads() {
    }
}
