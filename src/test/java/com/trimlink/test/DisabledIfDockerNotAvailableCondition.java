package com.trimlink.test;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Custom JUnit 5 condition to skip tests if Docker is not available.
 * Useful for local builds in environments without Docker.
 */
public class DisabledIfDockerNotAvailableCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                return ConditionEvaluationResult.enabled("Docker is available");
            }
        } catch (Exception e) {
            // fall through to disabled
        }
        return ConditionEvaluationResult.disabled("Docker is not available - skipping integration test");
    }
}
