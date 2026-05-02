package com.trimlink.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic definitions — topics are auto-created on startup if missing.
 * Each topic has 3 partitions + replication factor 1 (scale up in prod).
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${trimlink.kafka.topics.booking-created}") private String bookingCreated;
    @Value("${trimlink.kafka.topics.booking-cancelled}") private String bookingCancelled;
    @Value("${trimlink.kafka.topics.booking-completed}") private String bookingCompleted;
    @Value("${trimlink.kafka.topics.payment-success}") private String paymentSuccess;
    @Value("${trimlink.kafka.topics.payment-failed}") private String paymentFailed;
    @Value("${trimlink.kafka.topics.queue-updated}") private String queueUpdated;
    @Value("${trimlink.kafka.topics.otp-requested}") private String otpRequested;

    @Bean public NewTopic bookingCreatedTopic() { return topic(bookingCreated); }
    @Bean public NewTopic bookingCancelledTopic() { return topic(bookingCancelled); }
    @Bean public NewTopic bookingCompletedTopic() { return topic(bookingCompleted); }
    @Bean public NewTopic paymentSuccessTopic() { return topic(paymentSuccess); }
    @Bean public NewTopic paymentFailedTopic() { return topic(paymentFailed); }
    @Bean public NewTopic queueUpdatedTopic() { return topic(queueUpdated); }
    @Bean public NewTopic otpRequestedTopic() { return topic(otpRequested); }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
