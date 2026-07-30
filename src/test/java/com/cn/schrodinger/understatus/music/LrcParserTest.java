package com.cn.schrodinger.understatus.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LrcParserTest {

    @Test
    void testParseLrc() {
        String lrc = "[00:10.50]First line\n[01:20.00]Second line";
        List<LrcParser.LrcLine> lines = LrcParser.parse(lrc);

        assertEquals(2, lines.size());
        assertEquals(10500, lines.get(0).getTimeMs());
        assertEquals("First line", lines.get(0).getText());
        assertEquals(80000, lines.get(1).getTimeMs());
        assertEquals("Second line", lines.get(1).getText());
    }

    @Test
    void testFindCurrentLineIndex() {
        String lrc = "[00:00.00]Intro\n[00:10.00]Verse 1\n[00:30.00]Chorus";
        List<LrcParser.LrcLine> lines = LrcParser.parse(lrc);

        assertEquals(0, LrcParser.findCurrentLineIndex(lines, 5000));
        assertEquals(1, LrcParser.findCurrentLineIndex(lines, 15000));
        assertEquals(2, LrcParser.findCurrentLineIndex(lines, 45000));
    }
}
