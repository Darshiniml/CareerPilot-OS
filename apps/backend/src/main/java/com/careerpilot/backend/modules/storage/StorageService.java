package com.careerpilot.backend.modules.storage;

import java.io.InputStream;

public interface StorageService {
    
    /**
     * Uploads a file to object storage.
     * @return The public or internal URL of the saved object.
     */
    String uploadFile(String fileName, String contentType, long contentLength, InputStream inputStream);

    /**
     * Downloads file contents.
     */
    byte[] downloadFile(String fileUrl);

    /**
     * Deletes a file.
     */
    void deleteFile(String fileUrl);
}
