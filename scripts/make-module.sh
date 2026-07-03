#!/usr/bin/env bash
# =============================================================================
# make-module.sh — SpringAdmin module scaffold generator
#
# Usage:
#   ./scripts/make-module.sh ModuleName module_name
#
# Example:
#   ./scripts/make-module.sh Product product
#
# Generated artefacts (mirroring the NodeAdmin module structure):
#   Entity, Repository, IService + Service
#   WebController + ApiController
#   DTOs (Request)
#   Thymeleaf templates: index / create / edit
#   Flyway migration SQL
#   Test class
#
# All files follow the patterns in src/main/java/com/nodeadmin/modules/access/
# =============================================================================

set -euo pipefail

# ── Argument validation ────────────────────────────────────────────────────────
if [[ $# -lt 2 ]]; then
  echo "Usage: $0 ModuleName module_name"
  echo "  ModuleName  — PascalCase,   e.g. Product"
  echo "  module_name — snake_case,   e.g. product"
  exit 1
fi

MODULE_CLASS="$1"          # e.g. Product
MODULE_SNAKE="$2"          # e.g. product
MODULE_LOWER="${MODULE_SNAKE,,}"    # already lower; belt-and-suspenders
MODULE_CAMEL="${MODULE_LOWER}"      # same for simple single-word names
MODULE_PLURAL="${MODULE_LOWER}s"    # naive plural (override manually if needed)

BASE_PKG="com.nodeadmin"
MODULE_PKG="${BASE_PKG}.modules.${MODULE_LOWER}"
SRC="src/main/java/com/nodeadmin/modules/${MODULE_LOWER}"
TEST="src/test/java/com/nodeadmin/modules/${MODULE_LOWER}"
TMPL="src/main/resources/templates/modules/${MODULE_LOWER}"

# Next Flyway version — count existing migrations and +1
NEXT_V=$(find src/main/resources/db/migration -name "V*.sql" 2>/dev/null | wc -l)
NEXT_V=$((NEXT_V + 1))
MIGRATION_FILE="src/main/resources/db/migration/V${NEXT_V}__create_${MODULE_LOWER}_table.sql"

echo "Generating module: ${MODULE_CLASS} (${MODULE_LOWER})"
echo "  Base package  : ${MODULE_PKG}"
echo "  Flyway version: V${NEXT_V}"

# ── Create directories ─────────────────────────────────────────────────────────
mkdir -p \
  "${SRC}/entity" \
  "${SRC}/repository" \
  "${SRC}/service" \
  "${SRC}/controller/web/v1" \
  "${SRC}/controller/api/v1" \
  "${SRC}/dto" \
  "${TEST}" \
  "${TMPL}"

# ═════════════════════════════════════════════════════════════════════════════
# 1. Entity
# ═════════════════════════════════════════════════════════════════════════════
cat > "${SRC}/entity/${MODULE_CLASS}Entity.java" << JAVA
package ${MODULE_PKG}.entity;

import com.nodeadmin.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "${MODULE_PLURAL}")
@Getter
@Setter
@NoArgsConstructor
public class ${MODULE_CLASS}Entity extends BaseEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "code", length = 20, unique = true, nullable = false)
    private String code;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "Active";

    @PrePersist
    protected void assignId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
JAVA

# ═════════════════════════════════════════════════════════════════════════════
# 2. Repository
# ═════════════════════════════════════════════════════════════════════════════
cat > "${SRC}/repository/${MODULE_CLASS}Repository.java" << JAVA
package ${MODULE_PKG}.repository;

import ${MODULE_PKG}.entity.${MODULE_CLASS}Entity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ${MODULE_CLASS}Repository extends JpaRepository<${MODULE_CLASS}Entity, String> {

    Optional<${MODULE_CLASS}Entity> findByCode(String code);

    boolean existsByCode(String code);
}
JAVA

# ═════════════════════════════════════════════════════════════════════════════
# 3. IService interface
# ═════════════════════════════════════════════════════════════════════════════
cat > "${SRC}/service/I${MODULE_CLASS}Service.java" << JAVA
package ${MODULE_PKG}.service;

