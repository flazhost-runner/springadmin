package com.nodeadmin.modules.media.service;

import com.nodeadmin.common.error.AppError;
import com.nodeadmin.config.AppProperties;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Concrete implementation of {@link IMediaService}.
 *
 * <p>Storage layout:
 * <pre>
 *   {storage.root}/editor/          ← all uploaded files live here
 * </pre>
 *
 * <p>Public URL: {@code /public/storage/editor/{filename}}
 * (the {@code /public/storage/**} resource handler maps to
 * {@code {storage.root}/} in {@link com.nodeadmin.config.WebMvcConfig}).
 *
 * <p>Security:
 * <ul>
 *   <li>Magic-byte MIME detection via Apache Tika — the declared Content-Type
 *       header from the client is ignored; only the detected MIME matters.</li>
 *   <li>Anti path-traversal: {@link #delete(String)} rejects any key that does
 *       not start with the literal string {@code "editor/"}.</li>
 *   <li>All paths resolved absolutely from {@link AppProperties#getStorage()#getRoot()}
 *       — never from the JVM working directory (Porting Guide — Lesson 9).</li>
 * </ul>
 */
@Service
public class MediaService implements IMediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    /** Only image/* MIME types are accepted. */
    private static final String ALLOWED_MIME_PREFIX = "image/";

    /** Storage key prefix — also used as the subdirectory name. */
    private static final String EDITOR_DIR = "editor";

    private final AppProperties appProperties;
    private final Tika          tika;

    public MediaService(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.tika          = new Tika();
    }

    // -------------------------------------------------------------------------
    // listFiles
    // -------------------------------------------------------------------------

    @Override
    public List<MediaFile> listFiles() {
        Path dir = editorDirPath();
        if (!Files.exists(dir)) {
            return Collections.emptyList();
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries
                    .filter(Files::isRegularFile)
                    .map(p -> toMediaFile(p.getFileName().toString()))
                    .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.warn("Failed to list editor directory: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // -------------------------------------------------------------------------
    // upload
    // -------------------------------------------------------------------------

    @Override
    public MediaFile upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppError(400, "NO_FILE", "No file provided");
        }

        // Magic-byte MIME detection — do NOT trust the declared Content-Type
        String detectedMime;
        try (InputStream is = file.getInputStream()) {
            detectedMime = tika.detect(is, file.getOriginalFilename());
        } catch (IOException e) {
            log.error("Tika MIME detection failed: {}", e.getMessage());
            throw new AppError(500, "MIME_DETECTION_ERROR", "Failed to detect file type");
        }

        if (!detectedMime.startsWith(ALLOWED_MIME_PREFIX)) {
            throw new AppError(400, "INVALID_FILE_TYPE",
                    "Only image files are allowed. Detected: " + detectedMime);
        }

        // Sanitise filename — strip path separators
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "upload.bin";
        }
        String filename = Paths.get(originalFilename).getFileName().toString();

        Path dir    = editorDirPath();
        Path target = dir.resolve(filename);

        try {
            Files.createDirectories(dir);
            file.transferTo(target);
            log.debug("Media upload saved: {}", target);
        } catch (IOException e) {
            log.error("Failed to save uploaded file to {}: {}", target, e.getMessage());
            throw new AppError(500, "FILE_SAVE_ERROR", "Failed to save uploaded file");
        }

        return toMediaFile(filename);
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Override
    public void delete(String key) {
        // Anti path-traversal guard
        if (key == null || !key.startsWith(EDITOR_DIR + "/")) {
            throw new AppError(400, "INVALID_KEY",
                    "Key must start with '" + EDITOR_DIR + "/' — path traversal rejected");
        }

        // Strip the "editor/" prefix to get just the filename
        String filename = key.substring((EDITOR_DIR + "/").length());

        // Additional guard: filename must not contain path separators
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new AppError(400, "INVALID_KEY", "Key contains illegal path characters");
        }

        Path target = editorDirPath().resolve(filename);

        if (!Files.exists(target)) {
            throw new AppError(404, "FILE_NOT_FOUND", "File not found: " + key);
        }

        try {
            Files.delete(target);
            log.debug("Media file deleted: {}", target);
        } catch (IOException e) {
            log.error("Failed to delete file {}: {}", target, e.getMessage());
            throw new AppError(500, "FILE_DELETE_ERROR", "Failed to delete file: " + key);
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Returns the absolute path to the {@code editor/} directory under storage root.
     * Never uses CWD — always resolves from {@link AppProperties}.
     */
    private Path editorDirPath() {
        String storageRoot = appProperties.getStorage().getRoot();
        return Paths.get(storageRoot).toAbsolutePath().resolve(EDITOR_DIR);
    }

    /**
     * Constructs a {@link MediaFile} from a bare filename.
     *
     * @param filename e.g. {@code photo.jpg}
     * @return MediaFile with name, public URL, and storage key
     */
    private MediaFile toMediaFile(String filename) {
        String key = EDITOR_DIR + "/" + filename;
        String url = "/public/storage/" + key;
        return new MediaFile(filename, url, key);
    }
}
