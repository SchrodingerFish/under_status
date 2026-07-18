package com.cn.schrodinger.understatus;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BuildBaselineTest {

    @Test
    void runsOnJava17OrNewer() {
        assertTrue(Runtime.version().feature() >= 17);
    }
}
