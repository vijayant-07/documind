package com.documind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "documind")
public class DocuMindProperties {

    private final Github github = new Github();
    private final Worker worker = new Worker();

    public Github getGithub() {
        return github;
    }

    public Worker getWorker() {
        return worker;
    }

    public static class Worker {
        private boolean enabled = true;
        private long pollIntervalMs = 5000;
        private int batchSize = 10;
        private int maxAttempts = 3;
        private long backoffInitialIntervalSeconds = 10;
        private double backoffMultiplier = 2.0;
        private long backoffMaxIntervalSeconds = 300;
        private int stuckJobTimeoutMinutes = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getBackoffInitialIntervalSeconds() {
            return backoffInitialIntervalSeconds;
        }

        public void setBackoffInitialIntervalSeconds(long backoffInitialIntervalSeconds) {
            this.backoffInitialIntervalSeconds = backoffInitialIntervalSeconds;
        }

        public double getBackoffMultiplier() {
            return backoffMultiplier;
        }

        public void setBackoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
        }

        public long getBackoffMaxIntervalSeconds() {
            return backoffMaxIntervalSeconds;
        }

        public void setBackoffMaxIntervalSeconds(long backoffMaxIntervalSeconds) {
            this.backoffMaxIntervalSeconds = backoffMaxIntervalSeconds;
        }

        public int getStuckJobTimeoutMinutes() {
            return stuckJobTimeoutMinutes;
        }

        public void setStuckJobTimeoutMinutes(int stuckJobTimeoutMinutes) {
            this.stuckJobTimeoutMinutes = stuckJobTimeoutMinutes;
        }
    }

    public static class Github {
        private String webhookSecret = "test-webhook-secret";
        private List<String> allowedBranches = new ArrayList<>(List.of("main", "master"));
        private String branchPrefix = "documind/docs-pr-";
        private String prBodyMarker = "<!-- documind-generated -->";

        public String getWebhookSecret() {
            return webhookSecret;
        }

        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }

        public List<String> getAllowedBranches() {
            return allowedBranches;
        }

        public void setAllowedBranches(List<String> allowedBranches) {
            this.allowedBranches = allowedBranches;
        }

        public String getBranchPrefix() {
            return branchPrefix;
        }

        public void setBranchPrefix(String branchPrefix) {
            this.branchPrefix = branchPrefix;
        }

        public String getPrBodyMarker() {
            return prBodyMarker;
        }

        public void setPrBodyMarker(String prBodyMarker) {
            this.prBodyMarker = prBodyMarker;
        }
    }
}
