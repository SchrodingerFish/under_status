package com.cn.schrodinger.understatus.toolbox.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ToolboxCoreTest {

    @Test
    void jsonPreservesWhitespaceInsideStrings() {
        assertEquals("{\"value\":\"a b\"}", JsonFormatter.minify("{ \"value\" : \"a b\" }"));
    }

    @Test
    void unicodeRoundTripPreservesChineseText() {
        String source = "天气 A";
        assertEquals(source, EncodingConverter.unicodeToString(EncodingConverter.stringToUnicode(source)));
    }

    @Test
    void jwtRejectsMissingPayload() {
        assertTrue(JwtDecoder.decodeJwt("header-only").contains("无效"));
    }

    @Test
    void mathRequiresClosingParenthesis() {
        assertThrows(IllegalArgumentException.class, () -> MathParser.eval("(1 + 2"));
    }

    @Test
    void diffReportsOneDeletionAndOneAddition() {
        List<DiffCalculator.DiffLine> lines = DiffCalculator.calculateDiff("a\nb", "a\nc");
        assertEquals(List.of(0, -1, 1), lines.stream().map(line -> line.type).toList());
    }
}
