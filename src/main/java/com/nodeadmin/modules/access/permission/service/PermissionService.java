package com.nodeadmin.modules.access.permission.service;

import com.nodeadmin.common.error.ConflictError;
import com.nodeadmin.common.error.NotFoundError;
import com.nodeadmin.common.route.RouteDefinition;
import com.nodeadmin.common.util.PaginateResult;
import com.nodeadmin.modules.access.permission.dto.PermissionRequest;
import com.nodeadmin.modules.access.permission.entity.PermissionEntity;
import com.nodeadmin.modules.access.permission.repository.PermissionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Concrete implementation of {@link IPermissionService}.
 *
 * <p>Business logic mirrors NodeAdmin's {@code PermissionService}:
 * <ul>
 *   <li>CRUD with duplicate-name guard on store and update (exclude-self).</li>
 *   <li>{@link #syncFromRoutes} iterates all {@link RouteDefinition}s and upserts
 *       by {@code (name, method)} — idempotent, safe to call on every page load.</li>
 * </ul>
 */
@Service
@Transactional
public class PermissionService implements IPermissionService {

    private final PermissionRepository permissionRepository;
    private final EntityManager        em;

    public PermissionService(PermissionRepository permissionRepository,
                             EntityManager em) {
        this.permissionRepository = permissionRepository;
        this.em                   = em;
    }

    // -------------------------------------------------------------------------
    // index
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PaginateResult<PermissionEntity> index(Map<String, String> filters) {
        Map<String, String> f = stripPrefix(filters, "q_");
        int page     = parseInt(f.get("page"), 1);
        int pageSize = parseInt(f.get("page_size"), 10);

        CriteriaBuilder cb = em.getCriteriaBuilder();

        // data query
        CriteriaQuery<PermissionEntity> cq = cb.createQuery(PermissionEntity.class);
        Root<PermissionEntity> root = cq.from(PermissionEntity.class);
        List<Predicate> predicates = buildPredicates(cb, root, f);
        cq.select(root).where(predicates.toArray(new Predicate[0]));

        List<PermissionEntity> data = em.createQuery(cq)
                .setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();

        // count query
        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<PermissionEntity> countRoot = countCq.from(PermissionEntity.class);
        List<Predicate> countPreds = buildPredicates(cb, countRoot, f);
        countCq.select(cb.count(countRoot)).where(countPreds.toArray(new Predicate[0]));
        long total = em.createQuery(countCq).getSingleResult();

        return PaginateResult.of(data, total, page, pageSize);
    }

    // -------------------------------------------------------------------------
    // store
    // -------------------------------------------------------------------------

    @Override
    public PermissionEntity store(PermissionRequest request) {
        if (permissionRepository.existsByNameAndMethod(request.getName(),
                request.getMethod() != null ? request.getMethod() : "")) {
            throw new ConflictError("Permission already exists");
        }
        PermissionEntity perm = new PermissionEntity();
        applyRequest(perm, request);
        return permissionRepository.save(perm);
    }

    // -------------------------------------------------------------------------
    // edit
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PermissionEntity edit(String id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new NotFoundError("Permission not found"));
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Override
    public PermissionEntity update(String id, PermissionRequest request) {
        // Duplicate name check (exclude self)
        permissionRepository.findByNameAndMethod(request.getName(),
                        request.getMethod() != null ? request.getMethod() : "")
                .ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new ConflictError("Permission already exists");
                    }
                });
        PermissionEntity perm = permissionRepository.findById(id)
                .orElseThrow(() -> new NotFoundError("Permission not found"));
        applyRequest(perm, request);
        return permissionRepository.save(perm);
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Override
    public void delete(String id) {
        PermissionEntity perm = permissionRepository.findById(id)
                .orElseThrow(() -> new NotFoundError("Permission not found"));
        permissionRepository.delete(perm);
    }

    // -------------------------------------------------------------------------
    // deleteSelected
    // -------------------------------------------------------------------------

    @Override
    public void deleteSelected(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        List<PermissionEntity> perms = permissionRepository.findAllById(ids);
        permissionRepository.deleteAll(perms);
    }

    // -------------------------------------------------------------------------
    // syncFromRoutes
    // -------------------------------------------------------------------------

    /**
     * Idempotent upsert: for each route in the registry, creates a permission
     * record when none with the same {@code (name, method)} exists.
     * Guard is derived from the route name prefix ({@code "api."} → {@code "api"},
     * everything else → {@code "web"}).
     */
    @Override
    public void syncFromRoutes(List<RouteDefinition> routes) {
        if (routes == null || routes.isEmpty()) return;
        for (RouteDefinition route : routes) {
            String name   = route.name();
            String method = route.method().toUpperCase();
            if (!permissionRepository.existsByNameAndMethod(name, method)) {
                String guard = name.startsWith("api.") ? "api" : "web";
                PermissionEntity perm = new PermissionEntity();
                perm.setName(name);
                perm.setMethod(method);
                perm.setGuardName(guard);
                perm.setStatus("Active");
                permissionRepository.save(perm);
            }
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private void applyRequest(PermissionEntity perm, PermissionRequest req) {
        if (req.getName()        != null) perm.setName(req.getName());
        if (req.getMethod()      != null) perm.setMethod(req.getMethod());
        if (req.getGuardName()   != null) perm.setGuardName(req.getGuardName());
        if (req.getStatus()      != null) perm.setStatus(req.getStatus());
        if (req.getDesc() != null) perm.setDesc(req.getDesc());
    }

    private List<Predicate> buildPredicates(CriteriaBuilder cb,
                                             Root<PermissionEntity> root,
                                             Map<String, String> f) {
        List<Predicate> predicates = new ArrayList<>();
        String name   = f.get("name");
        String method = f.get("method");
        String status = f.get("status");
        String guard  = f.get("guard");
        String desc   = f.get("desc");

        if (name   != null && !name.isBlank())
            predicates.add(cb.like(cb.lower(root.get("name")),        "%" + name.toLowerCase()  + "%"));
        if (method != null && !method.isBlank())
            predicates.add(cb.equal(root.get("method"), method));
        if (status != null && !status.isBlank())
            predicates.add(cb.equal(root.get("status"), status));
        if (guard  != null && !guard.isBlank())
            predicates.add(cb.equal(root.get("guardName"), guard));
        if (desc   != null && !desc.isBlank())
            predicates.add(cb.like(cb.lower(root.get("desc")), "%" + desc.toLowerCase()  + "%"));

        return predicates;
    }

    private static Map<String, String> stripPrefix(Map<String, String> src, String prefix) {
        Map<String, String> out = new LinkedHashMap<>();
        if (src == null) return out;
        src.forEach((k, v) -> {
            if (k != null && k.startsWith(prefix)) out.put(k.substring(prefix.length()), v);
            else if (k != null) out.put(k, v);
        });
        return out;
    }

    private static int parseInt(String value, int defaultVal) {
        if (value == null || value.isBlank()) return defaultVal;
        try { return Math.max(1, Integer.parseInt(value.trim())); }
        catch (NumberFormatException e) { return defaultVal; }
    }
}
