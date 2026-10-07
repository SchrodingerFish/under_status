package com.cn.schrodinger.understatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cn.schrodinger.understatus.core.JsonSupport;
import org.junit.jupiter.api.Test;

class JsonSupportTest {
    @Test
    void rejectsExtremeDecimalScalesBeforeReturningNumbersToConsumers() {
        for (String value : new String[]{"1e1025", "1e-1025", "1e100000000", "-1e100000000",
                "1e-100000000", "-1e-100000000", "1e2147483647", "1e-2147483647"}) {
            assertThrows(IllegalArgumentException.class, () -> JsonSupport.parse(value), value);
            assertThrows(IllegalArgumentException.class,
                    () -> JsonSupport.parse("{\"outer\":[{\"number\":" + value + "}]}"), value);
        }
    }

    @Test
    void permitsTheDocumentedDecimalScaleBoundary() {
        assertEquals(-1024, JsonSupport.parse("1e1024").decimalValue().scale());
        assertEquals(1024, JsonSupport.parse("1e-1024").decimalValue().scale());
        assertEquals(0, JsonSupport.parse("0e-100000000").decimalValue().signum());
    }

    @Test
    void preservesDecimalPrecision() {
        String source = "{\"n\":1516239022.123456789}";
        assertEquals(source, JsonSupport.write(JsonSupport.parse(source), false));
    }

    @Test
    void rejectsAmbiguousOrUnboundedJson() {
        assertThrows(IllegalArgumentException.class, () -> JsonSupport.parse("{\"exp\":1,\"exp\":2}"));
        assertThrows(IllegalArgumentException.class, () -> JsonSupport.parse("{} {}"));
        assertThrows(IllegalArgumentException.class, () -> JsonSupport.parse("[".repeat(140) + "0" + "]".repeat(140)));
        assertThrows(IllegalArgumentException.class, () -> JsonSupport.parse(" ".repeat(1_000_001)));
    }
}
