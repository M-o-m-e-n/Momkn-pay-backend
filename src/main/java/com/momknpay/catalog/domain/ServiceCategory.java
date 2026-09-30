package com.momknpay.catalog.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** Service category. Lowercase on the wire and in the database. */
public enum ServiceCategory {
    ELECTRICITY,
    WATER,
    GAS,
    INTERNET,
    MOBILE,
    LANDLINE;

    @JsonValue
    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static ServiceCategory fromWire(String value) {
        return Arrays.stream(values())
                .filter(category -> category.wire().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown category: " + value));
    }
}
