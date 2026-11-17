package com.aegispulse.engine.guardrail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecurityGuardrailServiceTest {

    private SecurityGuardrailService guardrailService;

    @BeforeEach
    void setUp() {
        guardrailService = new SecurityGuardrailService();
    }

    @Test
    void testDetectCreditCard() {
        String prompt = "Please process payment for card 4111-1111-1111-1111 today.";
        List<String> signals = guardrailService.detectSensitiveSignals(prompt);
        assertTrue(signals.contains("Credit Card Number"));
        assertTrue(guardrailService.isConfidential(prompt, false));

        String redacted = guardrailService.redactSensitiveData(prompt);
        assertTrue(redacted.contains("[REDACTED_CARD]"));
        assertFalse(redacted.contains("4111-1111-1111-1111"));
    }

    @Test
    void testDetectSSN() {
        String prompt = "My SSN is 123-45-6789, please register me.";
        List<String> signals = guardrailService.detectSensitiveSignals(prompt);
        assertTrue(signals.contains("Social Security Number"));
        assertTrue(guardrailService.isConfidential(prompt, null));

        String redacted = guardrailService.redactSensitiveData(prompt);
        assertTrue(redacted.contains("[REDACTED_SSN]"));
        assertFalse(redacted.contains("123-45-6789"));
    }

    @Test
    void testDetectApiKey() {
        String prompt = "Use this key gsk_1234567890abcdef1234567890abcdef1234 to authenticate.";
        List<String> signals = guardrailService.detectSensitiveSignals(prompt);
        assertTrue(signals.contains("API Key / Bearer Token"));
        assertTrue(guardrailService.isConfidential(prompt, false));
    }

    @Test
    void testDetectConfidentialKeyword() {
        String prompt = "This report contains trade secret formulas for project Aegis.";
        List<String> signals = guardrailService.detectSensitiveSignals(prompt);
        assertFalse(signals.isEmpty());
        assertTrue(guardrailService.isConfidential(prompt, false));
    }

    @Test
    void testExplicitConfidentialFlag() {
        String nonSensitivePrompt = "What is the capital of France?";
        assertFalse(guardrailService.isConfidential(nonSensitivePrompt, false));
        assertTrue(guardrailService.isConfidential(nonSensitivePrompt, true));
    }
}
