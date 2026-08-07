package com.torqmind.ops.infrastructure.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class LocalStorageProvider implements StorageProvider {

    private final Path root;

    public LocalStorageProvider(@Value("${app.storage.local-root:/app/storage}") String rootPath) {
        this.root = Path.of(rootPath).toAbsolutePath().normalize();
    }

    @Override
    public String providerName() {
        return "local";
    }

    @Override
    public String saveBytes(String folder, String fileName, byte[] content) {
        try {
            Path dir = root.resolve(folder).normalize();
            if (!dir.startsWith(root)) {
                throw new IllegalArgumentException("Caminho de destino inválido.");
            }
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName).normalize();
            if (!target.startsWith(root)) {
                throw new IllegalArgumentException("Nome de arquivo inválido.");
            }
            Files.write(target, content);
            return root.relativize(target).toString();
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao salvar arquivo local.", ex);
        }
    }

    @Override
    public byte[] read(String path) {
        try {
            Path target = root.resolve(path).normalize();
            if (!target.startsWith(root)) {
                throw new IllegalArgumentException("Caminho de arquivo inválido.");
            }
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao ler arquivo local.", ex);
        }
    }

    @Override
    public void delete(String path) {
        try {
            Path target = root.resolve(path).normalize();
            if (!target.startsWith(root)) {
                throw new IllegalArgumentException("Caminho de arquivo inválido.");
            }
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao remover arquivo local.", ex);
        }
    }
}
