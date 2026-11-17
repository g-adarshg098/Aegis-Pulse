package com.aegispulse.engine.guardrail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class SecurityGuardrailService {

    // Regex patterns for sensitive data (handles formatted with hyphens/spaces and raw digits)
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\b(?:4[0-9]{3}(?:[- ]?[0-9]{4}){3}|5[1-5][0-9]{2}(?:[- ]?[0-9]{4}){3}|3[47][0-9]{2}(?:[- ]?[0-9]{6}[- ]?[0-9]{5}|(?:[- ]?[0-9]{4}){3})|6(?:011|5[0-9]{2})(?:[- ]?[0-9]{4}){3})\\b"
    );

    private static final Pattern SSN_PATTERN = Pattern.compile(
            "\\b(?!000|666|9\\d{2})\\d{3}-(?!00)\\d{2}-(?!0000)\\d{4}\\b"
    );

    private static final Pattern API_KEY_PATTERN = Pattern.compile(
            "(?i)\\b(?:gsk_[a-zA-Z0-9]{32,}|sk-[a-zA-Z0-9]{32,}|AKIA[0-9A-Z]{16}|bearer\\s+[a-zA-Z0-9_\\-\\.]+)\\b"
    );

    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
            "-----BEGIN (?:RSA )?PRIVATE KEY-----"
    );

    private static final List<String> CONFIDENTIAL_KEYWORDS = List.of(
            "strictly confidential",
            "internal only",
            "trade secret",
            "proprietary and confidential",
            "nda protected",
            "classified information"
    );

    public boolean isConfidential(String text, Boolean explicitFlag) {
        if (Boolean.TRUE.equals(explicitFlag)) {
            return true;
        }
        if (text == null || text.isBlank()) {
            return false;
        }

        List<String> findings = detectSensitiveSignals(text);
        if (!findings.isEmpty()) {
            log.info("Aegis Guardrail identified sensitive content: {}", findings);
            return true;
        }

        return false;
    }

    public List<String> detectSensitiveSignals(String text) {
        List<String> signals = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return signals;
        }

        if (CREDIT_CARD_PATTERN.matcher(text).find()) {
            signals.add("Credit Card Number");
        }
        if (SSN_PATTERN.matcher(text).find()) {
            signals.add("Social Security Number");
        }
        if (API_KEY_PATTERN.matcher(text).find()) {
            signals.add("API Key / Bearer Token");
        }
        if (PRIVATE_KEY_PATTERN.matcher(text).find()) {
            signals.add("Private Cryptographic Key");
        }

        String lower = text.toLowerCase();
        for (String keyword : CONFIDENTIAL_KEYWORDS) {
            if (lower.contains(keyword)) {
                signals.add("Confidential Tag: '" + keyword + "'");
            }
        }

        return signals;
    }

    public String redactSensitiveData(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String sanitized = CREDIT_CARD_PATTERN.matcher(text).replaceAll("[REDACTED_CARD]");
        sanitized = SSN_PATTERN.matcher(sanitized).replaceAll("[REDACTED_SSN]");
        sanitized = API_KEY_PATTERN.matcher(sanitized).replaceAll("[REDACTED_KEY]");
        sanitized = PRIVATE_KEY_PATTERN.matcher(sanitized).replaceAll("[REDACTED_PRIVATE_KEY]");

        return sanitized;
    }
}
