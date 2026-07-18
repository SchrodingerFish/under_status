package com.cn.schrodinger.understatus.toolbox.notes;

public record Note(String title, String content) {
    public Note {
        title = title == null || title.isBlank() ? "便签" : title;
        content = content == null ? "" : content;
    }
}
