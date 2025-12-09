package com.habeshago.telegram;

public class InlineKeyboardButton {
    private String text;
    private String url;

    public InlineKeyboardButton() {}

    public InlineKeyboardButton(String text, String url) {
        this.text = text;
        this.url = url;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}
