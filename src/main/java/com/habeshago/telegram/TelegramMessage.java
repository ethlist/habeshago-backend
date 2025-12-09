package com.habeshago.telegram;

import java.util.List;

public class TelegramMessage {
    private String text;
    private String parseMode;
    private List<List<InlineKeyboardButton>> inlineKeyboard;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getParseMode() { return parseMode; }
    public void setParseMode(String parseMode) { this.parseMode = parseMode; }

    public List<List<InlineKeyboardButton>> getInlineKeyboard() { return inlineKeyboard; }
    public void setInlineKeyboard(List<List<InlineKeyboardButton>> inlineKeyboard) { this.inlineKeyboard = inlineKeyboard; }
}
