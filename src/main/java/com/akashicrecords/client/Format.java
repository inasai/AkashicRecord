package com.akashicrecords.client;

import java.text.NumberFormat;

public final class Format {
    private Format() {
    }

    public static String num(long value) {
        return NumberFormat.getIntegerInstance().format(value);
    }
}
