package com.ada.checkout;

import com.ada.checkout.service.SafeUrlValidator;
import com.ada.checkout.service.SafeUrlValidator.SsrfBlockedException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Evidência unitária da defesa de SSRF (OWASP API7). Usa IPs literais para não depender de DNS.
 */
class SsrfDefenseTest {

    private final SafeUrlValidator validator = new SafeUrlValidator(List.of());

    @Test
    void bloqueiaMetadataCloud() {
        assertThrows(SsrfBlockedException.class,
                () -> validator.validate("https://169.254.169.254/latest/meta-data/"));
    }

    @Test
    void bloqueiaLoopback() {
        assertThrows(SsrfBlockedException.class,
                () -> validator.validate("https://127.0.0.1/admin"));
    }

    @Test
    void bloqueiaRedePrivada() {
        assertThrows(SsrfBlockedException.class,
                () -> validator.validate("https://10.1.2.3/internal"));
    }

    @Test
    void bloqueiaEsquemaNaoHttps() {
        assertThrows(SsrfBlockedException.class,
                () -> validator.validate("http://exemplo-publico.invalido/"));
        assertThrows(SsrfBlockedException.class,
                () -> validator.validate("file:///etc/passwd"));
    }

    @Test
    void permiteIpPublico() {
        assertDoesNotThrow(() -> validator.validate("https://93.184.216.34/webhook"));
    }

    @Test
    void respeitaAllowlist() {
        SafeUrlValidator comAllowlist = new SafeUrlValidator(List.of("hooks.parceiro.com"));
        assertThrows(SsrfBlockedException.class,
                () -> comAllowlist.validate("https://93.184.216.34/webhook")); // IP não está na allowlist
    }
}
