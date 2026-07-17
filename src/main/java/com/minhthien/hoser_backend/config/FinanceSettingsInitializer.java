package com.minhthien.hoser_backend.config;

import com.minhthien.hoser_backend.service.FinanceSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
@RequiredArgsConstructor
public class FinanceSettingsInitializer implements ApplicationRunner {

    private final FinanceSettingsService financeSettingsService;

    @Override
    public void run(ApplicationArguments args) {
        financeSettingsService.getOrCreateSettings();
    }
}
