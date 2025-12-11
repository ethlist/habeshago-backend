package com.habeshago.telegram;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habeshago.notification.NotificationOutbox;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TelegramMessageFormatter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public TelegramMessage formatNotification(NotificationOutbox notification) {
        Map<String, Object> payload = parsePayload(notification.getPayload());
        String type = (String) payload.get("type");

        if (type == null) {
            type = notification.getType();
        }

        return switch (type) {
            case "REQUEST_ACCEPTED" -> formatRequestAccepted(payload);
            case "REQUEST_ACCEPTED_TRAVELER" -> formatRequestAcceptedTraveler(payload);
            case "REQUEST_DELIVERED" -> formatRequestDelivered(payload);
            case "REQUEST_REJECTED" -> formatRequestRejected(payload);
            case "NEW_REQUEST" -> formatNewRequest(payload);
            case "TRIP_CANCELLED" -> formatTripCancelled(payload);
            default -> formatGeneric(payload);
        };
    }

    private TelegramMessage formatRequestAccepted(Map<String, Object> payload) {
        StringBuilder text = new StringBuilder();
        text.append("✅ *Your request was accepted!*\n\n");
        text.append("📦 ").append(payload.get("itemDescription")).append("\n");
        text.append("✈️ ").append(payload.get("route")).append("\n");
        text.append("📅 ").append(payload.get("departureDate")).append("\n\n");

        // Traveler info
        text.append("🧳 *Traveler:* ").append(payload.get("travelerFirstName"));
        Object lastName = payload.get("travelerLastName");
        if (lastName != null) {
            String lastNameStr = lastName.toString();
            if (!lastNameStr.isEmpty()) {
                text.append(" ").append(lastNameStr.charAt(0)).append(".");
            }
        }

        Boolean verified = (Boolean) payload.get("travelerVerified");
        if (verified != null && verified) {
            text.append(" ✓");
        }

        Object rating = payload.get("travelerRating");
        if (rating != null) {
            text.append(String.format(" (%.1f⭐)", ((Number) rating).doubleValue()));
        }
        text.append("\n\n");

        text.append("_Tap below to coordinate pickup details:_");

        TelegramMessage message = new TelegramMessage();
        message.setText(text.toString());
        message.setParseMode("Markdown");

        // Add inline keyboard with contact button
        String contactUrl = (String) payload.get("contactUrl");
        String buttonText = (String) payload.get("contactButtonText");
        if (contactUrl != null && buttonText != null) {
            message.setInlineKeyboard(List.of(
                    List.of(new InlineKeyboardButton("💬 " + buttonText, contactUrl))
            ));
        }

        return message;
    }

    private TelegramMessage formatRequestAcceptedTraveler(Map<String, Object> payload) {
        StringBuilder text = new StringBuilder();
        text.append("📦 *You accepted a new request!*\n\n");
        text.append("*Item:* ").append(payload.get("itemDescription")).append("\n");

        Object weight = payload.get("itemWeight");
        if (weight != null) {
            text.append("*Weight:* ").append(weight).append(" kg\n");
        }

        String instructions = (String) payload.get("specialInstructions");
        if (instructions != null && !instructions.isBlank()) {
            text.append("*Instructions:* ").append(instructions).append("\n");
        }

        text.append("\n");
        text.append("👤 *Sender:* ").append(payload.get("senderFirstName"));
        Object lastName = payload.get("senderLastName");
        if (lastName != null) {
            String lastNameStr = lastName.toString();
            if (!lastNameStr.isEmpty()) {
                text.append(" ").append(lastNameStr.charAt(0)).append(".");
            }
        }
        text.append("\n\n");

        text.append("_Tap below to coordinate with sender:_");

        TelegramMessage message = new TelegramMessage();
        message.setText(text.toString());
        message.setParseMode("Markdown");

        String contactUrl = (String) payload.get("contactUrl");
        String buttonText = (String) payload.get("contactButtonText");
        if (contactUrl != null && buttonText != null) {
            message.setInlineKeyboard(List.of(
                    List.of(new InlineKeyboardButton("💬 " + buttonText, contactUrl))
            ));
        }

        return message;
    }

    private TelegramMessage formatRequestDelivered(Map<String, Object> payload) {
        StringBuilder text = new StringBuilder();
        text.append("🎉 *Your item was delivered!*\n\n");
        text.append("📦 ").append(payload.get("itemDescription")).append("\n");
        text.append("✈️ ").append(payload.get("route")).append("\n\n");
        text.append("Thanks to *").append(payload.get("travelerFirstName")).append("* for carrying your item!\n\n");
        text.append("_How was your experience? Leave a review in the app._");

        TelegramMessage message = new TelegramMessage();
        message.setText(text.toString());
        message.setParseMode("Markdown");

        return message;
    }

    private TelegramMessage formatRequestRejected(Map<String, Object> payload) {
        StringBuilder text = new StringBuilder();
        text.append("❌ *Request not accepted*\n\n");
        text.append("📦 ").append(payload.get("itemDescription")).append("\n");
        text.append("✈️ ").append(payload.get("route")).append("\n\n");
        text.append((String) payload.get("message"));

        TelegramMessage message = new TelegramMessage();
        message.setText(text.toString());
        message.setParseMode("Markdown");

        return message;
    }

    private TelegramMessage formatNewRequest(Map<String, Object> payload) {
        StringBuilder text = new StringBuilder();
        text.append("📬 *New item request!*\n\n");
        text.append("📦 ").append(payload.get("itemDescription")).append("\n");

        Object weight = payload.get("itemWeight");
        if (weight != null) {
            text.append("⚖️ ").append(weight).append(" kg\n");
        }

        text.append("✈️ ").append(payload.get("route")).append("\n");
        text.append("📅 ").append(payload.get("departureDate")).append("\n\n");
        text.append("👤 From: ").append(payload.get("senderFirstName")).append("\n\n");
        text.append("_Open the app to accept or decline._");

        TelegramMessage message = new TelegramMessage();
        message.setText(text.toString());
        message.setParseMode("Markdown");

        return message;
    }

    private TelegramMessage formatTripCancelled(Map<String, Object> payload) {
        StringBuilder text = new StringBuilder();
        text.append("❌ *Trip cancelled by traveler*\n\n");
        text.append("📦 ").append(payload.get("itemDescription")).append("\n");
        text.append("✈️ ").append(payload.get("route")).append("\n");
        text.append("📅 ").append(payload.get("departureDate")).append("\n\n");

        String reason = (String) payload.get("reason");
        if (reason != null && !reason.isBlank() && !"No reason provided".equals(reason)) {
            text.append("*Reason:* ").append(reason).append("\n\n");
        }

        text.append("_Your request has been automatically cancelled. ");
        text.append("You can search for other travelers going to your destination._");

        TelegramMessage message = new TelegramMessage();
        message.setText(text.toString());
        message.setParseMode("Markdown");

        return message;
    }

    private TelegramMessage formatGeneric(Map<String, Object> payload) {
        TelegramMessage message = new TelegramMessage();
        message.setText((String) payload.getOrDefault("title", "You have a new notification"));
        return message;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
