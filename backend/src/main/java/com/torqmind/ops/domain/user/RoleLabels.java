package com.torqmind.ops.domain.user;

import java.util.Locale;
import java.util.Map;

/** Rótulos em português básico para exibição (códigos internos permanecem em inglês). */
public final class RoleLabels {

    private static final Map<String, String> LABELS = Map.of(
            "MASTER", "Administrador",
            "OWNER", "Dono da empresa",
            "MANAGER", "Gerente",
            "OPERATOR", "Funcionário"
    );

    private RoleLabels() {
    }

    public static String pt(String role) {
        if (role == null || role.isBlank()) {
            return "Usuário";
        }
        return LABELS.getOrDefault(role.trim().toUpperCase(Locale.ROOT), role);
    }
}
