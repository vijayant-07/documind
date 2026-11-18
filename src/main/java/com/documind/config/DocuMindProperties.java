package com.documind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "documind")
public class DocuMindProperties {

    private final Github github = new Github();

    public Github getGithub() {
        return github;
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
