package com.trimlink.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;

@Configuration
@ConditionalOnProperty(prefix = "trimlink.notification.push", name = "enabled", havingValue = "true")
public class FirebasePushConfig {

    private static final String FIREBASE_APP_NAME = "trimlink-push";

    @Bean
    public FirebaseApp firebaseApp(
            @Value("${trimlink.notification.push.firebase-credentials-path:}") String credentialsPath) throws IOException {
        if (!StringUtils.hasText(credentialsPath)) {
            throw new IllegalStateException("Push notifications are enabled, but Firebase credentials are not configured.");
        }

        return FirebaseApp.getApps().stream()
                .filter(app -> FIREBASE_APP_NAME.equals(app.getName()))
                .findFirst()
                .orElseGet(() -> initializeFirebaseApp(credentialsPath));
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }

    private FirebaseApp initializeFirebaseApp(String credentialsPath) {
        Resource resource = new org.springframework.core.io.DefaultResourceLoader().getResource(credentialsPath);
        if (!resource.exists()) {
            throw new IllegalStateException("Firebase credentials resource not found: " + credentialsPath);
        }

        try (InputStream inputStream = resource.getInputStream()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(inputStream))
                    .build();
            return FirebaseApp.initializeApp(options, FIREBASE_APP_NAME);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to initialize Firebase push configuration.", ex);
        }
    }
}
