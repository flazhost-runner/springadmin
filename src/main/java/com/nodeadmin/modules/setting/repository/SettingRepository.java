package com.nodeadmin.modules.setting.repository;

import com.nodeadmin.modules.setting.entity.SettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link SettingEntity}.
 *
 * <p>The {@code settings} table holds exactly one row. {@link #findFirst()}
 * wraps the standard {@link #findAll()} stream to provide a zero-boilerplate
 * accessor from service layer, mirroring NodeAdmin's
 * {@code SettingService.getSetting()} pattern.
 */
@Repository
public interface SettingRepository extends JpaRepository<SettingEntity, String> {

    /**
     * Returns the first (and normally only) settings row.
     *
     * <p>This default method is intentionally kept simple — no JPQL or native
     * query — so it works against any JPA dialect without extra configuration.
     *
     * @return an {@link Optional} containing the settings row, or empty if the
     *         table has not been seeded yet
     */
    default Optional<SettingEntity> findFirst() {
        return findAll().stream().findFirst();
    }
}
