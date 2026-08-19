package com.torqmind.ops.infrastructure.security;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/** Protecao anti-SSRF para URLs de webhook: exige https e recusa destinos internos/privados/reservados. */
public final class SsrfGuard {

    private SsrfGuard() {
    }

    /** Valida a URL; lanca IllegalArgumentException se nao for um destino externo seguro. */
    public static void validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("Informe a URL do webhook.");
        }
        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("URL invalida.");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase("https")) {
            throw new IllegalArgumentException("A URL do webhook precisa usar https.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL sem host valido.");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Nao foi possivel resolver o host da URL.");
        }
        for (InetAddress address : addresses) {
            if (isBlocked(address)) {
                throw new IllegalArgumentException("Destino nao permitido (endereco interno/privado/reservado).");
            }
        }
    }

    public static boolean isSafe(String rawUrl) {
        try {
            validate(rawUrl);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] b = address.getAddress();
        if (b.length == 4) {
            int o0 = b[0] & 0xff;
            int o1 = b[1] & 0xff;
            if (o0 == 100 && o1 >= 64 && o1 <= 127) {
                return true; // CGNAT 100.64.0.0/10
            }
            if (o0 == 192 && o1 == 0 && (b[2] & 0xff) == 0) {
                return true; // 192.0.0.0/24 (IETF protocol assignments)
            }
            if (o0 == 198 && (o1 == 18 || o1 == 19)) {
                return true; // 198.18.0.0/15 (benchmarking)
            }
            return false;
        }
        if (b.length == 16) {
            if ((b[0] & 0xfe) == 0xfc) {
                return true; // fc00::/7 (unique local address)
            }
            return false;
        }
        return true;
    }
}
