package com.torqmind.ops.infrastructure.storage;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

public final class StoragePaths {

    private StoragePaths() {
    }

    /** Ex.: 1-Rede-Demonstracao */
    public static String folderLabel(Long id, String name) {
        return id + "-" + slug(name);
    }

    public static String slug(String name) {
        if (name == null || name.isBlank()) {
            return "sem-nome";
        }
        String n = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("(^-)|(-$)", "");
        if (n.isBlank()) {
            return "sem-nome";
        }
        return n.length() > 60 ? n.substring(0, 60) : n;
    }

    public static String taskKindFolder(String taskType) {
        if ("ROUTINE_RUN".equalsIgnoreCase(taskType)) {
            return "rotinas";
        }
        if ("OCCURRENCE".equalsIgnoreCase(taskType)) {
            return "ocorrencias";
        }
        return taskType == null ? "outros" : taskType.toLowerCase(Locale.ROOT);
    }

    public static String taskFileName(Long taskId, String originalName, String ext) {
        String base = originalName == null || originalName.isBlank() ? "arquivo" : originalName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        base = slug(base);
        String extension = ext == null ? "" : ext;
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return taskId + "-" + unique + "-" + base + extension;
    }
}
