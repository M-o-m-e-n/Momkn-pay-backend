package com.momknpay.payment.engine;

import java.util.List;

/**
 * Deterministic, masked customer names: the same subscriber number always shows the same name (SRS
 * §6.4). {@code 1024750891} → digit sum 37 → index 7 → "Mina A.", as in the sample UI.
 */
public final class CustomerNames {

    private static final List<String> NAMES =
            List.of(
                    "Ahmed S.",
                    "Fatma H.",
                    "Mohamed K.",
                    "Nour E.",
                    "Youssef M.",
                    "Salma R.",
                    "Omar T.",
                    "Mina A.",
                    "Heba F.",
                    "Karim N.");

    private CustomerNames() {}

    public static String forSubscriber(String subscriberNumber) {
        int digitSum = subscriberNumber.chars().filter(Character::isDigit).map(c -> c - '0').sum();
        return NAMES.get(digitSum % NAMES.size());
    }
}
