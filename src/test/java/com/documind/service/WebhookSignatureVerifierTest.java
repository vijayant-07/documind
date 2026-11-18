package com.documind.service;

import com.documind.config.DocuMindProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSignatureVerifierTest {

    private static final String SECRET = "my-test-webhook-secret-12345";
    private DocuMindProperties properties;
    private WebhookSignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        properties = new DocuMindProperties();
        properties.getGithub().setWebhookSecret(SECRET);
        verifier = new WebhookSignatureVerifier(properties);
    }

    @Test
    void verifySignatureShouldReturnTrueForValidSignature() throws Exception {
        byte[] payload = "{\"action\":\"closed\",\"number\":42}".getBytes(StandardCharsets.UTF_8);
        String validSignature = "sha256=" + computeHmac(SECRET, payload);

        assertTrue(verifier.verifySignature(validSignature, payload));
    }

    @Test
    void verifySignatureShouldReturnFalseForTamperedPayload() throws Exception {
        byte[] originalPayload = "{\"action\":\"closed\",\"number\":42}".getBytes(StandardCharsets.UTF_8);
        byte[] tamperedPayload = "{\"action\":\"closed\",\"number\":43}".getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=" + computeHmac(SECRET, originalPayload);

        assertFalse(verifier.verifySignature(signature, tamperedPayload));
    }

    @Test
    void verifySignatureShouldReturnFalseForWrongSecret() throws Exception {
        byte[] payload = "{\"action\":\"closed\",\"number\":42}".getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=" + computeHmac("wrong-secret", payload);

        assertFalse(verifier.verifySignature(signature, payload));
    }

    @Test
    void verifySignatureShouldReturnFalseForMissingSha256Prefix() throws Exception {
        byte[] payload = "{\"action\":\"closed\",\"number\":42}".getBytes(StandardCharsets.UTF_8);
        String rawHex = computeHmac(SECRET, payload);

        assertFalse(verifier.verifySignature(rawHex, payload));
    }

    @Test
    void verifySignatureShouldReturnFalseForNullOrBlankSignature() {
        byte[] payload = "{\"action\":\"closed\"}".getBytes(StandardCharsets.UTF_8);

        assertFalse(verifier.verifySignature(null, payload));
        assertFalse(verifier.verifySignature("", payload));
        assertFalse(verifier.verifySignature("   ", payload));
    }

    @Test
    void verifySignatureShouldReturnFalseWhenSecretNotConfigured() throws Exception {
        properties.getGithub().setWebhookSecret(null);
        byte[] payload = "{\"action\":\"closed\"}".getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=" + computeHmac(SECRET, payload);

        assertFalse(verifier.verifySignature(signature, payload));
    }

    @Test
    void verifySignatureShouldHandleNullPayloadAsEmptyArray() throws Exception {
        byte[] emptyPayload = new byte[0];
        String signature = "sha256=" + computeHmac(SECRET, emptyPayload);

        assertTrue(verifier.verifySignature(signature, null));
    }

    private String computeHmac(String secret, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        return HexFormat.of().formatHex(mac.doFinal(data));
    }
}
