package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceSettingsRequestValidationTest {
    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsSupportedWinningTaxPercentValues() {
        assertValid("0");
        assertValid("10");
        assertValid("12.50");
        assertValid("100");
    }

    @Test
    void rejectsOutOfRangeOrOverPreciseWinningTaxPercentValues() {
        assertInvalid("-0.01");
        assertInvalid("100.01");
        assertInvalid("10.123");
    }

    private void assertValid(String value) {
        FinanceSettingsRequest request = new FinanceSettingsRequest();
        request.setBetWinningTaxPercent(new BigDecimal(value));
        assertTrue(validator.validate(request).isEmpty(), () -> "Expected valid tax percent: " + value);
    }

    private void assertInvalid(String value) {
        FinanceSettingsRequest request = new FinanceSettingsRequest();
        request.setBetWinningTaxPercent(new BigDecimal(value));
        assertFalse(validator.validate(request).isEmpty(), () -> "Expected invalid tax percent: " + value);
    }
}
