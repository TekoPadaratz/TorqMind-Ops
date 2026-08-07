package com.torqmind.ops.infrastructure.storage;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class StorageConfig {

    @Bean
    @Primary
    StorageProvider activeStorageProvider(
            @Value("${app.storage.provider:local}") String provider,
            ObjectProvider<GoogleDriveStorageProvider> gdrive,
            LocalStorageProvider local
    ) {
        if ("gdrive".equalsIgnoreCase(provider)) {
            GoogleDriveStorageProvider drive = gdrive.getIfAvailable();
            if (drive != null && drive.isEnabled()) {
                return drive;
            }
        }
        return local;
    }
}
