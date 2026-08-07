package com.torqmind.ops.infrastructure.storage;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.UserCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

/**
 * Google Drive StorageProvider.
 * Conta pessoal (recomendado): OAuth com refresh token em gdrive-oauth-token.json
 * Conta de serviço: só funciona bem em Shared Drive (Workspace), não em Meu Drive.
 */
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "gdrive")
public class GoogleDriveStorageProvider implements StorageProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveStorageProvider.class);
    private static final String FOLDER_MIME = "application/vnd.google-apps.folder";

    private final Drive drive;
    private final String rootFolderId;
    private final boolean enabled;

    public GoogleDriveStorageProvider(
            @Value("${app.storage.gdrive.root-folder-id:}") String rootFolderId,
            @Value("${app.storage.gdrive.oauth-token-file:}") String oauthTokenFile,
            @Value("${app.storage.gdrive.credentials-json:}") String credentialsJson,
            @Value("${app.storage.gdrive.credentials-file:}") String credentialsFile
    ) throws IOException, GeneralSecurityException {
        this.rootFolderId = rootFolderId == null ? "" : rootFolderId.trim();
        GoogleCredentials credentials = loadOauthUser(oauthTokenFile);
        String mode = "oauth";
        if (credentials == null) {
            credentials = loadServiceAccount(credentialsJson, credentialsFile);
            mode = "service_account";
        }
        if (credentials == null || this.rootFolderId.isBlank()) {
            log.warn("Google Drive não configurado (OAuth/token ou root-folder-id ausente). Usando fallback local se disponível.");
            this.drive = null;
            this.enabled = false;
        } else {
            credentials = credentials.createScoped(Collections.singleton(DriveScopes.DRIVE));
            HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);
            this.drive = new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    requestInitializer
            ).setApplicationName("TorqMind Ops").build();
            this.enabled = true;
            log.info("Google Drive storage ativo (modo={}). Pasta raiz={}", mode, this.rootFolderId);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getRootFolderId() {
        return rootFolderId;
    }

    @Override
    public String providerName() {
        return "gdrive";
    }

    @Override
    public String saveBytes(String folder, String fileName, byte[] content) {
        ensureEnabled();
        try {
            String parentId = ensureFolderPath(rootFolderId, folder);
            File meta = new File();
            meta.setName(fileName);
            meta.setParents(Collections.singletonList(parentId));
            ByteArrayContent media = new ByteArrayContent("application/octet-stream", content);
            File created = drive.files().create(meta, media)
                    .setSupportsAllDrives(true)
                    .setFields("id,name")
                    .execute();
            return "gdrive:" + created.getId();
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao enviar arquivo ao Google Drive.", ex);
        }
    }

    @Override
    public byte[] read(String path) {
        ensureEnabled();
        String fileId = stripPrefix(path);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            drive.files().get(fileId).setSupportsAllDrives(true).executeMediaAndDownloadTo(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao ler arquivo do Google Drive.", ex);
        }
    }

    @Override
    public void delete(String path) {
        ensureEnabled();
        String fileId = stripPrefix(path);
        try {
            drive.files().delete(fileId).setSupportsAllDrives(true).execute();
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao remover arquivo do Google Drive.", ex);
        }
    }

    public String ensureChildFolder(String parentId, String name) {
        ensureEnabled();
        try {
            String existing = findChildFolder(parentId, name);
            if (existing != null) {
                return existing;
            }
            File meta = new File();
            meta.setName(name);
            meta.setMimeType(FOLDER_MIME);
            meta.setParents(Collections.singletonList(parentId));
            File created = drive.files().create(meta)
                    .setSupportsAllDrives(true)
                    .setFields("id,name")
                    .execute();
            return created.getId();
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao criar pasta no Google Drive: " + name, ex);
        }
    }

    private String ensureFolderPath(String parentId, String folderPath) throws IOException {
        String current = parentId;
        if (folderPath == null || folderPath.isBlank()) {
            return current;
        }
        for (String part : folderPath.replace('\\', '/').split("/")) {
            if (part == null || part.isBlank() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                throw new IllegalArgumentException("Caminho de pasta inválido.");
            }
            current = ensureChildFolder(current, part);
        }
        return current;
    }

    private String findChildFolder(String parentId, String name) throws IOException {
        String q = "mimeType = '%s' and name = '%s' and '%s' in parents and trashed = false"
                .formatted(FOLDER_MIME, escape(name), parentId);
        FileList list = drive.files().list()
                .setQ(q)
                .setSupportsAllDrives(true)
                .setIncludeItemsFromAllDrives(true)
                .setSpaces("drive")
                .setFields("files(id,name)")
                .setPageSize(5)
                .execute();
        List<File> files = list.getFiles();
        if (files == null || files.isEmpty()) {
            return null;
        }
        return files.get(0).getId();
    }

    private void ensureEnabled() {
        if (!enabled || drive == null) {
            throw new IllegalStateException(
                    "Google Drive não está configurado. Para conta pessoal, conclua o OAuth (scripts/gdrive-oauth-setup.sh).");
        }
    }

    private static String stripPrefix(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Caminho de arquivo inválido.");
        }
        return path.startsWith("gdrive:") ? path.substring("gdrive:".length()) : path;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static GoogleCredentials loadOauthUser(String oauthTokenFile) throws IOException {
        if (oauthTokenFile == null || oauthTokenFile.isBlank()) {
            return null;
        }
        Path path = Path.of(oauthTokenFile);
        if (!Files.exists(path)) {
            log.info("Token OAuth não encontrado em {} — tentando conta de serviço.", oauthTokenFile);
            return null;
        }
        try (InputStream in = Files.newInputStream(path)) {
            return UserCredentials.fromStream(in);
        }
    }

    private static GoogleCredentials loadServiceAccount(String jsonInline, String filePath) throws IOException {
        if (jsonInline != null && !jsonInline.isBlank()) {
            byte[] bytes = jsonInline.trim().getBytes(StandardCharsets.UTF_8);
            try (InputStream in = new ByteArrayInputStream(bytes)) {
                return GoogleCredentials.fromStream(in);
            }
        }
        if (filePath != null && !filePath.isBlank()) {
            Path path = Path.of(filePath);
            if (Files.exists(path)) {
                try (InputStream in = Files.newInputStream(path)) {
                    return GoogleCredentials.fromStream(in);
                }
            }
            log.warn("Arquivo de conta de serviço não encontrado: {}", filePath);
        }
        return null;
    }
}
