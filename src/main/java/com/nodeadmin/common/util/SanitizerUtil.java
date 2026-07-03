package com.nodeadmin.common.util;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

/**
 * HTML sanitizer utility using the OWASP Java HTML Sanitizer.
 *
 * <p>Mirrors NodeAdmin's server-side sanitization strategy for rich-text fields.
 * The allowed tag/attribute set is intentionally restrictive — only safe
 * formatting elements and links are permitted; all JavaScript event handlers,
 * {@code <script>}, {@code <style>}, and unknown attributes are stripped.
 */
public final class SanitizerUtil {

    private SanitizerUtil() {
        // utility class — no instantiation
    }

    /**
     * Safe-tag policy: block-level + inline formatting + links + images.
     * Built once and reused (PolicyFactory is thread-safe).
     */
    private static final PolicyFactory RICH_TEXT_POLICY = new HtmlPolicyBuilder()
            // block / structural
            .allowElements("p", "br", "ul", "ol", "li",
                           "h1", "h2", "h3", "h4", "h5", "h6")
            // inline emphasis
            .allowElements("strong", "em", "b", "i", "u", "s")
            // links — allow href but strip javascript: schemes
            .allowElements("a")
            .allowAttributes("href").onElements("a")
            .allowStandardUrlProtocols()
            .requireRelNofollowOnLinks()
            // images — allow src/alt/width/height, strip data: and javascript: src
            .allowElements("img")
            .allowAttributes("src", "alt", "width", "height").onElements("img")
            .allowStandardUrlProtocols()
            .toFactory();

    /**
     * Sanitizes an HTML string, removing any tags or attributes not in the
     * safe allow-list.  Returns an empty string when {@code html} is null.
     *
     * @param html raw HTML from user input
     * @return sanitized HTML safe for storage and rendering
     */
    public static String sanitizeRichText(String html) {
        if (html == null) return "";
        return RICH_TEXT_POLICY.sanitize(html);
    }
}
