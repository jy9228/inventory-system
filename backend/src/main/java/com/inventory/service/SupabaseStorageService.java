package com.inventory.service;

import com.inventory.exception.FileStorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class SupabaseStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private static final Map<String, String> MIME_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "gif", "image/gif",
            "webp", "image/webp"
    );

    private final HttpClient httpClient;
    private final String supabaseUrl;
    private final String supabaseKey;
    private final String bucket;

    public SupabaseStorageService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.key}") String supabaseKey,
            @Value("${supabase.storage.bucket}") String bucket) {
        this.supabaseUrl = supabaseUrl;
        this.supabaseKey = supabaseKey;
        this.bucket = bucket;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String storeFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new FileStorageException("Invalid file name");
        }

        String extension = getExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new FileStorageException("File type not allowed. Accepted: " + ALLOWED_EXTENSIONS);
        }

        String filename = UUID.randomUUID() + "." + extension;
        String contentType = MIME_TYPES.getOrDefault(extension, "application/octet-stream");

        try {
            URI uploadUri = URI.create(supabaseUrl + "/storage/v1/object/" + bucket + "/" + filename);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uploadUri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("Content-Type", contentType)
                    .header("x-upsert", "true")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new FileStorageException("Supabase upload failed (HTTP " + response.statusCode() + "): " + response.body());
            }

            String publicUrl = supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + filename;
            log.info("Uploaded to Supabase: {}", filename);
            return publicUrl;

        } catch (FileStorageException e) {
            throw e;
        } catch (Exception e) {
            throw new FileStorageException("Failed to upload file to Supabase", e);
        }
    }

    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;

        String filename = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);

        try {
            URI deleteUri = URI.create(supabaseUrl + "/storage/v1/object/" + bucket);

            String body = "{\"prefixes\":[\"" + filename + "\"]}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(deleteUri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("Content-Type", "application/json")
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("Deleted from Supabase: {}", filename);
            } else {
                log.warn("Supabase delete returned HTTP {}: {}", response.statusCode(), response.body());
            }

        } catch (Exception e) {
            log.warn("Failed to delete file from Supabase: {}", filename, e);
        }
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) return "";
        return filename.substring(dotIndex + 1);
    }
}
