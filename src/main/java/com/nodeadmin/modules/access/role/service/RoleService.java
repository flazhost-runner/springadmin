package com.nodeadmin.modules.access.role.service;

import com.nodeadmin.common.error.AppError;
import com.nodeadmin.common.error.ConflictError;
import com.nodeadmin.common.error.NotFoundError;
import com.nodeadmin.common.util.PaginateResult;
import com.nodeadmin.modules.access.permission.entity.PermissionEntity;
import com.nodeadmin.modules.access.permission.repository.PermissionRepository;
import com.nodeadmin.modules.access.role.dto.RoleRequest;
import com.nodeadmin.modules.access.role.entity.RoleEntity;
import com.nodeadmin.modules.access.role.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Concrete implementation of {@link IRoleService}.
 *
 * <p>Business logic mirrors NodeAdmin's {@code RoleService}:
 * <ul>
 *   <li>Duplicate name check on store (conflict) and update (exclude self).</li>
 *   <li>{@link #listPermissions} returns ALL permissions with a computed
 *       {@code assigned} boolean — implemented as a projection query that
 *       left-joins {@code roles_permissions} for the given roleId.</li>
 *   <li>Assign / unassign operations load the role with its permissions
 *       collection and modify the Set in-memory before flushing (mirrors
 *       NodeAdmin's {@code role.permissions.push/filter} pattern).</li>
 * </ul>
 */
@Service
@Transactional
public class RoleService implements IRoleService {

    private final RoleRepository       roleRepository;
    private final PermissionRepository permissionRepository;
    private final EntityManager        em;

    public RoleService(RoleRepository roleRepository,
                       PermissionRepository permissionRepository,
                       EntityManager em) {
        this.roleRepository       = roleRepository;
        this.permissionRepository = permissionRepository;
        this.em                   = em;
    }

    // -------------------------------------------------------------------------
    // index
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PaginateResult<RoleEntity> index(Map<String, String> filters) {
        Map<String, String> f = stripPrefix(filters, "q_");
        int page     = parseInt(f.get("page"), 1);
        int pageSize = parseInt(f.get("page_size"), 10);

        CriteriaBuilder cb = em.getCriteriaBuilder();

        // data query
        CriteriaQuery<RoleEntity> cq = cb.createQuery(RoleEntity.class);
        Root<RoleEntity> root = cq.from(RoleEntity.class);
        List<Predicate> predicates = buildPredicates(cb, root, f);
        cq.select(root).where(predicates.toArray(new Predicate[0]));

        List<RoleEntity> data = em.createQuery(cq)
                .setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();

        // count query
        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<RoleEntity> countRoot = countCq.from(RoleEntity.class);
        List<Predicate> countPreds = buildPredicates(cb, countRoot, f);
        countCq.select(cb.count(countRoot)).where(countPreds.toArray(new Predicate[0]));
        long total = em.createQuery(countCq).getSingleResult();

        return PaginateResult.of(data, total, page, pageSize);
    }

    // -------------------------------------------------------------------------
    // store
    // -------------------------------------------------------------------------

    @Override
    public RoleEntity store(RoleRequest request) {
        if (roleRepository.existsByName(request.getName())) {
            throw new ConflictError("Role already exists");
        }
        RoleEntity role = new RoleEntity();
        applyRequest(role, request);
        return roleRepository.save(role);
    }

    // -------------------------------------------------------------------------
    // edit
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public RoleEntity edit(String id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new NotFoundError("Role not found"));
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Override
    public RoleEntity update(String id, RoleRequest request) {
        // Duplicate name check (exclude self)
        roleRepository.findByName(request.getName()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new ConflictError("Role already exists");
            }
        });
        RoleEntity role = roleRepository.findById(id)
                .orElseThrow(() -> new NotFoundError("Role not found"));
        applyRequest(role, request);
        return roleRepository.save(role);
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Override
    public void delete(String id) {
        RoleEntity role = roleRepository.findById(id)
                .orElseThrow(() -> new NotFoundError("Role not found"));
        roleRepository.delete(role);
    }

    // -------------------------------------------------------------------------
    // deleteSelected
    // -------------------------------------------------------------------------

    @Override
    public void deleteSelected(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        List<RoleEntity> roles = roleRepository.findAllById(ids);
        roleRepository.deleteAll(roles);
    }

    // -------------------------------------------------------------------------
    // listPermissions
    // -------------------------------------------------------------------------

    /**
     * Returns ALL permissions with a computed {@code assigned} flag.
     *
     * <p>Implementation: fetch all permissions, then fetch the assigned permission
     * ids for this role in a single IN-clause query, and annotate each record.
     * The result list contains {@code Map<String,Object>} entries with keys:
     * {@code id}, {@code name}, {@code method}, {@code guardName} (permission's guard),
     * {@code status}, {@code description}, {@code assigned}.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> listPermissions(String roleId, Map<String, String> filters) {
        RoleEntity role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundError("Role not found"));
        // Eagerly init permissions to build the assigned set
        Set<PermissionEntity> assigned = role.getPermissions();
        Set<String> assignedIds = new HashSet<>();
        for (PermissionEntity p : assigned) {
            assignedIds.add(p.getId());
        }

        Map<String, String> f = stripPrefix(filters, "q_");
        int page     = parseInt(f.get("page"), 1);
        int pageSize = parseInt(f.get("page_size"), 10);

        CriteriaBuilder cb = em.getCriteriaBuilder();

        // data query
        CriteriaQuery<PermissionEntity> cq = cb.createQuery(PermissionEntity.class);
        Root<PermissionEntity> root = cq.from(PermissionEntity.class);
        List<Predicate> predicates = buildPermPredicates(cb, cq, root, f, roleId);
        cq.select(root).where(predicates.toArray(new Predicate[0]));

        List<PermissionEntity> perms = em.createQuery(cq)
                .setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();

        // count query
        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<PermissionEntity> countRoot = countCq.from(PermissionEntity.class);
        List<Predicate> countPreds = buildPermPredicates(cb, countCq, countRoot, f, roleId);
        countCq.select(cb.count(countRoot)).where(countPreds.toArray(new Predicate[0]));
        long total = em.createQuery(countCq).getSingleResult();

        // Annotate with assigned flag
        List<Map<String, Object>> annotated = new ArrayList<>();
        for (PermissionEntity p : perms) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id",          p.getId());
            entry.put("name",        p.getName());
            entry.put("method",      p.getMethod());
            entry.put("guardName",   p.getGuardName());
            entry.put("status",      p.getStatus());
            entry.put("description", p.getDesc());
            entry.put("assigned",    assignedIds.contains(p.getId()));
            annotated.add(entry);
        }

        PaginateResult<?> paginate = PaginateResult.of(annotated, total, page, pageSize);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data",       paginate.data());
        result.put("total",      paginate.total());
        result.put("page",       paginate.page());
        result.put("pageSize",   paginate.pageSize());
        result.put("totalPages", paginate.totalPages());
        result.put("role",       role);
        return result;
    }

    // -------------------------------------------------------------------------
    // assignPermission
    // -------------------------------------------------------------------------

    @Override
    public void assignPermission(String roleId, String permId) {
        RoleEntity role = loadRoleWithPermissions(roleId);
        PermissionEntity perm = permissionRepository.findById(permId)
                .orElseThrow(() -> new NotFoundError("Permission not found"));
        role.getPermissions().add(perm);
        roleRepository.save(role);
    }

    // -------------------------------------------------------------------------
    // unassignPermission
    // -------------------------------------------------------------------------

    @Override
    public void unassignPermission(String roleId, String permId) {
        RoleEntity role = loadRoleWithPermissions(roleId);
        role.getPermissions().removeIf(p -> p.getId().equals(permId));
        roleRepository.save(role);
    }

    // -------------------------------------------------------------------------
    // assignSelected
    // -------------------------------------------------------------------------

    @Override
    public void assignSelected(String roleId, List<String> permIds) {
        if (permIds == null || permIds.isEmpty()) return;
        RoleEntity role = loadRoleWithPermissions(roleId);
        Set<String> existingIds = new HashSet<>();
        for (PermissionEntity p : role.getPermissions()) {
            existingIds.add(p.getId());
        }
        List<PermissionEntity> found = permissionRepository.findAllById(permIds);
        for (PermissionEntity p : found) {
            if (!existingIds.contains(p.getId())) {
                role.getPermissions().add(p);
            }
        }
        roleRepository.save(role);
    }

    // -------------------------------------------------------------------------
    // unassignSelected
    // -------------------------------------------------------------------------

    @Override
    public void unassignSelected(String roleId, List<String> permIds) {
        if (permIds == null || permIds.isEmpty()) return;
        RoleEntity role = loadRoleWithPermissions(roleId);
        Set<String> removeSet = new HashSet<>(permIds);
        role.getPermissions().removeIf(p -> removeSet.contains(p.getId()));
        roleRepository.save(role);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private RoleEntity loadRoleWithPermissions(String roleId) {
        RoleEntity role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundError("Role not found"));
        // Force-init lazy collection
        role.getPermissions().size();
        return role;
    }

    private void applyRequest(RoleEntity role, RoleRequest req) {
        if (req.getName()        != null) role.setName(req.getName());
        if (req.getStatus()      != null) role.setStatus(req.getStatus());
        if (req.getDesc() != null) role.setDesc(req.getDesc());
    }

    private List<Predicate> buildPredicates(CriteriaBuilder cb,
                                             Root<RoleEntity> root,
                                             Map<String, String> f) {
        List<Predicate> predicates = new ArrayList<>();
        String name   = f.get("name");
        String desc   = f.get("desc");
        String status = f.get("status");

        if (name   != null && !name.isBlank())
            predicates.add(cb.like(cb.lower(root.get("name")),        "%" + name.toLowerCase()  + "%"));
        if (desc   != null && !desc.isBlank())
            predicates.add(cb.like(cb.lower(root.get("desc")), "%" + desc.toLowerCase()  + "%"));
        if (status != null && !status.isBlank())
            predicates.add(cb.equal(root.get("status"), status));

        return predicates;
    }

    /**
     * Builds predicates for the permissions list query.
     * When {@code status=Active} → only assigned (IN subquery via roles.permissions join);
     * when {@code status=Inactive} → only unassigned (NOT IN same subquery).
     * Mirrors NodeAdmin RoleService.permission() filter semantics.
     *
     * @param outerQuery the owning CriteriaQuery or Subquery — required so subqueries
     *                   are bound to the correct query context (Hibernate constraint).
     */
    private List<Predicate> buildPermPredicates(CriteriaBuilder cb,
                                                 AbstractQuery<?> outerQuery,
                                                 Root<PermissionEntity> root,
                                                 Map<String, String> f,
                                                 String roleId) {
        List<Predicate> predicates = new ArrayList<>();
        String name   = f.get("name");
        String method = f.get("method");
        String status = f.get("status");
        String desc   = f.get("desc");

        if (name   != null && !name.isBlank())
            predicates.add(cb.like(cb.lower(root.get("name")),        "%" + name.toLowerCase()  + "%"));
        if (method != null && !method.isBlank())
            predicates.add(cb.equal(root.get("method"), method));
        if (desc   != null && !desc.isBlank())
            predicates.add(cb.like(cb.lower(root.get("desc")), "%" + desc.toLowerCase()  + "%"));

        if ("Active".equalsIgnoreCase(status)) {
            // Only assigned: id IN (select p.id from roles r join r.permissions p where r.id = roleId)
            Subquery<String> sub = outerQuery.subquery(String.class);
            Root<RoleEntity> roleRoot = sub.from(RoleEntity.class);
            Join<RoleEntity, PermissionEntity> permJoin = roleRoot.join("permissions");
            sub.select(permJoin.get("id"))
               .where(cb.equal(roleRoot.get("id"), roleId));
            predicates.add(root.get("id").in(sub));
        } else if ("Inactive".equalsIgnoreCase(status)) {
            // Only unassigned: id NOT IN (same subquery)
            Subquery<String> sub = outerQuery.subquery(String.class);
            Root<RoleEntity> roleRoot = sub.from(RoleEntity.class);
            Join<RoleEntity, PermissionEntity> permJoin = roleRoot.join("permissions");
            sub.select(permJoin.get("id"))
               .where(cb.equal(roleRoot.get("id"), roleId));
            predicates.add(cb.not(root.get("id").in(sub)));
        }

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
