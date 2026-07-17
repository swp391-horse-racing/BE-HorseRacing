package com.minhthien.hoser_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:context;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-secret-key-that-is-at-least-sixty-four-characters-long-for-jwt-signing",
        "app.payment.callback-token=test-callback-token",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-key",
        "cloudinary.api-secret=test-secret",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password",
        "google.client-id=test-google-client",
        "facebook.app-id=test-facebook-app",
        "facebook.app-secret=test-facebook-secret",
        "zalopay.app-id=test-zalopay-app",
        "zalopay.key1=test-zalopay-key-1",
        "zalopay.key2=test-zalopay-key-2",
        "app.public-url=http://localhost:8080",
        "zalopay.redirect-url=http://localhost:8080/payment-result",
        "zalopay.callback-url=http://localhost:8080/api/v1/payments/zalopay/callback",
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
