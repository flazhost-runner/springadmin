package com.nodeadmin.modules.home.service;

import com.nodeadmin.common.error.AppError;
import com.nodeadmin.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Concrete implementation of {@link IFeTemplateService}.
 *
 * <p>Templates are downloaded from raw.githubusercontent.com and stored at
 * {@code {storage.root}/fe/templates/{slug}.html}.  The storage root is
 * resolved absolutely from {@link AppProperties#getStorage()#getRoot()},
 * never from the JVM working directory (Porting Guide — Lesson 9).
 *
 * <p>Slug validation uses the canonical anti-SSRF regex before any
 * network call is made.
 */
@Service
public class FeTemplateService implements IFeTemplateService {

    private static final Logger log = LoggerFactory.getLogger(FeTemplateService.class);

    /** Anti-SSRF: canonical opentailwind slug `<category>-<3digit>-<name>` (flat, no '/'). */
    private static final Pattern SAFE_SEGMENT =
            Pattern.compile("^([a-z]+(?:-[a-z]+)*)-([0-9]{3})-([a-z0-9-]+)$");

    /** Landing HTML hidup di subpath `landings/` (bukan root repo). */
    private static final String RAW_BASE =
            "https://raw.githubusercontent.com/lindoai/opentailwind/master/landings/";

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);

    private final AppProperties appProperties;
    private final HttpClient httpClient;

    public FeTemplateService(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.httpClient    = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();
    }

    // -------------------------------------------------------------------------
    // ensure
    // -------------------------------------------------------------------------

    @Override
    public void ensure(String slug) {
        validateSlug(slug);
        Path target = templatePath(slug);
        if (Files.exists(target)) {
            log.debug("fe-template already cached: {}", slug);
            return;
        }
        String html = download(slug);
        writeToDisk(target, html);
    }

    // -------------------------------------------------------------------------
    // getActiveHtml
    // -------------------------------------------------------------------------

    @Override
    public String getActiveHtml(String slug) {
        validateSlug(slug);
        Path target = templatePath(slug);
        if (!Files.exists(target)) {
            ensure(slug);
        }
        try {
            return Files.readString(target, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read cached template {}: {}", slug, e.getMessage());
            throw new AppError(500, "TEMPLATE_READ_ERROR",
                    "Failed to read cached template: " + slug);
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Validates slug against the canonical flat pattern (anti-SSRF/path-traversal).
     * Slug TIDAK boleh mengandung '/' — subpath `landings/` sudah di {@link #RAW_BASE}.
     */
    private void validateSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new AppError(400, "INVALID_SLUG", "Template slug must not be blank");
        }
        if (!SAFE_SEGMENT.matcher(slug).matches()) {
            throw new AppError(400, "INVALID_SLUG",
                    "Template slug contains invalid characters: " + slug);
        }
    }

    /**
     * Resolves the absolute disk path for a cached template.
     * Uses the storage root from {@link AppProperties} — never CWD-relative.
     */
    private Path templatePath(String slug) {
        String storageRoot = appProperties.getStorage().getRoot();
        // Ensure storageRoot is absolute
        Path root = Paths.get(storageRoot).toAbsolutePath();
        return root.resolve("fe/templates/" + slug + ".html");
    }

    /** Downloads the HTML from raw GitHub CDN. */
    private String download(String slug) {
        String url = RAW_BASE + slug + ".html";
        log.info("Downloading fe-template: {}", url);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(HTTP_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 404) {
                throw new AppError(404, "TEMPLATE_NOT_FOUND",
                        "Template not found on GitHub: " + slug);
            }
            if (resp.statusCode() >= 400) {
                throw new AppError(502, "GITHUB_ERROR",
                        "GitHub returned " + resp.statusCode() + " for: " + slug);
            }
            return resp.body();
        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to download fe-template {}: {}", slug, e.getMessage());
            throw new AppError(502, "GITHUB_UNREACHABLE",
                    "Failed to download template from GitHub: " + e.getMessage());
        }
    }

    /** Writes HTML to disk, creating parent directories as needed. */
    private void writeToDisk(Path target, String html) {
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, html, StandardCharsets.UTF_8);
            log.info("fe-template cached to: {}", target);
        } catch (IOException e) {
            log.error("Failed to write template to {}: {}", target, e.getMessage());
            throw new AppError(500, "TEMPLATE_WRITE_ERROR",
                    "Failed to cache template to disk: " + e.getMessage());
        }
    }
}
