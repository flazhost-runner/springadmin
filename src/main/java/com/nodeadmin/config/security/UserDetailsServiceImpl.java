package com.nodeadmin.config.security;

import com.nodeadmin.modules.access.user.entity.UserEntity;
import com.nodeadmin.modules.access.user.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Spring Security {@link UserDetailsService} implementation.
 *
 * <p>Loads a {@link UserEntity} by email address and converts it into a
 * {@link UserDetails} object that Spring Security uses for authentication
 * and authorization.
 *
 * <p>Important conventions:
 * <ul>
 *   <li>The {@code username} field of the returned {@link User} is set to the
 *       entity's UUID {@code id} (not the email) so that downstream code can
 *       look up the user by stable primary key after authentication.</li>
 *   <li>Roles are mapped to {@link SimpleGrantedAuthority} with the
 *       {@code ROLE_} prefix required by Spring Security's
 *       {@code hasRole()} expressions.</li>
 *   <li>The roles collection is eagerly fetched inside a transaction to avoid
 *       {@code LazyInitializationException}.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // -------------------------------------------------------------------------
    // UserDetailsService
    // -------------------------------------------------------------------------

    /**
     * Loads a user by email address.
     *
     * @param email the email address submitted during login (used as "username")
     * @return a populated {@link UserDetails}; username is set to the entity id
     * @throws UsernameNotFoundException if no user exists with that email
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No user found with email: " + email));

        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
                .toList();

        return User.builder()
                .username(user.getId())          // stable UUID, not email
                .password(user.getPassword())    // bcrypt hash from DB
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(Boolean.TRUE.equals(user.getBlocked()))
                .credentialsExpired(false)
                .disabled(!"Active".equalsIgnoreCase(user.getStatus()))
                .build();
    }
}
