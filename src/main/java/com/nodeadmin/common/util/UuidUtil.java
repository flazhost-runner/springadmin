package com.nodeadmin.common.util;

import java.util.UUID;

/**
 * UUID generation utility.
 *
 * <p>Centralises UUID creation so callers do not depend on {@link UUID} directly,
 * making it easy to swap the generation strategy (e.g. UUID v7) in one place.
 */
public final class UuidUtil {

    private UuidUtil() {
        // utility class — no instantiation
    }

    /**
     * Generates a new random UUID string (version 4).
     *
     * @return lowercase hyphenated UUID string, e.g. {@code "550e8400-e29b-41d4-a716-446655440000"}
     */
    public static String generate() {
        return UUID.randomUUID().toString();
    }
}
