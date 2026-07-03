package com.nodeadmin.modules.access.user.repository;

import com.nodeadmin.modules.access.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link UserEntity}.
 *
 * <p>Derived query methods follow the same lookup patterns used by NodeAdmin's
 * TypeORM UserRepository (findByEmail, findByCode) so that service-layer logic
 * can be ported with minimal friction.
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> {

    /**
     * Finds a user by their email address (case-sensitive, exact match).
     *
     * @param email the email address to search for
     * @return an {@link Optional} containing the user, or empty if not found
     */
    Optional<UserEntity> findByEmail(String email);

    /**
     * Finds a user by their unique code.
     *
     * @param code the user code to search for
     * @return an {@link Optional} containing the user, or empty if not found
     */
    Optional<UserEntity> findByCode(String code);

    /**
     * Checks whether a user with the given email exists.
     *
     * @param email the email address to check
     * @return {@code true} if a record with that email exists
     */
    boolean existsByEmail(String email);

    /**
     * Checks whether a user with the given code exists.
     *
     * @param code the user code to check
     * @return {@code true} if a record with that code exists
     */
    boolean existsByCode(String code);
}
