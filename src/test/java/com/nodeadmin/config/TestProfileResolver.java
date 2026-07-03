package com.nodeadmin.config;

import org.springframework.test.context.ActiveProfilesResolver;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Resolves active test profiles so CI can layer database-specific profiles
 * on top of the base "test" profile.
 *
 * <p>The base profile is always {@code test} (SQLite in-memory, Redis excluded,
 * in-memory JWT blacklist via {@link TestJwtConfig}). If the JVM system property
 * {@code spring.profiles.active} contains additional profiles (e.g.
 * {@code mysql-test} in the CI MySQL matrix job), they are appended <em>after</em>
 * {@code test} so their properties override the base profile.
 *
 * <p>Rationale: a hard-coded {@code @ActiveProfiles("test")} silently ignores
 * {@code -Dspring.profiles.active=mysql-test}, while datasource system properties
 * still override the URL — producing a broken mix of SQLite driver + MySQL URL.
 */
public class TestProfileResolver implements ActiveProfilesResolver {

    @Override
    public String[] resolve(Class<?> testClass) {
        Set<String> profiles = new LinkedHashSet<>();
        profiles.add("test");
        String requested = System.getProperty("spring.profiles.active", "");
        for (String profile : requested.split(",")) {
            if (!profile.isBlank()) {
                profiles.add(profile.trim());
            }
        }
        return profiles.toArray(String[]::new);
    }
}
