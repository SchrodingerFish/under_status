package com.cn.schrodinger.understatus.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class MusicSongTest {

    @Test
    void testSerializationAndDeserialization() {
        MusicSong song1 = new MusicSong("123", "测试歌曲", "歌手A", "专辑B", "netease",
                "http://example.com/a.mp3", "http://example.com/a.jpg", "[00:00]歌词");

        String serialized = song1.serialize();
        assertNotNull(serialized);

        MusicSong song2 = MusicSong.deserialize(serialized);
        assertNotNull(song2);
        assertEquals("123", song2.getId());
        assertEquals("测试歌曲", song2.getName());
        assertEquals("歌手A", song2.getArtist());
        assertEquals("专辑B", song2.getAlbum());
        assertEquals("netease", song2.getSource());
        assertEquals("http://example.com/a.mp3", song2.getUrl());
    }

    @Test
    void testListSerialization() {
        MusicSong song1 = new MusicSong("1", "Song1", "Art1", "Alb1", "netease");
        MusicSong song2 = new MusicSong("2", "Song2", "Art2", "Alb2", "tencent");

        List<MusicSong> list = List.of(song1, song2);
        String data = MusicSong.serializeList(list);

        List<MusicSong> restored = MusicSong.deserializeList(data);
        assertEquals(2, restored.size());
        assertEquals("Song1", restored.get(0).getName());
        assertEquals("Song2", restored.get(1).getName());
    }
}
