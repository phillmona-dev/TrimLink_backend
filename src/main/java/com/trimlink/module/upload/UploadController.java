package com.trimlink.module.upload;

import com.trimlink.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * Handles local file uploads (receipts, etc.).
 * Saves files to the local uploads/ directory and returns a URL to access them.
 * For production, replace with S3/Cloudinary integration.
 */
@Slf4j
@RestController
@RequestMapping("/uploads")
public class UploadController {

    @Value("${trimlink.app.base-url:http://localhost:9090/api/v1}")
    private String baseUrl;

    @Value("${trimlink.upload.dir:uploads}")
    private String uploadDir;

    @PostMapping("/receipt")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadReceipt(
            @RequestParam("file") MultipartFile file) throws IOException {

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "Only image files are allowed"));
        }

        // Create upload directory if it doesn't exist
        Path uploadPath = Paths.get(uploadDir, "receipts");
        try {
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                log.info("Created upload directory: {}", uploadPath.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("CRITICAL: Could not create upload directory at {}. Error: {}", uploadPath.toAbsolutePath(), e.getMessage());
            throw e;
        }

        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String ext = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";
        String filename = UUID.randomUUID() + ext;

        // Save file to disk
        Path filePath = uploadPath.resolve(filename);
        try {
            Files.copy(file.getInputStream(), filePath);
            log.info("Receipt uploaded successfully to: {}", filePath.toAbsolutePath());
        } catch (IOException e) {
            log.error("CRITICAL: Failed to save uploaded file to {}. Error: {}", filePath.toAbsolutePath(), e.getMessage());
            throw e;
        }

        // Return URL to access the file
        String fileUrl = baseUrl + "/uploads/receipts/" + filename;
        return ResponseEntity.ok(ApiResponse.ok(Map.of("url", fileUrl)));
    }

    @GetMapping("/receipts/{filename}")
    public ResponseEntity<byte[]> getReceipt(@PathVariable String filename) throws IOException {
        Path filePath = Paths.get(uploadDir, "receipts", filename);
        if (!Files.exists(filePath)) {
            log.warn("Receipt not found: {}", filePath.toAbsolutePath());
            return ResponseEntity.notFound().build();
        }
        byte[] bytes = Files.readAllBytes(filePath);
        String contentType = Files.probeContentType(filePath);
        if (contentType == null) contentType = "image/jpeg";

        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .header("Cache-Control", "public, max-age=31536000")
                .body(bytes);
    }
}
