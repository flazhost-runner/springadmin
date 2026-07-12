package com.nodeadmin.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Embedded Tomcat tuning for multipart form handling.
 *
 * <p>Tomcat 10.1.42 (the June 2025 security release bundled by Spring Boot 3.5.1)
 * introduced a new connector default {@code maxPartCount = 10} — the maximum number
 * of parts allowed in a {@code multipart/form-data} request. Every field of such a
 * form counts as one part, not just file inputs. The profile form
 * ({@code POST /admin/v1/profile}) submits {@code _method, code, name, phone, email,
 * timezone, password, passwordConfirmation, status, picture} — already at the limit —
 * so Tomcat rejected it with {@code FileCountLimitExceededException} before the request
 * ever reached the controller.
 *
 * <p>Spring Boot 3.5.1 does not yet expose {@code server.tomcat.max-part-count} as a
 * configuration property (added in a later patch), so the limit is raised programmatically
 * on the connector. The value is generous headroom for wide admin forms; file <em>size</em>
 * stays bounded by {@code spring.servlet.multipart.max-file-size} (2 MB).
 */
@Configuration
public class WebServerConfig {

    /** Max parts per multipart request — well above the widest admin form. */
    private static final int MAX_PART_COUNT = 100;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> multipartPartCountCustomizer() {
        return factory -> factory.addConnectorCustomizers(
                connector -> connector.setMaxPartCount(MAX_PART_COUNT));
    }
}
