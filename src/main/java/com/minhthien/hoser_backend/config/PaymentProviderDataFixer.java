package com.minhthien.hoser_backend.config;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Component
@RequiredArgsConstructor
public class PaymentProviderDataFixer implements CommandLineRunner {

    private final EntityManager entityManager;
    private final DataSource dataSource;

    @Override
    @Transactional
    public void run(String... args) throws SQLException {
        if (isH2Database()) {
            return;
        }
        entityManager.createNativeQuery("""
                update payment_orders
                set provider = 'ZALOPAY'
                where provider is null or provider <> 'ZALOPAY'
                """).executeUpdate();
    }

    private boolean isH2Database() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            return productName != null && productName.toLowerCase().contains("h2");
        }
    }
}
