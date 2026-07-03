package com.nodeadmin.modules.home.service;

import com.nodeadmin.common.util.PaginateResult;

import java.util.List;
import java.util.Map;

/**
 * Contract for the FE Template Catalog service.
 *
 * <p>Loads the list of available opentailwind templates from the GitHub
 * tree API, caches the result in memory (6 h TTL) and optionally on disk,
 * and exposes pagination + HTML-preview helpers.
 */
public interface IFeCatalogService {

    /**
     * A lightweight view of a single catalog entry.
     *
     * @param slug     file-path identifier, e.g. {@code agency/agency-001-classic}
     * @param name     human-readable name derived from the slug
     * @param category top-level folder extracted from the slug
     */
    record CatalogEntry(String slug, String name, String category) {}

    /**
     * Loads the full list of template slugs, using the memory/disk cache when
     * still fresh and falling back to a curated offline list when the GitHub
     * API is unreachable.
     *
     * @return list of all catalog entries (never null, may be empty)
     */
    List<CatalogEntry> loadCatalog();

    /**
     * Returns a paginated + filtered subset of the catalog.
     *
     * @param search   substring filter on name (nullable/blank = no filter)
     * @param category category filter (nullable/blank = all categories)
     * @param page     1-based page number
     * @param pageSize entries per page
     * @param pinSlug  slug to keep on the current page even when filtered out
     *                 (nullable = no pinning)
     * @return paginated result with metadata
     */
    PaginateResult<CatalogEntry> paginate(String search, String category,
                                           int page, int pageSize, String pinSlug);

    /**
     * Fetches the raw HTML for a given slug from the raw GitHub CDN,
     * validates the slug against an anti-SSRF regex, and caches the
     * result locally.
     *
     * @param slug template slug
     * @return raw HTML string
     * @throws com.nodeadmin.common.error.AppError 400 when the slug is invalid,
     *         404 when the template does not exist, 502 when GitHub is unreachable
     */
    String previewHtml(String slug);

    /**
     * Returns all distinct category names present in the catalog, sorted.
     *
     * @return sorted list of category strings
     */
    List<String> categories();
}
