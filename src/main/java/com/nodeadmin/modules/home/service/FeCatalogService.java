package com.nodeadmin.modules.home.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nodeadmin.common.error.AppError;
import com.nodeadmin.common.util.PaginateResult;
import com.nodeadmin.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Concrete implementation of {@link IFeCatalogService}.
 *
 * <p>Template slugs are sourced from the GitHub Tree API:
 * {@code https://api.github.com/repos/lindoai/opentailwind/git/trees/master?recursive=1}
 *
 * <p>Caching strategy:
 * <ul>
 *   <li><b>Memory</b>: loaded list kept in an {@link AtomicReference} with a 6 h TTL.
 *   <li><b>Disk</b>: serialized to {@code {storage.root}/_catalog.json}; loaded on
 *       startup if memory cache is cold and API is unreachable.
 *   <li><b>Offline fallback</b>: curated list of ~10 slugs returned when both
 *       the API and disk cache are unavailable.
 * </ul>
 *
 * <p>The storage root is resolved absolutely from {@link AppProperties} —
 * never CWD-relative (Porting Guide — Lesson 9).
 */
@Service
public class FeCatalogService implements IFeCatalogService {

    private static final Logger log = LoggerFactory.getLogger(FeCatalogService.class);

    private static final String GITHUB_TREE_API =
            "https://api.github.com/repos/lindoai/opentailwind/git/trees/master?recursive=1";
    private static final String RAW_BASE =
            "https://raw.githubusercontent.com/lindoai/opentailwind/master/";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);
    private static final long MEMORY_TTL_SECONDS = 6 * 3600L; // 6 hours

    /** Anti-SSRF slug validation — mirrors FeTemplateService. */
    static final java.util.regex.Pattern SAFE_SEGMENT =
            java.util.regex.Pattern.compile("^[a-z0-9][a-z0-9-]*$");

    /** Curated offline fallback list (~10 items). */
    private static final List<CatalogEntry> OFFLINE_FALLBACK = List.of(
            new CatalogEntry("agency/agency-001-classic",  "agency-001-classic",  "agency"),
            new CatalogEntry("saas/saas-001-launch",       "saas-001-launch",     "saas"),
            new CatalogEntry("portfolio/portfolio-001-dev","portfolio-001-dev",   "portfolio"),
            new CatalogEntry("blog/blog-001-minimal",      "blog-001-minimal",    "blog"),
            new CatalogEntry("startup/startup-001-bold",   "startup-001-bold",    "startup"),
            new CatalogEntry("landing/landing-001-hero",   "landing-001-hero",    "landing"),
            new CatalogEntry("corporate/corporate-001-pro","corporate-001-pro",   "corporate"),
            new CatalogEntry("ecommerce/ecommerce-001-shop","ecommerce-001-shop", "ecommerce"),
            new CatalogEntry("app/app-001-mobile",         "app-001-mobile",      "app"),
            new CatalogEntry("tech/tech-001-dark",         "tech-001-dark",       "tech")
    );

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private record CacheEntry(List<CatalogEntry> entries, Instant loadedAt) {
        boolean isStale() {
            return Instant.now().isAfter(loadedAt.plusSeconds(MEMORY_TTL_SECONDS));
        }
    }

    private final AtomicReference<CacheEntry> memoryCache = new AtomicReference<>();

    private final AppProperties appProperties;
    private final ObjectMapper  objectMapper;
    private final HttpClient    httpClient;

    public FeCatalogService(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper  = objectMapper;
        this.httpClient    = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();
    }

    // -------------------------------------------------------------------------
    // loadCatalog
    // -------------------------------------------------------------------------

    @Override
    public List<CatalogEntry> loadCatalog() {
        CacheEntry cached = memoryCache.get();
        if (cached != null && !cached.isStale()) {
            return cached.entries();
        }

        // Try GitHub API
        try {
            List<CatalogEntry> entries = fetchFromGitHub();
            CacheEntry fresh = new CacheEntry(entries, Instant.now());
            memoryCache.set(fresh);
            writeDiskCache(entries);
            return entries;
        } catch (Exception e) {
            log.warn("GitHub tree API unavailable ({}), trying disk cache.", e.getMessage());
        }

        // Try disk cache
        List<CatalogEntry> disk = readDiskCache();
        if (!disk.isEmpty()) {
            memoryCache.set(new CacheEntry(disk, Instant.now()));
            return disk;
        }

        // Offline fallback
        log.warn("No catalog available — using offline fallback list.");
        return OFFLINE_FALLBACK;
    }

    // -------------------------------------------------------------------------
    // paginate
    // -------------------------------------------------------------------------

    @Override
    public PaginateResult<CatalogEntry> paginate(String search, String category,
                                                  int page, int pageSize, String pinSlug) {
        List<CatalogEntry> all = loadCatalog();
        List<CatalogEntry> filtered = all.stream()
                .filter(e -> isBlank(search) || e.name().toLowerCase()
                        .contains(search.toLowerCase()))
                .filter(e -> isBlank(category) || e.category().equalsIgnoreCase(category))
                .collect(Collectors.toCollection(ArrayList::new));

        // Pin the selected slug on the first page if not in current result
        if (!isBlank(pinSlug) && page == 1) {
            boolean present = filtered.stream().anyMatch(e -> e.slug().equals(pinSlug));
            if (!present) {
                all.stream().filter(e -> e.slug().equals(pinSlug)).findFirst()
                        .ifPresent(e -> filtered.add(0, e));
            }
        }

        long total = filtered.size();
        int safePageSize = pageSize > 0 ? pageSize : 12;
        int safePage     = page > 0 ? page : 1;
        int from         = (safePage - 1) * safePageSize;
        List<CatalogEntry> page_ = from < filtered.size()
                ? filtered.subList(from, Math.min(from + safePageSize, filtered.size()))
                : Collections.emptyList();

        return PaginateResult.of(page_, total, safePage, safePageSize);
    }

    // -------------------------------------------------------------------------
    // previewHtml
    // -------------------------------------------------------------------------

    @Override
    public String previewHtml(String slug) {
        validateSlug(slug);
        String url = RAW_BASE + slug + ".html";
        log.debug("Fetching preview HTML for slug: {}", slug);
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
                        "Template not found: " + slug);
            }
            if (resp.statusCode() >= 400) {
                throw new AppError(502, "GITHUB_ERROR",
                        "GitHub returned " + resp.statusCode());
            }
            return resp.body();
        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch preview for {}: {}", slug, e.getMessage());
            throw new AppError(502, "GITHUB_UNREACHABLE",
                    "Failed to fetch template preview: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // categories
    // -------------------------------------------------------------------------

    @Override
    public List<String> categories() {
        return loadCatalog().stream()
                .map(CatalogEntry::category)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private List<CatalogEntry> fetchFromGitHub() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_TREE_API))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() >= 400) {
            throw new IOException("GitHub API returned " + resp.statusCode());
        }
        return parseGitHubTree(resp.body());
    }

    /** Categories excluded from the catalog — component/snippet directories, not full pages. */
    private static final java.util.Set<String> EXCLUDED_CATEGORIES =
            java.util.Set.of("blocks");

    private List<CatalogEntry> parseGitHubTree(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode tree = root.path("tree");
        List<CatalogEntry> entries = new ArrayList<>();
        for (JsonNode node : tree) {
            String path = node.path("path").asText("");
            String type = node.path("type").asText("");
            // Only blob .html files in a top-level directory
            if (!"blob".equals(type)) continue;
            if (!path.endsWith(".html")) continue;
            // Strip .html extension to get slug
            String slug = path.substring(0, path.length() - 5);
            // Derive category (first path segment) and name (last segment)
            String category = slug.contains("/")
                    ? slug.substring(0, slug.indexOf('/'))
                    : "";
            String name = slug.contains("/")
                    ? slug.substring(slug.lastIndexOf('/') + 1)
                    : slug;
            // Skip component/snippet directories — only full landing page templates
            if (EXCLUDED_CATEGORIES.contains(category)) continue;
            // Basic anti-garbage filter — name must look like a real slug
            if (!name.isBlank() && !category.isBlank()) {
                entries.add(new CatalogEntry(slug, name, category));
            }
        }
        return entries;
    }

    /** Persists the catalog to disk as JSON. */
    private void writeDiskCache(List<CatalogEntry> entries) {
        Path target = diskCachePath();
        try {
            Files.createDirectories(target.getParent());
            objectMapper.writeValue(target.toFile(), entries);
            log.debug("Catalog disk cache written: {} entries", entries.size());
        } catch (IOException e) {
            log.warn("Failed to write catalog disk cache: {}", e.getMessage());
        }
    }

    /** Reads the catalog from disk cache; returns empty list on any error. */
    private List<CatalogEntry> readDiskCache() {
        Path target = diskCachePath();
        if (!Files.exists(target)) return Collections.emptyList();
        try {
            List<CatalogEntry> entries = objectMapper.readValue(target.toFile(),
                    new TypeReference<List<CatalogEntry>>() {});
            log.info("Loaded catalog from disk cache: {} entries", entries.size());
            return entries;
        } catch (IOException e) {
            log.warn("Failed to read catalog disk cache: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Path diskCachePath() {
        String storageRoot = appProperties.getStorage().getRoot();
        Path root = Paths.get(storageRoot).toAbsolutePath();
        return root.resolve("_catalog.json");
    }

    private void validateSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new AppError(400, "INVALID_SLUG", "Template slug must not be blank");
        }
        if (slug.startsWith("/") || slug.endsWith("/") || slug.contains("..") || slug.contains("\\")) {
            throw new AppError(400, "INVALID_SLUG", "Invalid slug: " + slug);
        }
        for (String segment : slug.split("/", -1)) {
            if (!SAFE_SEGMENT.matcher(segment).matches()) {
                throw new AppError(400, "INVALID_SLUG",
                        "Template slug contains invalid characters: " + slug);
            }
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
