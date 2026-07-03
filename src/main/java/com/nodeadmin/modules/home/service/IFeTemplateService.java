package com.nodeadmin.modules.home.service;

/**
 * Contract for the FE Template file-cache service.
 *
 * <p>Responsible for downloading opentailwind HTML templates from the raw
 * GitHub CDN and persisting them under
 * {@code {storage.root}/fe/templates/{slug}.html} so that the public
 * landing page can be served without hitting GitHub on every request.
 */
public interface IFeTemplateService {

    /**
     * Ensures the template identified by {@code slug} is available on disk.
     * Downloads from raw.githubusercontent.com if not already cached.
     *
     * <p>Slug is validated via the canonical anti-SSRF regex before any
     * network call is made.
     *
     * @param slug template slug, e.g. {@code agency/agency-001-classic}
     */
    void ensure(String slug);

    /**
     * Returns the HTML content of the active template.
     * Reads from the local disk cache; calls {@link #ensure(String)} first
     * if the file does not exist yet.
     *
     * @param slug template slug
     * @return raw HTML string
     */
    String getActiveHtml(String slug);
}
