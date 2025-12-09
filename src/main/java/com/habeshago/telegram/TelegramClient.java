package com.habeshago.telegram;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class TelegramClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramClient.class);

    private final RestClient restClient;
    private final String botToken;
    private final boolean devMode;

    public TelegramClient(@Value("${habeshago.telegram.bot-token}") String botToken) {
        this.botToken = botToken;
        this.devMode = botToken == null || botToken.equals("REPLACE_ME");
        this.restClient = RestClient.builder()
                .baseUrl("https://api.telegram.org/bot" + botToken)
                .build();

        if (devMode) {
            log.warn("Telegram bot token not configured - running in DEV MODE. Messages will be logged only.");
        }
    }

    public void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, null, null);
    }

    public void sendMessage(TelegramMessage message, Long chatId) {
        sendMessage(chatId, message.getText(), message.getParseMode(), message.getInlineKeyboard());
    }

    public void sendMessage(Long chatId, String text, String parseMode, List<List<InlineKeyboardButton>> inlineKeyboard) {
        if (devMode) {
            log.info("DEV MODE - Telegram message to {}: {}", chatId, text);
            return;
        }

        try {
            SendMessageRequest request = new SendMessageRequest(
                    chatId,
                    text,
                    parseMode,
                    inlineKeyboard != null ? new InlineKeyboardMarkup(
                            inlineKeyboard.stream()
                                    .map(row -> row.stream()
                                            .map(btn -> new TelegramButton(btn.getText(), btn.getUrl()))
                                            .collect(Collectors.toList()))
                                    .collect(Collectors.toList())
                    ) : null
            );

            restClient.post()
                    .uri("/sendMessage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

            log.debug("Sent Telegram message to {}", chatId);
        } catch (Exception e) {
            log.error("Failed to send Telegram message to {}: {}", chatId, e.getMessage());
            throw e;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SendMessageRequest(
            @JsonProperty("chat_id") Long chatId,
            String text,
            @JsonProperty("parse_mode") String parseMode,
            @JsonProperty("reply_markup") InlineKeyboardMarkup replyMarkup
    ) {}

    public record InlineKeyboardMarkup(
            @JsonProperty("inline_keyboard") List<List<TelegramButton>> inlineKeyboard
    ) {}

    public record TelegramButton(
            String text,
            String url
    ) {}
}
