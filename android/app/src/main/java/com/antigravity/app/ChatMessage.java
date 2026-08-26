package com.antigravity.app;

public class ChatMessage {
    public static final int TYPE_USER = 1;
    public static final int TYPE_AGENT = 2;

    private final String content;
    private final int type;

    public ChatMessage(String content, int type) {
        this.content = content;
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public int getType() {
        return type;
    }
}
