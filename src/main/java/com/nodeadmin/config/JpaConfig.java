package com.nodeadmin.config;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Auto-detects the Hibernate dialect for databases that Spring Boot cannot
 * infer on its own (currently: SQLite).
 *
 * MySQL and PostgreSQL are handled by Spring Boot's auto-configuration; only
 * SQLite needs an explicit hint because it uses a community dialect not
 * included in the core Hibernate distribution.
 *
 * To switch databases: change spring.datasource.url (and optionally
 * spring.jpa.properties.hibernate.dialect) in application.yml.
 */
@Configuration
public class JpaConfig {

    @Bean
    public HibernatePropertiesCustomizer sqliteDialectCustomizer(DataSource dataSource) {
        return hibernateProperties -> {
            String url = resolveUrl(dataSource);
            if (url != null && url.startsWith("jdbc:sqlite")) {
                hibernateProperties.put(
                        AvailableSettings.DIALECT,
                        "org.hibernate.community.dialect.SQLiteDialect"
                );
            }
        };
    }

    private String resolveUrl(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            return conn.getMetaData().getURL();
        } catch (SQLException e) {
            return null;
        }
    }
}
