package com.torqmind.ops.infrastructure.storage;

public interface StorageProvider {
    String providerName();
    String saveBytes(String folder, String fileName, byte[] content);
    byte[] read(String path);
    void delete(String path);
}
