package com.minhthien.hoser_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:context;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.seed.enabled=false",
        "app.tournament-status.initial-delay-ms=3600000",
        "app.race-reminder.initial-delay-ms=3600000",
        "app.notification-campaign.initial-delay-ms=3600000"
})
class ApplicationContextTest {
    @Test
    void contextLoads() {
    }
}
