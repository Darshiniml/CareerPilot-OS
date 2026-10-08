package com.careerpilot.backend.modules.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

/**
 * Filesystem storage for local development without MinIO ({@code storage.type=local}). Objects are
 * stored under {@code storage.local.dir}; URLs have the form {@code local://<key>} and every key is
 * resolved inside the base directory (path traversal is rejected).
 */
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local")
@Slf4j
public class LocalFileStorageService implements StorageService {

    static final String SCHEME = "local://";
    private final Path baseDir;

    public LocalFileStorageService(@Value("${storage.local.dir:${user.home}/.careerpilot/storage}") String dir) {
        this.baseDir = Paths.get(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir);
            log.info("Local file storage at {}", baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create storage directory " + baseDir, e);
        }
    }

    @Override
    public String uploadFile(String fileName, String contentType, long contentLength, InputStream inputStream) {
        Path target = resolve(fileName);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            return SCHEME + fileName;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file", e);
        }
    }

    @Override
    public byte[] downloadFile(String fileUrl) {
        try {
            return Files.readAllBytes(resolve(key(fileUrl)));
        } catch (NoSuchFileException e) {
            throw new IllegalArgumentException("Stored file not found");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read stored file", e);
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        try {
            Files.deleteIfExists(resolve(key(fileUrl)));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete stored file", e);
        }
    }

    @Override
    public StorageStatus checkHealth() {
        return Files.isDirectory(baseDir) && Files.isWritable(baseDir) ? StorageStatus.HEALTHY : StorageStatus.UNAVAILABLE;
    }

    private static String key(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith(SCHEME)) {
            throw new IllegalArgumentException("Not a local storage URL");
        }
        return fileUrl.substring(SCHEME.length());
    }

    private Path resolve(String key) {
        Path p = baseDir.resolve(key).normalize();
        if (!p.startsWith(baseDir)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return p;
    }
}
