package com.ada.checkout.web;

import com.ada.checkout.service.SafeUrlValidator;
import com.ada.checkout.service.SafeUrlValidator.SsrfBlockedException;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

/**
 * Registro de URL de callback de pagamento — superfície clássica de SSRF (OWASP API7).
 *
 * <p>A URL fornecida pelo cliente passa obrigatoriamente por {@link SafeUrlValidator}
 * antes de qualquer uso. Destinos internos (loopback, metadata cloud, redes privadas)
 * são rejeitados com 400 e o motivo é registrado para a trilha de auditoria do BlueTeam.
 */
@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private final SafeUrlValidator urlValidator;

    public WebhookController(SafeUrlValidator urlValidator) {
        this.urlValidator = urlValidator;
    }

    public record CallbackRequest(@NotBlank String callbackUrl) {}

    @PostMapping("/payment-callback")
    @PreAuthorize("hasAuthority('SCOPE_orders.write')")
    public ResponseEntity<Map<String, String>> registerCallback(@RequestBody CallbackRequest req) {
        URI safe = urlValidator.validate(req.callbackUrl()); // lança SsrfBlockedException se proibido
        // Em produção, a chamada de saída usaria um cliente com timeouts curtos, sem
        // seguir redirects para IPs internos e revalidando o IP no momento da conexão.
        return ResponseEntity.accepted().body(Map.of(
                "status", "callback_registered",
                "host", safe.getHost()));
    }

    @ExceptionHandler(SsrfBlockedException.class)
    public ResponseEntity<Map<String, String>> onBlocked(SsrfBlockedException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "ssrf_blocked",
                "reason", ex.getMessage()));
    }
}
