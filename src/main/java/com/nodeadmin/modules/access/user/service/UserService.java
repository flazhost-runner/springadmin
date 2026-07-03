package com.nodeadmin.modules.access.user.service;

import com.nodeadmin.common.error.AppError;
import com.nodeadmin.common.error.NotFoundError;
import com.nodeadmin.common.util.PaginateResult;
import com.nodeadmin.config.AppProperties;
import com.nodeadmin.modules.access.role.entity.RoleEntity;
import com.nodeadmin.modules.access.role.repository.RoleRepository;
import com.nodeadmin.modules.access.user.dto.UserRequest;
import com.nodeadmin.modules.access.user.entity.UserEntity;
import com.nodeadmin.modules.access.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Concrete implementation of {@link IUserService}.
 *
 * <p>Business logic mirrors NodeAdmin's {@code UserService}:
 * <ul>
 *   <li>Password hashed with BCrypt before INSERT; re-hashed on UPDATE only when
 *       a non-blank new password is supplied.</li>
 *   <li>Profile picture saved to {@code {storage.root}/user/{id}.{ext}}.</li>
 *   <li>Pagination driven by JPA Criteria API with dynamic predicates — mirrors
 *       NodeAdmin's {@code createQueryBuilder + paginate()} pattern.</li>
 *   <li>{@code q_role} filter joins {@code users_roles} / {@code roles}.</li>
 * </ul>
 */
