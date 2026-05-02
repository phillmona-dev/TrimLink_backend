package com.trimlink.module.notification.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.Map;

@Getter
@Builder
public class PushMessage {
    private final String title;
    private final String body;
    @Builder.Default
    private final Map<String, String> data = Collections.emptyMap();
}
