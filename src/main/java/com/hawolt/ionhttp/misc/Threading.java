package com.hawolt.ionhttp.misc;

import java.util.function.BooleanSupplier;

public class Threading {
    public static void snooze(long duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void waitUntil(BooleanSupplier supplier, long delay) {
        while (!supplier.getAsBoolean()) {
            snooze(delay);
        }
    }
}