@Service
@Transactional
public class UserService implements IUserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BCryptPasswordEncoder bcrypt;
    private final AppProperties appProperties;
    private final EntityManager em;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       AppProperties appProperties,
                       EntityManager em) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.appProperties  = appProperties;
        this.bcrypt         = new BCryptPasswordEncoder(appProperties.getBcrypt().getRounds());
        this.em             = em;
    }

    // -------------------------------------------------------------------------
    // index
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> index(Map<String, String> filters) {
        // Strip q_ prefix
        Map<String, String> f = stripPrefix(filters, "q_");

        int page     = parseInt(f.get("page"), 1);
        int pageSize = parseInt(f.get("page_size"), 10);

        CriteriaBuilder cb = em.getCriteriaBuilder();

        // --- data query ---
        CriteriaQuery<UserEntity> cq = cb.createQuery(UserEntity.class);
        Root<UserEntity> root = cq.from(UserEntity.class);
        root.fetch("roles", JoinType.LEFT);

        List<Predicate> predicates = buildPredicates(cb, root, f);
        cq.select(root).distinct(true).where(predicates.toArray(new Predicate[0]));

        TypedQuery<UserEntity> dataQuery = em.createQuery(cq);
        dataQuery.setFirstResult((page - 1) * pageSize);
        dataQuery.setMaxResults(pageSize);
        List<UserEntity> data = dataQuery.getResultList();

        // --- count query ---
        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<UserEntity> countRoot = countCq.from(UserEntity.class);
        // Need role join for count filter as well
        if (f.containsKey("role") && !f.get("role").isBlank()) {
            countRoot.join("roles", JoinType.LEFT);
        }
        List<Predicate> countPredicates = buildPredicates(cb, countRoot, f);
        countCq.select(cb.countDistinct(countRoot)).where(countPredicates.toArray(new Predicate[0]));
        long total = em.createQuery(countCq).getSingleResult();

        PaginateResult<UserEntity> paginate = PaginateResult.of(data, total, page, pageSize);

        // Also supply all roles for dropdowns (mirrors NodeAdmin index returning roles)
        List<RoleEntity> roles = roleRepository.findAll();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data",       paginate.data());
        result.put("total",      paginate.total());
        result.put("page",       paginate.page());
        result.put("pageSize",   paginate.pageSize());
        result.put("totalPages", paginate.totalPages());
        result.put("roles",      roles);
        return result;
    }

    // -------------------------------------------------------------------------
    // store
    // -------------------------------------------------------------------------

    @Override
    public UserEntity store(UserRequest request, String createdBy, MultipartFile[] files) {
        UserEntity user = new UserEntity();
        applyRequest(user, request);

        // Hash password (required on create)
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new AppError(422, "VALIDATION", "Password is required");
        }
        user.setPassword(bcrypt.encode(request.getPassword()));

        // Roles
        if (request.getRoles() == null || request.getRoles().isEmpty()) {
            throw new AppError(422, "VALIDATION", "At least one role is required");
        }
        List<RoleEntity> roles = roleRepository.findAllById(request.getRoles());
        if (roles.isEmpty()) {
            throw new NotFoundError("Roles not found");
        }
        user.setRoles(new HashSet<>(roles));

        // Persist first to get the generated id for file naming
        UserEntity saved = userRepository.save(user);

        // Picture upload (after save so id is available)
        if (files != null && files.length > 0) {
            String picturePath = savePicture(saved.getId(), files[0]);
            if (picturePath != null) {
                saved.setPicture(picturePath);
                saved = userRepository.save(saved);
            }
        }

        return saved;
    }

    // -------------------------------------------------------------------------
    // edit
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> edit(String id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundError("User not found"));
        // Eagerly initialize roles for the edit form
        user.getRoles().size();

        List<RoleEntity> roles = roleRepository.findAll();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data",  user);
        result.put("roles", roles);
        return result;
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Override
    public UserEntity update(String id, UserRequest request, String updatedBy, MultipartFile[] files) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundError("User not found"));

        applyRequest(user, request);

        // Re-hash only when a new password is supplied
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(bcrypt.encode(request.getPassword()));
        }

        // Roles
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            List<RoleEntity> roles = roleRepository.findAllById(request.getRoles());
            if (roles.isEmpty()) {
                throw new NotFoundError("Roles not found");
            }
            user.setRoles(new HashSet<>(roles));
        }

        // Picture upload
        if (files != null && files.length > 0) {
            String picturePath = savePicture(id, files[0]);
            if (picturePath != null) {
                user.setPicture(picturePath);
            }
        }

        return userRepository.save(user);
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Override
    public void delete(String id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundError("User not found"));
        deletePicture(user.getPicture());
        userRepository.delete(user);
    }

    // -------------------------------------------------------------------------
    // deleteSelected
    // -------------------------------------------------------------------------

    @Override
    public void deleteSelected(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        List<UserEntity> users = userRepository.findAllById(ids);
        for (UserEntity u : users) {
            deletePicture(u.getPicture());
        }
        userRepository.deleteAll(users);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /** Copies plain (non-password, non-role) fields from DTO to entity. */
    private void applyRequest(UserEntity user, UserRequest req) {
        if (req.getCode()     != null) user.setCode(req.getCode());
        if (req.getName()     != null) user.setName(req.getName());
        if (req.getEmail()    != null) user.setEmail(req.getEmail());
        if (req.getPhone()    != null) user.setPhone(req.getPhone());
        if (req.getStatus()   != null) user.setStatus(req.getStatus());
        if (req.getTimezone() != null) user.setTimezone(req.getTimezone());
    }

    /**
     * Builds JPA Criteria predicates from the cleaned (prefix-stripped) filter map.
     * {@code q_role} filter joins through the {@code roles} association.
     */
    private List<Predicate> buildPredicates(CriteriaBuilder cb,
                                             Root<UserEntity> root,
                                             Map<String, String> f) {
        List<Predicate> predicates = new ArrayList<>();

        String code   = f.get("code");
        String name   = f.get("name");
        String phone  = f.get("phone");
        String email  = f.get("email");
        String status = f.get("status");
        String role   = f.get("role");

        if (code   != null && !code.isBlank())
            predicates.add(cb.like(cb.lower(root.get("code")),   "%" + code.toLowerCase()   + "%"));
        if (name   != null && !name.isBlank())
            predicates.add(cb.like(cb.lower(root.get("name")),   "%" + name.toLowerCase()   + "%"));
        if (phone  != null && !phone.isBlank())
            predicates.add(cb.like(cb.lower(root.get("phone")),  "%" + phone.toLowerCase()  + "%"));
        if (email  != null && !email.isBlank())
            predicates.add(cb.like(cb.lower(root.get("email")),  "%" + email.toLowerCase()  + "%"));
        if (status != null && !status.isBlank())
            predicates.add(cb.equal(root.get("status"), status));

        if (role != null && !role.isBlank()) {
            // Join roles to filter — fetch join already done in data query; use separate join for count
            Join<?, ?> rolesJoin = getOrJoinRoles(root);
            predicates.add(cb.equal(rolesJoin.get("id"), role));
        }

        return predicates;
    }

    /**
     * Finds an existing {@code roles} join on the root, or creates an inner join.
     * This avoids creating a duplicate join when the fetch join is already present.
     */
    @SuppressWarnings("unchecked")
    private Join<?, ?> getOrJoinRoles(Root<UserEntity> root) {
        for (Join<UserEntity, ?> j : root.getJoins()) {
            if ("roles".equals(j.getAttribute().getName())) {
                return j;
            }
        }
        return root.join("roles", JoinType.LEFT);
    }

    /**
     * Saves an uploaded file to {@code {storage.root}/user/{id}.{ext}}.
     *
     * @return the relative path stored in the DB, or {@code null} on failure
     */
    private String savePicture(String userId, MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase()
                : "jpg";
        String relativePath = "user/" + userId + "." + ext;
        Path target = Paths.get(appProperties.getStorage().getRoot(), relativePath);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            return relativePath;
        } catch (IOException e) {
            log.warn("Failed to save user picture for id={}: {}", userId, e.getMessage());
            return null;
        }
    }

    /** Silently deletes a stored picture by relative path. */
    private void deletePicture(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        Path target = Paths.get(appProperties.getStorage().getRoot(), relativePath);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Failed to delete picture at {}: {}", relativePath, e.getMessage());
        }
    }

    /** Strips a prefix from all keys in a map. */
    private static Map<String, String> stripPrefix(Map<String, String> src, String prefix) {
        Map<String, String> out = new LinkedHashMap<>();
        if (src == null) return out;
        src.forEach((k, v) -> {
            if (k != null && k.startsWith(prefix)) {
                out.put(k.substring(prefix.length()), v);
            } else if (k != null) {
                out.put(k, v);
            }
        });
        return out;
    }

    private static int parseInt(String value, int defaultVal) {
        if (value == null || value.isBlank()) return defaultVal;
        try { return Math.max(1, Integer.parseInt(value.trim())); }
        catch (NumberFormatException e) { return defaultVal; }
    }
}
