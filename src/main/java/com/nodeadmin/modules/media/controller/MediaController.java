package com.nodeadmin.modules.media.controller;

import com.nodeadmin.common.response.ResponseHandler;
import com.nodeadmin.modules.media.service.IMediaService;
import com.nodeadmin.modules.media.service.IMediaService.MediaFile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * REST controller for the Trumbowyg file manager.
 *
 * <p>Routes:
 * <ul>
 *   <li>GET  {@code /admin/v1/media/list}   — returns JSON list of uploaded images</li>
 *   <li>POST {@code /admin/v1/media/upload} — multipart/form-data; field name {@code file}</li>
 *   <li>POST {@code /admin/v1/media/delete} — form-urlencoded; field name {@code key}</li>
 * </ul>
 *
 * <p>CSRF: Spring Security's {@code webFilterChain} uses the
 * {@code CsrfTokenRequestAttributeHandler}, which accepts both the standard
 * {@code _csrf} form field AND the {@code X-CSRF-TOKEN} header. The Trumbowyg
 * filemanager plugin reads the token from {@code <meta name="csrf-token">} and
 * sends it as the {@code X-CSRF-TOKEN} request header — this is handled
 * transparently by Spring Security without any extra configuration here.
 */
@RestController
@RequestMapping("/admin/v1/media")
public class MediaController {

    private final IMediaService mediaService;

    public MediaController(IMediaService mediaService) {
        this.mediaService = mediaService;
    }

    // -------------------------------------------------------------------------
    // GET /admin/v1/media/list
    // -------------------------------------------------------------------------

    /**
     * Returns the list of all files in the editor storage directory.
     *
     * @return 200 with {@code { data: [...] }}
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> list() {
        List<MediaFile> files = mediaService.listFiles();
        return ResponseHandler.success("Files fetched", files);
    }

    // -------------------------------------------------------------------------
    // POST /admin/v1/media/upload
    // -------------------------------------------------------------------------

    /**
     * Uploads a new image file.
     *
     * <p>Expects {@code multipart/form-data} with a single field named {@code file}.
     * Tika magic-byte detection runs inside the service — only {@code image/*} allowed.
     *
     * @param file the uploaded file
     * @return 200 with the saved {@link MediaFile} descriptor
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file) {
        MediaFile saved = mediaService.upload(file);
        return ResponseHandler.success("File uploaded", saved);
    }

    // -------------------------------------------------------------------------
    // POST /admin/v1/media/delete
    // -------------------------------------------------------------------------

    /**
     * Deletes a file by its storage key.
     *
     * <p>Expects {@code application/x-www-form-urlencoded} with a single field
     * named {@code key}, e.g. {@code editor/photo.jpg}.
     *
     * @param key storage key — must start with {@code editor/}
     * @return 200 on success
     */
    @PostMapping("/delete")
    public ResponseEntity<Map<String, Object>> delete(
            @RequestParam("key") String key) {
        mediaService.delete(key);
        return ResponseHandler.success("File deleted", null);
    }
}