import ${MODULE_PKG}.dto.${MODULE_CLASS}Request;
import ${MODULE_PKG}.entity.${MODULE_CLASS}Entity;

import java.util.List;
import java.util.Map;

public interface I${MODULE_CLASS}Service {

    Map<String, Object> index(Map<String, String> filters);

    ${MODULE_CLASS}Entity store(${MODULE_CLASS}Request request, String createdBy);

    Map<String, Object> edit(String id);

    ${MODULE_CLASS}Entity update(String id, ${MODULE_CLASS}Request request, String updatedBy);

    void delete(String id);

    void deleteSelected(List<String> ids);
}
JAVA

# ═════════════════════════════════════════════════════════════════════════════
# 4. Service implementation
# ═════════════════════════════════════════════════════════════════════════════
cat > "${SRC}/service/${MODULE_CLASS}Service.java" << JAVA
package ${MODULE_PKG}.service;

import com.nodeadmin.common.error.NotFoundError;
import com.nodeadmin.common.util.PaginateResult;
import ${MODULE_PKG}.dto.${MODULE_CLASS}Request;
import ${MODULE_PKG}.entity.${MODULE_CLASS}Entity;
import ${MODULE_PKG}.repository.${MODULE_CLASS}Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class ${MODULE_CLASS}Service implements I${MODULE_CLASS}Service {

    private final ${MODULE_CLASS}Repository ${MODULE_CAMEL}Repository;

    public ${MODULE_CLASS}Service(${MODULE_CLASS}Repository ${MODULE_CAMEL}Repository) {
        this.${MODULE_CAMEL}Repository = ${MODULE_CAMEL}Repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> index(Map<String, String> filters) {
        int page     = parseInt(filters.getOrDefault("q_page",      "1"));
        int pageSize = parseInt(filters.getOrDefault("q_page_size", "10"));

        List<${MODULE_CLASS}Entity> all = ${MODULE_CAMEL}Repository.findAll();
        long total = all.size();

        // Simple in-memory pagination (replace with JPA Criteria for production use)
        int from = Math.min((page - 1) * pageSize, (int) total);
        int to   = Math.min(from + pageSize, (int) total);
        List<${MODULE_CLASS}Entity> data = all.subList(from, to);

        PaginateResult<${MODULE_CLASS}Entity> paginate = PaginateResult.of(data, total, page, pageSize);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data",       paginate.data());
        result.put("total",      paginate.total());
        result.put("page",       paginate.page());
        result.put("pageSize",   paginate.pageSize());
        result.put("totalPages", paginate.totalPages());
        return result;
    }

    @Override
    public ${MODULE_CLASS}Entity store(${MODULE_CLASS}Request request, String createdBy) {
        ${MODULE_CLASS}Entity entity = new ${MODULE_CLASS}Entity();
        applyRequest(entity, request);
        entity.setCreatedBy(createdBy);
        return ${MODULE_CAMEL}Repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> edit(String id) {
        ${MODULE_CLASS}Entity entity = ${MODULE_CAMEL}Repository.findById(id)
                .orElseThrow(() -> new NotFoundError("${MODULE_CLASS} not found"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", entity);
        return result;
    }

    @Override
    public ${MODULE_CLASS}Entity update(String id, ${MODULE_CLASS}Request request, String updatedBy) {
        ${MODULE_CLASS}Entity entity = ${MODULE_CAMEL}Repository.findById(id)
                .orElseThrow(() -> new NotFoundError("${MODULE_CLASS} not found"));
        applyRequest(entity, request);
        entity.setUpdatedBy(updatedBy);
        return ${MODULE_CAMEL}Repository.save(entity);
    }

    @Override
    public void delete(String id) {
        ${MODULE_CLASS}Entity entity = ${MODULE_CAMEL}Repository.findById(id)
                .orElseThrow(() -> new NotFoundError("${MODULE_CLASS} not found"));
        ${MODULE_CAMEL}Repository.delete(entity);
    }

    @Override
    public void deleteSelected(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        ${MODULE_CAMEL}Repository.deleteAllById(ids);
    }

    private void applyRequest(${MODULE_CLASS}Entity entity, ${MODULE_CLASS}Request req) {
        if (req.getCode()   != null) entity.setCode(req.getCode());
        if (req.getName()   != null) entity.setName(req.getName());
        if (req.getStatus() != null) entity.setStatus(req.getStatus());
    }

    private static int parseInt(String value) {
        try { return Math.max(1, Integer.parseInt(value.trim())); }
        catch (NumberFormatException e) { return 1; }
    }
}
JAVA

# ═════════════════════════════════════════════════════════════════════════════
# 5. DTO — Request
# ═════════════════════════════════════════════════════════════════════════════
cat > "${SRC}/dto/${MODULE_CLASS}Request.java" << JAVA
package ${MODULE_PKG}.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ${MODULE_CLASS}Request {

    @NotBlank(message = "Code is required")
    @Size(max = 20)
    private String code;

    @NotBlank(message = "Name is required")
    @Size(max = 255)
    private String name;

    private String status = "Active";
}
JAVA

# ═════════════════════════════════════════════════════════════════════════════
# 6. Web Controller
# ═════════════════════════════════════════════════════════════════════════════
cat > "${SRC}/controller/web/v1/${MODULE_CLASS}WebController.java" << JAVA
package ${MODULE_PKG}.controller.web.v1;

import com.nodeadmin.common.model.SessionUser;
import com.nodeadmin.common.route.RouteRegistry;
import ${MODULE_PKG}.dto.${MODULE_CLASS}Request;
import ${MODULE_PKG}.service.I${MODULE_CLASS}Service;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/v1/${MODULE_LOWER}")
public class ${MODULE_CLASS}WebController {

    private static final String SESSION_USER_KEY = "currentUser";
    private static final String REDIRECT_INDEX   = "redirect:/admin/v1/${MODULE_LOWER}";

    private final I${MODULE_CLASS}Service ${MODULE_CAMEL}Service;
    private final RouteRegistry          routeRegistry;

    public ${MODULE_CLASS}WebController(I${MODULE_CLASS}Service ${MODULE_CAMEL}Service,
                                        RouteRegistry routeRegistry) {
        this.${MODULE_CAMEL}Service = ${MODULE_CAMEL}Service;
        this.routeRegistry        = routeRegistry;
    }

    @PostConstruct
    public void registerRoutes() {
        routeRegistry.register("admin.v1.${MODULE_LOWER}.index",          "GET",    "/admin/v1/${MODULE_LOWER}");
        routeRegistry.register("admin.v1.${MODULE_LOWER}.create",         "GET",    "/admin/v1/${MODULE_LOWER}/create");
        routeRegistry.register("admin.v1.${MODULE_LOWER}.store",          "POST",   "/admin/v1/${MODULE_LOWER}/store");
        routeRegistry.register("admin.v1.${MODULE_LOWER}.edit",           "GET",    "/admin/v1/${MODULE_LOWER}/{id}/edit");
        routeRegistry.register("admin.v1.${MODULE_LOWER}.update",         "PUT",    "/admin/v1/${MODULE_LOWER}/{id}/update");
        routeRegistry.register("admin.v1.${MODULE_LOWER}.delete",         "DELETE", "/admin/v1/${MODULE_LOWER}/{id}/delete");
        routeRegistry.register("admin.v1.${MODULE_LOWER}.delete_selected","POST",   "/admin/v1/${MODULE_LOWER}/delete_selected");
    }

    @GetMapping
    public String index(@RequestParam Map<String, String> filters, Model model) {
        model.addAllAttributes(${MODULE_CAMEL}Service.index(filters));
        model.addAttribute("filters", filters);
        return "modules/${MODULE_LOWER}/index";
    }

    @GetMapping("/create")
    public String create(Model model) {
        model.addAttribute("${MODULE_CAMEL}Request", new ${MODULE_CLASS}Request());
        return "modules/${MODULE_LOWER}/create";
    }

    @PostMapping("/store")
    public String store(@Valid @ModelAttribute("${MODULE_CAMEL}Request") ${MODULE_CLASS}Request req,
                        BindingResult binding,
                        HttpSession session,
                        RedirectAttributes ra) {
        if (binding.hasErrors()) {
            ra.addFlashAttribute("flashKey", "error");
            ra.addFlashAttribute("flashMessage", binding.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/admin/v1/${MODULE_LOWER}/create";
        }
        SessionUser actor = (SessionUser) session.getAttribute(SESSION_USER_KEY);
        ${MODULE_CAMEL}Service.store(req, actor != null ? actor.getId() : "system");
        ra.addFlashAttribute("flashKey", "success");
        ra.addFlashAttribute("flashMessage", "${MODULE_CLASS} created successfully.");
        return REDIRECT_INDEX;
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable String id, Model model) {
        model.addAllAttributes(${MODULE_CAMEL}Service.edit(id));
        model.addAttribute("${MODULE_CAMEL}Request", new ${MODULE_CLASS}Request());
        return "modules/${MODULE_LOWER}/edit";
    }

    @PutMapping("/{id}/update")
    public String update(@PathVariable String id,
                         @Valid @ModelAttribute("${MODULE_CAMEL}Request") ${MODULE_CLASS}Request req,
                         BindingResult binding,
                         HttpSession session,
                         RedirectAttributes ra) {
        if (binding.hasErrors()) {
            ra.addFlashAttribute("flashKey", "error");
            ra.addFlashAttribute("flashMessage", binding.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/admin/v1/${MODULE_LOWER}/" + id + "/edit";
        }
        SessionUser actor = (SessionUser) session.getAttribute(SESSION_USER_KEY);
        ${MODULE_CAMEL}Service.update(id, req, actor != null ? actor.getId() : "system");
        ra.addFlashAttribute("flashKey", "success");
        ra.addFlashAttribute("flashMessage", "${MODULE_CLASS} updated successfully.");
        return REDIRECT_INDEX;
    }

    @DeleteMapping("/{id}/delete")
    public String delete(@PathVariable String id, RedirectAttributes ra) {
        ${MODULE_CAMEL}Service.delete(id);
        ra.addFlashAttribute("flashKey", "success");
        ra.addFlashAttribute("flashMessage", "${MODULE_CLASS} deleted successfully.");
        return REDIRECT_INDEX;
    }

    @PostMapping("/delete_selected")
    public String deleteSelected(@RequestParam List<String> ids, RedirectAttributes ra) {
        ${MODULE_CAMEL}Service.deleteSelected(ids);
        ra.addFlashAttribute("flashKey", "success");
        ra.addFlashAttribute("flashMessage", "Selected ${MODULE_LOWER}s deleted successfully.");
        return REDIRECT_INDEX;
    }
}
JAVA

# ═════════════════════════════════════════════════════════════════════════════
# 7. API Controller
# ═════════════════════════════════════════════════════════════════════════════
cat > "${SRC}/controller/api/v1/${MODULE_CLASS}ApiController.java" << JAVA
package ${MODULE_PKG}.controller.api.v1;

import com.nodeadmin.common.response.ResponseHandler;
import ${MODULE_PKG}.dto.${MODULE_CLASS}Request;
import ${MODULE_PKG}.entity.${MODULE_CLASS}Entity;
import ${MODULE_PKG}.service.I${MODULE_CLASS}Service;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/${MODULE_LOWER}")
public class ${MODULE_CLASS}ApiController {

    private final I${MODULE_CLASS}Service ${MODULE_CAMEL}Service;

    public ${MODULE_CLASS}ApiController(I${MODULE_CLASS}Service ${MODULE_CAMEL}Service) {
        this.${MODULE_CAMEL}Service = ${MODULE_CAMEL}Service;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> index(@RequestParam Map<String, String> filters) {
        return ResponseHandler.success("Ok", ${MODULE_CAMEL}Service.index(filters));
    }

    @PostMapping("/store")
    public ResponseEntity<Map<String, Object>> store(
            @Valid @ModelAttribute ${MODULE_CLASS}Request req,
            @RequestHeader(value = "X-Actor-Id", required = false, defaultValue = "system") String actorId) {
        ${MODULE_CLASS}Entity entity = ${MODULE_CAMEL}Service.store(req, actorId);
        return ResponseHandler.success("${MODULE_CLASS} created successfully", Map.of("id", entity.getId()));
    }

    @GetMapping("/{id}/edit")
    public ResponseEntity<Map<String, Object>> edit(@PathVariable String id) {
        return ResponseHandler.success("Ok", ${MODULE_CAMEL}Service.edit(id));
    }

    @PutMapping("/{id}/update")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String id,
            @Valid @ModelAttribute ${MODULE_CLASS}Request req,
            @RequestHeader(value = "X-Actor-Id", required = false, defaultValue = "system") String actorId) {
        ${MODULE_CLASS}Entity entity = ${MODULE_CAMEL}Service.update(id, req, actorId);
        return ResponseHandler.success("${MODULE_CLASS} updated successfully", Map.of("id", entity.getId()));
    }

    @DeleteMapping("/{id}/delete")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String id) {
        ${MODULE_CAMEL}Service.delete(id);
        return ResponseHandler.success("${MODULE_CLASS} deleted successfully");
    }

    @PostMapping("/delete_selected")
    public ResponseEntity<Map<String, Object>> deleteSelected(
            @RequestBody Map<String, List<String>> body) {
        ${MODULE_CAMEL}Service.deleteSelected(body.get("ids"));
        return ResponseHandler.success("Selected ${MODULE_LOWER}s deleted successfully");
    }
}
JAVA

# ═════════════════════════════════════════════════════════════════════════════
# 8. Thymeleaf templates
# ═════════════════════════════════════════════════════════════════════════════

# index.html
cat > "${TMPL}/index.html" << HTML
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layouts/be/default/main}">
<head><title>${MODULE_CLASS} List</title></head>
<body>
<section layout:fragment="content">

  <div class="d-flex justify-content-between align-items-center mb-3">
    <h4>${MODULE_CLASS} Management</h4>
    <a th:href="@{/admin/v1/${MODULE_LOWER}/create}" class="btn btn-primary btn-sm">+ New ${MODULE_CLASS}</a>
  </div>

  <!-- Flash message -->
  <div th:if="\${flashKey == 'success'}" class="alert alert-success" th:text="\${flashMessage}"></div>
  <div th:if="\${flashKey == 'error'}"   class="alert alert-danger"  th:text="\${flashMessage}"></div>

  <table class="table table-bordered table-hover table-sm">
    <thead class="table-dark">
      <tr>
        <th>#</th>
        <th>Code</th>
        <th>Name</th>
        <th>Status</th>
        <th>Actions</th>
      </tr>
    </thead>
    <tbody>
      <tr th:each="item, stat : \${data}">
        <td th:text="\${stat.index + 1}"></td>
        <td th:text="\${item.code}"></td>
        <td th:text="\${item.name}"></td>
        <td th:text="\${item.status}"></td>
        <td>
          <a th:href="@{/admin/v1/${MODULE_LOWER}/{id}/edit(id=\${item.id})}" class="btn btn-xs btn-warning">Edit</a>
          <form th:action="@{/admin/v1/${MODULE_LOWER}/{id}/delete(id=\${item.id})}" method="post" style="display:inline">
            <input type="hidden" name="_method" value="DELETE"/>
            <input type="hidden" th:name="\${_csrf.parameterName}" th:value="\${_csrf.token}"/>
            <button type="submit" class="btn btn-xs btn-danger"
                    onclick="return confirm('Delete this ${MODULE_LOWER}?')">Delete</button>
          </form>
        </td>
      </tr>
      <tr th:if="\${#lists.isEmpty(data)}">
        <td colspan="5" class="text-center text-muted">No ${MODULE_PLURAL} found.</td>
      </tr>
    </tbody>
  </table>

  <!-- Pagination -->
  <nav th:if="\${totalPages > 1}">
    <ul class="pagination pagination-sm">
      <li th:each="p : \${#numbers.sequence(1, totalPages)}"
          th:classappend="\${p == page} ? 'active'">
        <a class="page-link" th:href="@{/admin/v1/${MODULE_LOWER}(q_page=\${p})}" th:text="\${p}"></a>
      </li>
    </ul>
  </nav>

</section>
</body>
</html>
HTML

# create.html
cat > "${TMPL}/create.html" << HTML
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layouts/be/default/main}">
<head><title>Create ${MODULE_CLASS}</title></head>
<body>
<section layout:fragment="content">

  <h4>Create ${MODULE_CLASS}</h4>
  <div th:if="\${flashKey == 'error'}" class="alert alert-danger" th:text="\${flashMessage}"></div>

  <form th:action="@{/admin/v1/${MODULE_LOWER}/store}" th:object="\${${MODULE_CAMEL}Request}" method="post">
    <input type="hidden" th:name="\${_csrf.parameterName}" th:value="\${_csrf.token}"/>

    <div class="mb-3">
      <label class="form-label">Code</label>
      <input type="text" class="form-control" th:field="*{code}" required maxlength="20"/>
    </div>
    <div class="mb-3">
      <label class="form-label">Name</label>
      <input type="text" class="form-control" th:field="*{name}" required maxlength="255"/>
    </div>
    <div class="mb-3">
      <label class="form-label">Status</label>
      <select class="form-select" th:field="*{status}">
        <option value="Active">Active</option>
        <option value="Inactive">Inactive</option>
      </select>
    </div>

    <a th:href="@{/admin/v1/${MODULE_LOWER}}" class="btn btn-secondary">Cancel</a>
    <button type="submit" class="btn btn-primary">Save</button>
  </form>

</section>
</body>
</html>
HTML

# edit.html
cat > "${TMPL}/edit.html" << HTML
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layouts/be/default/main}">
<head><title>Edit ${MODULE_CLASS}</title></head>
<body>
<section layout:fragment="content">

  <h4>Edit ${MODULE_CLASS}</h4>
  <div th:if="\${flashKey == 'error'}" class="alert alert-danger" th:text="\${flashMessage}"></div>

  <form th:action="@{/admin/v1/${MODULE_LOWER}/{id}/update(id=\${data.id})}"
        th:object="\${${MODULE_CAMEL}Request}" method="post">
    <input type="hidden" name="_method" value="PUT"/>
    <input type="hidden" th:name="\${_csrf.parameterName}" th:value="\${_csrf.token}"/>

    <div class="mb-3">
      <label class="form-label">Code</label>
      <input type="text" class="form-control" th:field="*{code}"
             th:value="\${data.code}" required maxlength="20"/>
    </div>
    <div class="mb-3">
      <label class="form-label">Name</label>
      <input type="text" class="form-control" th:field="*{name}"
             th:value="\${data.name}" required maxlength="255"/>
    </div>
    <div class="mb-3">
      <label class="form-label">Status</label>
      <select class="form-select" th:field="*{status}">
        <option value="Active"   th:selected="\${data.status == 'Active'}">Active</option>
        <option value="Inactive" th:selected="\${data.status == 'Inactive'}">Inactive</option>
      </select>
    </div>

    <a th:href="@{/admin/v1/${MODULE_LOWER}}" class="btn btn-secondary">Cancel</a>
    <button type="submit" class="btn btn-primary">Update</button>
  </form>

</section>
</body>
</html>
HTML

# ═════════════════════════════════════════════════════════════════════════════
# 9. Flyway migration SQL
# ═════════════════════════════════════════════════════════════════════════════
cat > "${MIGRATION_FILE}" << SQL
-- =============================================================================
-- V${NEXT_V}__create_${MODULE_LOWER}_table.sql
-- Auto-generated by scripts/make-module.sh
-- =============================================================================

CREATE TABLE IF NOT EXISTS ${MODULE_PLURAL} (
    id          VARCHAR(36)  NOT NULL,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'Active',
    created_by  VARCHAR(36)  NULL,
    updated_by  VARCHAR(36)  NULL,
    created_at  DATETIME(6)  NULL,
    updated_at  DATETIME(6)  NULL,
    CONSTRAINT pk_${MODULE_PLURAL} PRIMARY KEY (id),
    CONSTRAINT uq_${MODULE_PLURAL}_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SQL

# ═════════════════════════════════════════════════════════════════════════════
# 10. Test class
# ═════════════════════════════════════════════════════════════════════════════
cat > "${TEST}/${MODULE_CLASS}ServiceTest.java" << JAVA
package com.nodeadmin.modules.${MODULE_LOWER};

import com.nodeadmin.BaseIntegrationTest;
import com.nodeadmin.common.error.AppError;
import ${MODULE_PKG}.dto.${MODULE_CLASS}Request;
import ${MODULE_PKG}.entity.${MODULE_CLASS}Entity;
import ${MODULE_PKG}.service.I${MODULE_CLASS}Service;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ${MODULE_CLASS}ServiceTest extends BaseIntegrationTest {

    @Autowired
    private I${MODULE_CLASS}Service ${MODULE_CAMEL}Service;

    @Test
    void store_shouldPersistEntity() {
        ${MODULE_CLASS}Entity saved = ${MODULE_CAMEL}Service.store(buildRequest("T001", "Test One"), "system");
        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getCode()).isEqualTo("T001");
    }

    @Test
    void update_shouldChangeFields() {
        ${MODULE_CLASS}Entity saved = ${MODULE_CAMEL}Service.store(buildRequest("T002", "Before"), "system");
        ${MODULE_CLASS}Request update = buildRequest("T002", "After");
        ${MODULE_CLASS}Entity updated = ${MODULE_CAMEL}Service.update(saved.getId(), update, "system");
        assertThat(updated.getName()).isEqualTo("After");
    }

    @Test
    void delete_shouldRemoveEntity() {
        ${MODULE_CLASS}Entity saved = ${MODULE_CAMEL}Service.store(buildRequest("T003", "To Delete"), "system");
        ${MODULE_CAMEL}Service.delete(saved.getId());
        // Verify via index
        Map<String, Object> result = ${MODULE_CAMEL}Service.index(Map.of());
        @SuppressWarnings("unchecked")
        List<${MODULE_CLASS}Entity> data = (List<${MODULE_CLASS}Entity>) result.get("data");
        assertThat(data).noneMatch(e -> e.getId().equals(saved.getId()));
    }

    @Test
    void delete_shouldThrowForUnknownId() {
        assertThatThrownBy(() -> ${MODULE_CAMEL}Service.delete("non-existent-id"))
                .isInstanceOf(AppError.class);
    }

    private ${MODULE_CLASS}Request buildRequest(String code, String name) {
        ${MODULE_CLASS}Request req = new ${MODULE_CLASS}Request();
        req.setCode(code);
        req.setName(name);
        req.setStatus("Active");
        return req;
    }
}
JAVA

# ═════════════════════════════════════════════════════════════════════════════
# Done
# ═════════════════════════════════════════════════════════════════════════════
echo ""
echo "Module '${MODULE_CLASS}' scaffolded successfully."
echo ""
echo "Files created:"
echo "  ${SRC}/entity/${MODULE_CLASS}Entity.java"
echo "  ${SRC}/repository/${MODULE_CLASS}Repository.java"
echo "  ${SRC}/service/I${MODULE_CLASS}Service.java"
echo "  ${SRC}/service/${MODULE_CLASS}Service.java"
echo "  ${SRC}/dto/${MODULE_CLASS}Request.java"
echo "  ${SRC}/controller/web/v1/${MODULE_CLASS}WebController.java"
echo "  ${SRC}/controller/api/v1/${MODULE_CLASS}ApiController.java"
echo "  ${TMPL}/index.html"
echo "  ${TMPL}/create.html"
echo "  ${TMPL}/edit.html"
echo "  ${MIGRATION_FILE}"
echo "  ${TEST}/${MODULE_CLASS}ServiceTest.java"
echo ""
echo "Next steps:"
echo "  1. Review and customise the entity columns and migration SQL."
echo "  2. Add @PermitAll / RBAC registration in a SecurityConfig or Permission seed."
echo "  3. Run: ./mvnw test -Dspring.profiles.active=test"
