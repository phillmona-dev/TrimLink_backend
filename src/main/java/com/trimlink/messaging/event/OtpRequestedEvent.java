package com.trimlink.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpRequestedEvent {
    private String phoneNumber;
    private String otpCode;
    private long ttlMinutes;
    private LocalDateTime occurredAt;
}
