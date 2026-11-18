package com.documind.service;

import com.documind.config.DocuMindProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class WebhookSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureVerifier.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final DocuMindProperties properties;

    public WebhookSignatureVerifier(DocuMindProperties properties) {
        this.properties = properties;
    }

    /**
     * Verifies the GitHub HMAC SHA-256 signature using constant-time comparison.
     *
     * @param signatureHeader The raw X-Hub-Signature-256 header (format: sha256=<hex>)
     * @param payloadBytes    The exact raw request body bytes signed by GitHub
     * @return true if the signature is valid; false otherwise
     */
    public boolean verifySignature(String signatureHeader, byte[] payloadBytes) {
        if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            log.warn("Missing or malformed X-Hub-Signature-256 header");
            return false;
        }

        String secret = properties.getGithub().getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            log.error("GitHub webhook secret is not configured in DocuMindProperties");
            return false;
        }

        if (payloadBytes == null) {
            payloadBytes = new byte[0];
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );
            mac.init(secretKeySpec);

            byte[] computedHmac = mac.doFinal(payloadBytes);
            String computedHex = HexFormat.of().formatHex(computedHmac);
            String expectedHex = signatureHeader.substring(SIGNATURE_PREFIX.length()).trim().toLowerCase();

            // Constant-time comparison to prevent timing attacks
            return MessageDigest.isEqual(
                    computedHex.getBytes(StandardCharsets.UTF_8),
                    expectedHex.getBytes(StandardCharsets.UTF_8)
            );
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Cryptographic error computing webhook HMAC: {}", e.getMessage(), e);
            return false;
        }
    }
}
