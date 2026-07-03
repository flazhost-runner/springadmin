package com.nodeadmin.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Centralized application configuration bound from application.properties/yml.
 * ALL code MUST access configuration via this class — no direct System.getenv()
 * or @Value in business/module code.
 *
 * Prefix: app.*
 * Example:
 *   app.jwt.expires-in=1h
 *   app.bcrypt.rounds=10
 *   app.storage.driver=oss
 *   app.upload.max-size=2097152
 *   app.rate-limit.auth.capacity=10
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String env = "development";
    private String mode = "standard";
    private int defaultPageSize = 10;

    private Jwt     jwt     = new Jwt();
    private Bcrypt  bcrypt  = new Bcrypt();
    private Otp     otp     = new Otp();
    private Storage storage = new Storage();
    private Upload  upload  = new Upload();
    private RateLimit rateLimit = new RateLimit();

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    public boolean isProduction() {
        return "production".equalsIgnoreCase(env);
    }

    public boolean isFullMode() {
        return "full".equalsIgnoreCase(mode);
    }

    // =========================================================================
    // Nested configuration classes
    // =========================================================================

    @Data
    public static class Jwt {
        private String secret   = "change-me-in-production";
        private String expiresIn = "1h";
        private String algorithm = "HS256";

        /** Parses expiresIn string ('1h', '30m', '7d', or plain seconds) to seconds. */
        public long getExpirationSeconds() {
            if (expiresIn == null || expiresIn.isBlank()) return 3600L;
            String s = expiresIn.trim();
            if (s.endsWith("h")) return Long.parseLong(s.substring(0, s.length() - 1)) * 3600L;
            if (s.endsWith("m")) return Long.parseLong(s.substring(0, s.length() - 1)) * 60L;
            if (s.endsWith("d")) return Long.parseLong(s.substring(0, s.length() - 1)) * 86400L;
            return Long.parseLong(s);
        }
    }

    @Data
    public static class Bcrypt {
        private int rounds = 10;
    }

    @Data
    public static class Otp {
        private int expiryMinutes = 10;
    }

    @Data
    public static class Storage {
        private String root              = "./uploads";
        private String driver            = "local";
        private String accessKeyId       = "";
        private String secretAccessKey   = "";
        private String endpoint          = "";
        private String bucket            = "";
        private String region            = "";
        private boolean ssl              = true;
    }

    @Data
    public static class Upload {
        private List<String> allowedTypes = List.of(
                "image/jpeg", "image/jpg", "image/png", "image/webp"
        );
        private long maxSize = 2_097_152L; // 2 MB
    }

    @Data
    public static class RateLimit {
        private BucketConfig auth = new BucketConfig(10, 10, 900);
        private BucketConfig otp  = new BucketConfig(5,  5,  900);

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class BucketConfig {
            private int capacity;
            private int refillTokens;
            private int refillPeriodSeconds;
        }
    }
}
