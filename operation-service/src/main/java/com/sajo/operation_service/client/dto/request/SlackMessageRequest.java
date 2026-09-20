package com.sajo.operation_service.client.dto.request;

import java.util.List;

public record SlackMessageRequest(List<Attachment> attachments) {

    public static SlackMessageRequest of(String color, String text) {
        return new SlackMessageRequest(List.of(new Attachment(color, text)));
    }

    public record Attachment(String color, String text) {
    }
}
