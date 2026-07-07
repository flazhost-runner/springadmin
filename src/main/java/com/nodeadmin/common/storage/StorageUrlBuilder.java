package com.nodeadmin.common.storage;

import com.nodeadmin.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * Builds the public render URL for a stored object key, branching on the active
 * storage driver — so switching backends needs only a property change
 * ({@code app.storage.driver} / {@code STORAGE_DRIVER}), never a code or view edit.
 *
 * <p>Mirrors NodeAdmin's {@code fileService._publicUrl()} driver-aware URL builder:
 * <ul>
 *   <li><b>local</b> — {@code /public/storage/<key>}: a stable URL prefix decoupled
 *       from the filesystem path. The {@code /public/storage/**} resource handler
 *       (registered in {@link com.nodeadmin.config.WebMvcConfig} when the driver is
 *       {@code local}) maps this prefix onto the absolute storage root, so an
 *       absolute {@code STORAGE_ROOT} (e.g. {@code /app/uploads} in Docker) still
 *       yields a valid URL.</li>
 *   <li><b>s3</b> — absolute URL: path-style {@code {proto}://{host}/{bucket}/{key}}
 *       when an endpoint is set (MinIO, R2, S3-compatible), else virtual-hosted
 *       {@code {proto}://{bucket}.s3.{region}.amazonaws.com/{key}}.</li>
 *   <li><b>oss</b> — absolute virtual-hosted URL {@code {proto}://{bucket}.{endpoint}/{key}}.</li>
 * </ul>
 *
 * <p>The database always stores the bare object <b>key</b> (e.g. {@code setting/icon.png},
 * {@code editor/photo.jpg}); the render URL is built here at request time.
 */
@Component
public class StorageUrlBuilder {

    /**
     * Stable URL prefix for locally-served objects. Decoupled from the filesystem
     * storage root so an absolute {@code STORAGE_ROOT} still yields a valid URL.
     */
    public static final String LOCAL_URL_PREFIX = "/public/storage";

    private final AppProperties.Storage storage;

    public StorageUrlBuilder(AppProperties appProperties) {
        this.storage = appProperties.getStorage();
    }

    /**
     * Returns the driver-aware public URL for the given storage key.
     *
     * @param key bare object key stored in the DB (e.g. {@code setting/icon.png});
     *            a {@code null}/blank key yields an empty string
     * @return relative {@code /public/storage/<key>} for the local driver, or an
     *         absolute cloud URL for the {@code s3} / {@code oss} drivers
     */
    public String url(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String normalizedKey = key.startsWith("/") ? key.substring(1) : key;
        String driver = storage.getDriver();

        if (driver == null || driver.isBlank() || "local".equalsIgnoreCase(driver)) {
            return LOCAL_URL_PREFIX + "/" + normalizedKey;
        }

        String proto    = storage.isSsl() ? "https" : "http";
        String bucket   = storage.getBucket();
        String endpoint = storage.getEndpoint();
        String region   = storage.getRegion();

        if ("s3".equalsIgnoreCase(driver)) {
            if (endpoint != null && !endpoint.isBlank()) {
                // Path-style: MinIO, Cloudflare R2, S3-compatible custom endpoints
                String host = endpoint.replaceFirst("^https?://", "");
                return proto + "://" + host + "/" + bucket + "/" + normalizedKey;
            }
            // Virtual-hosted: AWS S3
            String reg = (region == null || region.isBlank()) ? "us-east-1" : region;
            return proto + "://" + bucket + ".s3." + reg + ".amazonaws.com/" + normalizedKey;
        }

        // oss (Alibaba Cloud OSS) — virtual-hosted: bucket.endpoint/key
        return proto + "://" + bucket + "." + endpoint + "/" + normalizedKey;
    }
}
