package com.nodeadmin.modules.media.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Contract for the Media (file-manager) service.
 *
 * <p>All files are stored under {@code {storage.root}/editor/} and served
 * via {@code /public/storage/editor/}.  Paths are always resolved absolutely
 * from {@link com.nodeadmin.config.AppProperties} — never CWD-relative
 * (Porting Guide — Lesson 9).
 */
public interface IMediaService {

    /**
     * Immutable file descriptor returned by list and upload operations.
     *
     * @param name file name (e.g. {@code photo.jpg})
     * @param url  public URL for use in {@code <img src="...">}
     *             (e.g. {@code /public/storage/editor/photo.jpg})
     * @param key  storage key for delete — always starts with {@code editor/}
     *             (e.g. {@code editor/photo.jpg})
     */
    record MediaFile(String name, String url, String key) {}

    /**
     * Returns a list of all files stored under {@code {storage.root}/editor/}.
     *
     * @return list of {@link MediaFile}; empty list if the directory does not exist
     */
    List<MediaFile> listFiles();

    /**
     * Validates and saves an uploaded file.
     *
     * <ul>
     *   <li>Magic-byte detection via Apache Tika — only {@code image/*} MIME types allowed.</li>
     *   <li>File saved to {@code {storage.root}/editor/{originalFilename}}.</li>
     * </ul>
     *
     * @param file uploaded multipart file
     * @return {@link MediaFile} descriptor for the saved file
     * @throws com.nodeadmin.common.error.AppError 400 if MIME type is not an image
     * @throws com.nodeadmin.common.error.AppError 500 if the file cannot be saved
     */
    MediaFile upload(MultipartFile file);

    /**
     * Deletes the file identified by {@code key}.
     *
     * <p>Anti path-traversal guard: the key must start with {@code editor/}.
     * Any key that does not satisfy this constraint throws immediately.
     *
     * @param key storage key, e.g. {@code editor/photo.jpg}
     * @throws com.nodeadmin.common.error.AppError 400 if the key fails the path-traversal check
     * @throws com.nodeadmin.common.error.AppError 404 if the file does not exist
     */
    void delete(String key);
}
