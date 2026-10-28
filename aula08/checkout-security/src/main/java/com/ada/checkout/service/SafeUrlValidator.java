package com.ada.checkout.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Defesa contra SSRF (OWASP API7 / A10) para URLs de callback fornecidas pelo cliente.
 *
 * <p>Estratégia de defesa em profundidade:
 * <ol>
 *   <li><b>Esquema:</b> apenas {@code https}.</li>
 *   <li><b>Allowlist de host:</b> se configurada, o host precisa estar nela.</li>
 *   <li><b>Resolução de IP:</b> resolve o host e rejeita se <i>qualquer</i> endereço
 *       resolvido for loopback, link-local (inclui o metadata {@code 169.254.169.254}),
 *       privado/site-local, any-local ou multicast. Isso bloqueia também o
 *       <i>DNS rebinding</i> para alvos internos no momento da validação.</li>
 * </ol>
 *
 * <p>A validação é feita sobre <b>todos</b> os IPs resolvidos — não basta o primeiro —
 * e deve ser reaplicada no momento da conexão (ou usar um resolver pinado) para fechar
 * a janela de TOCTOU do rebinding.
 */
@Component
public class SafeUrlValidator {

    private final Set<String> allowedHosts;

    public SafeUrlValidator(
            @Value("${app.webhook.allowed-hosts:}") List<String> allowedHosts) {
        this.allowedHosts = allowedHosts == null ? Set.of()
                : allowedHosts.stream()
                    .filter(h -> h != null && !h.isBlank())
                    .map(String::toLowerCase)
                    .collect(Collectors.toUnmodifiableSet());
    }

    /** @throws SsrfBlockedException se a URL for inválida ou apontar para alvo proibido. */
    public URI validate(String rawUrl) {
        URI uri;
        try {
            uri = new URI(rawUrl);
        } catch (Exception e) {
            throw new SsrfBlockedException("URL malformada");
        }

        if (uri.getScheme() == null || !uri.getScheme().equalsIgnoreCase("https")) {
            throw new SsrfBlockedException("Apenas https é permitido");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SsrfBlockedException("Host ausente");
        }
        if (!allowedHosts.isEmpty() && !allowedHosts.contains(host.toLowerCase())) {
            throw new SsrfBlockedException("Host fora da allowlist: " + host);
        }

        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new SsrfBlockedException("Host não resolve: " + host);
        }

        for (InetAddress addr : resolved) {
            if (isBlocked(addr)) {
                throw new SsrfBlockedException(
                        "Destino interno/proibido bloqueado: " + addr.getHostAddress());
            }
        }
        return uri;
    }

    private boolean isBlocked(InetAddress addr) {
        return addr.isLoopbackAddress()      // 127.0.0.0/8, ::1
            || addr.isLinkLocalAddress()     // 169.254.0.0/16 (metadata cloud), fe80::/10
            || addr.isSiteLocalAddress()     // 10/8, 172.16/12, 192.168/16
            || addr.isAnyLocalAddress()      // 0.0.0.0, ::
            || addr.isMulticastAddress()
            || isUniqueLocalIpv6(addr);      // fc00::/7
    }

    private boolean isUniqueLocalIpv6(InetAddress addr) {
        byte[] b = addr.getAddress();
        return b.length == 16 && (b[0] & 0xfe) == 0xfc;
    }

    /** Erro de validação de SSRF (mapeado para HTTP 400 no controller). */
    public static class SsrfBlockedException extends RuntimeException {
        public SsrfBlockedException(String message) {
            super(message);
        }
    }
}
