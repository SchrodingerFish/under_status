package com.cn.schrodinger.understatus.toolbox.notes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class NoteCodecTest {

    @Test
    void roundTripsDelimiterAndUnicodeContent() {
        List<Note> notes = List.of(new Note("标题:一", "第一行;\n第二行 🌦"));
        NoteCodec codec = new NoteCodec();
        assertEquals(notes, codec.decode(codec.encode(notes)));
    }

    @Test
    void corruptEntriesDoNotDestroyValidEntries() {
        NoteCodec codec = new NoteCodec();
        String valid = codec.encode(List.of(new Note("有效", "内容")));
        assertEquals(List.of(new Note("有效", "内容")), codec.decode(valid + "v1\n%%%"));
    }
}
