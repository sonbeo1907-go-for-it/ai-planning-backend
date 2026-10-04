package com.codegym.aiplanning.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IanaTimeZoneValidatorTest {

    private IanaTimeZoneValidator validator;

    @BeforeEach
    void setUp() {
        validator = new IanaTimeZoneValidator();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Asia/Ho_Chi_Minh",
        "UTC",
        "America/New_York",
        "Europe/London",
        "Asia/Tokyo",
        "Asia/Bangkok",
        "Australia/Sydney",
        "GMT"
    })
    @DisplayName("Should accept valid standard IANA timezones from Java runtime")
    void shouldAcceptValidIanaTimeZones(String timeZone) {
        assertThat(validator.isValid(timeZone, null)).isTrue();
    }

    @Test
    @DisplayName("Should accept null so @NotNull/@NotBlank handles presence check")
    void shouldAcceptNull() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    @DisplayName("Should accept blank so @NotBlank handles empty string validation")
    void shouldAcceptBlank(String blankString) {
        assertThat(validator.isValid(blankString, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "UTC+7",
        "UTC+07:00",
        "GMT+7",
        "Vietnam",
        "Viet_Nam",
        "abc",
        "Hanoi",
        "SaiGon",
        " Asia/Ho_Chi_Minh ",
        "America/NonExistentCity",
        "12345"
    })
    @DisplayName("Should reject invalid or free-text timezones")
    void shouldRejectInvalidOrFreeTextTimeZones(String invalidTimeZone) {
        assertThat(validator.isValid(invalidTimeZone, null)).isFalse();
    }
}
