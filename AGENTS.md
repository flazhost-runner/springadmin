# AGENTS.md — Aturan Pengembangan SpringAdmin

> **Sumber kebenaran tunggal** untuk SpringAdmin (port NodeAdmin ke Spring Boot 3 / Java 21).
> Setiap AI (Claude Code, Cursor, Copilot) dan developer **WAJIB** membaca dan mematuhi dokumen ini sebelum menulis atau mengubah kode apapun.

---

## Alur Request (Request Lifecycle)

```
HTTP Request
  → HiddenHttpMethodFilter          // ubah POST _method=PUT/DELETE → verb nyata
  → SecurityFilterChain             // autentikasi JWT / session
  → RateLimitFilter                 // token-bucket per IP (AppProperties.RateLimit)
  → DispatcherServlet
      → HandlerMapping (routes)
          → Interceptor: AuthInterceptor  → AccessInterceptor (RBAC guard_name)
          → Validator (@Valid / manual)
          → Controller (@Controller / @RestController)
              → Service (interface I*Service, implements via @Service)
                  → Repository (Spring Data JPA)
                  → Entity / DB (Flyway-managed schema)
              ← throws AppException (service layer)
          ← GlobalExceptionHandler (@ControllerAdvice)
              → web: renderView (Thymeleaf)
              → api: ResponseEntity<ApiResponse<T>>
```

**Aturan alur:**
- Controller **tidak boleh** mengandung logika bisnis — hanya parsing request + delegasi ke service + pembentukan response.
- Service **tidak boleh** mengakses `HttpServletRequest`/`HttpServletResponse`.
- Repository **tidak boleh** mengembalikan `Optional` yang langsung di-`.get()` tanpa guard — throw `NotFoundException` di service.

---

## Prinsip Wajib

### 1. SOLID / Dependency Injection (Spring DI)
- Service: `@Service`, **implements `I*Service`** (interface wajib).
- Controller: `@Controller` / `@RestController`, **inject `I*Service` lewat constructor**, bukan field.
- Jangan `new XService()` / `new XController()` di luar context Spring.
- Gunakan constructor injection (bukan `@Autowired` field injection).

```java
// BENAR
@Controller
public class UserController {
    private final IUserService userService;
    public UserController(IUserService userService) {
        this.userService = userService;
    }
}

// SALAH
@Controller
public class UserController {
    @Autowired
    private UserService userService; // field injection — dilarang
}
```

### 2. DRY
Gunakan helper/util yang sudah ada; jangan duplikat:
- `PageUtils.paginate(repo, pageable)` — paginasi seragam.
- `CiLikeSpec.of(field, value)` — case-insensitive LIKE portabel.
- `ResponseBuilder.ok(data)` / `ResponseBuilder.error(msg)` — response API seragam.
- `ThemeConfig.getByName(name)` — lookup tema.
- `AppProperties` — semua config.

### 3. Error Handling
- Service **throw** `AppException` (atau subclass: `NotFoundException`, `ConflictException`, `ValidationException`, `UnauthorizedException`).
- **Dilarang** `return null` sebagai sinyal error, `instanceof Exception` di controller, atau swallow exception.
- `GlobalExceptionHandler` (`@ControllerAdvice`) menangani semua exception secara terpusat.
- Web: error → Thymeleaf error page + flash message. API: error → `ApiResponse { success:false, message, errors }`.

```java
// BENAR
public User findById(Long id) {
    return userRepo.findById(id)
        .orElseThrow(() -> new NotFoundException("User tidak ditemukan: " + id));
}

// SALAH
public User findById(Long id) {
    Optional<User> u = userRepo.findById(id);
    if (u.isEmpty()) return null; // dilarang
    return u.get();
}
```

### 4. Separation of Concerns
| Layer | Tanggung Jawab | Larangan |
|-------|---------------|----------|
| Controller | Parsing HTTP, delegasi, response | Logika bisnis, query DB langsung |
| Service | Logika bisnis, orchestration | Akses `HttpServletRequest/Response` |
| Repository | Query DB (Spring Data / JPQL) | Logika bisnis |
| View (Thymeleaf) | Presentasi | Logika bisnis, query langsung |

### 5. Config via AppProperties
- **Dilarang** `System.getenv()`, `System.getProperty()`, atau `@Value` di dalam package `modules/`.
- Semua config diakses via `AppProperties` yang di-inject:

```java
@Service
public class AuthService implements IAuthService {
    private final AppProperties props;
    public AuthService(AppProperties props) { this.props = props; }

    public String buildToken(User user) {
        long exp = props.getJwt().getExpiration(); // BENAR
        // bukan: long exp = Long.parseLong(System.getenv("JWT_EXPIRATION")); // SALAH
    }
}
```

### 6. DDL via Flyway Saja
- **Dilarang** `spring.jpa.hibernate.ddl-auto=create/update` — selalu `validate` atau `none` di production.
- Semua perubahan skema WAJIB lewat Flyway migration di `src/main/resources/db/migration/`.
- Nama file: `V{versi}__{deskripsi_singkat}.sql` (contoh: `V1__create_users_table.sql`).
- Migration bersifat **immutable** — jangan edit migration yang sudah pernah dijalankan.

---

## Struktur Package Per Fitur

```
src/main/java/com/nodeadmin/
├── SpringAdminApplication.java
├── config/
│   ├── AppProperties.java          // @ConfigurationProperties
│   ├── ThemeConfig.java            // daftar 9 tema
│   ├── SecurityConfig.java         // Spring Security
│   ├── WebMvcConfig.java           // interceptors, static resources
│   └── FlywayConfig.java           // (opsional override)
├── common/
│   ├── exception/
│   │   ├── AppException.java
│   │   ├── NotFoundException.java
│   │   ├── ConflictException.java
│   │   ├── ValidationException.java
│   │   └── UnauthorizedException.java
│   ├── response/
│   │   └── ApiResponse.java        // { success, message, data, errors }
│   ├── util/
│   │   ├── PageUtils.java
│   │   └── CiLikeSpec.java
│   └── handler/
│       └── GlobalExceptionHandler.java
└── modules/
    └── <nama_modul>/               // contoh: access, auth, setting, dashboard
        ├── entity/
        │   └── <Entitas>.java      // @Entity, kolom portabel
        ├── repository/
        │   └── I<Entitas>Repository.java   // extends JpaRepository + JpaSpecificationExecutor
        ├── service/
        │   ├── I<Nama>Service.java         // interface kontrak
        │   └── <Nama>ServiceImpl.java      // @Service, implements I<Nama>Service
        ├── controller/
        │   ├── web/
        │   │   └── <Nama>Controller.java   // @Controller, Thymeleaf
        │   └── api/
        │       └── <Nama>ApiController.java // @RestController, /api/v1/...
        ├── dto/
        │   ├── <Nama>CreateDto.java        // input validasi store
        │   └── <Nama>UpdateDto.java        // input validasi update
        └── validator/                      // (opsional) custom @Constraint
```

---

## Matriks Artefak Modul

**Test WAJIB untuk fitur apa pun.** Setiap modul yang terjangkau via route harus punya minimal 1 test.

### Selalu Ada (modul fungsional)
| Artefak | Catatan |
|---------|---------|
| `I*Service` + `*ServiceImpl` | Semua logika bisnis; ServiceImpl `@Service implements I*Service` |
| Controller (`web/` atau `api/` atau keduanya) | Pintu HTTP |
| DTO (bila ada input tulis) | `@Valid` di controller; anotasi Bean Validation di DTO |
| Test | **WAJIB** — min 1 test/modul; integration bila ada service; MockMvc bila ada controller |
| Update docs | `README.md`; + endpoint ke `docs/API.md` bila ada REST API |

### Kondisional
| Artefak | Wajib JIKA |
|---------|------------|
| `@Entity` | Menyimpan data ke DB |
| Migration Flyway | Ada entity baru atau perubahan skema |
| DTO validasi | Ada input tulis (store/update) |
| View Thymeleaf | Ada UI admin |
| Web controller | Ada view Thymeleaf |
| REST API controller (`/api/v1/`) | Fitur perlu akses dari mobile/integrasi |
| `docs/API.md` entry | Ada REST controller |

> **API adalah opsional** untuk modul baru. Jika membuat modul resource, tawarkan ke user apakah perlu REST API. Jika dibuat, test API (`MockMvc` + `@WebMvcTest`) dan entri `docs/API.md` **wajib ada**.

---

## Named Routes (URL Conventions)

| Pola | Verb | Keterangan |
|------|------|------------|
| `/admin/<modul>` | GET | Index / list |
| `/admin/<modul>/create` | GET | Form tambah |
| `/admin/<modul>` | POST | Store (simpan baru) |
| `/admin/<modul>/{id}` | GET | Show / detail |
| `/admin/<modul>/{id}/edit` | GET | Form edit |
| `/admin/<modul>/{id}` | PUT | Update |
| `/admin/<modul>/{id}` | DELETE | Hapus |
| `/api/v1/<modul>` | GET | API list |
| `/api/v1/<modul>` | POST | API create |
| `/api/v1/<modul>/{id}` | GET | API show |
| `/api/v1/<modul>/{id}` | PUT | API update |
| `/api/v1/<modul>/{id}` | DELETE | API delete |

Browser hanya mengirim GET/POST. `HiddenHttpMethodFilter` (diaktifkan via `spring.mvc.hiddenmethod.filter.enabled=true`) mengkonversi `<input type="hidden" name="_method" value="PUT/DELETE">` di form Thymeleaf menjadi verb HTTP yang benar.

---

## Security Checklist

- Route admin: `AuthInterceptor` (autentikasi) **sebelum** `AccessInterceptor` (otorisasi RBAC) — urutan wajib di `WebMvcConfig`.
- Form mutasi (POST/PUT/DELETE via web): CSRF token Thymeleaf (`th:action`) aktif — jangan disable kecuali endpoint API stateless dengan JWT.
- Endpoint sensitif (login, register, OTP, reset password): pasang `RateLimitFilter` dengan bucket kecil (`AppProperties.RateLimit`).
- Validasi semua input DTO: `@Valid` + Bean Validation annotation — cegah mass-assignment lewat `@JsonIgnoreProperties` atau field whitelist eksplisit.
- Upload file: validasi magic-byte di service, jangan percaya `ContentType` dari client.
- Jangan bocorkan stack trace ke user: `GlobalExceptionHandler` return pesan generik di production (`AppProperties.isProduction()`).
- Secret (JWT, DB password, dsb.) hanya dari environment/`application.properties` yang tidak di-commit — jangan hardcode.
- Password: selalu hash via BCrypt dengan rounds dari `AppProperties.Bcrypt.getRounds()`.

---

## Aturan Skema DB

- **UUID**: kolom UUID disimpan sebagai `VARCHAR(36)` — portabel lintas MySQL, PostgreSQL, SQLite.
- **Timestamp**: gunakan `TIMESTAMP` (bukan `DATETIME` — tidak portabel ke PG).
- **String panjang**: `VARCHAR(n)` atau `TEXT` — hindari `LONGTEXT`/`MEDIUMTEXT` (MySQL-only).
- **Boolean**: `BOOLEAN` / `TINYINT(1)` — hindari tipe vendor-spesifik.
- **Auto-increment PK**: `BIGINT AUTO_INCREMENT` (MySQL) atau `BIGSERIAL` (PG) — gunakan `GenerationType.IDENTITY` di JPA, bukan `SEQUENCE` bila belum pasti dialek.
- **Collation**: jangan hardcode collation di SQL (`utf8mb4_unicode_ci`) — biarkan default DB.
- Semua DDL via **Flyway** — `ddl-auto=validate` di production.
- Migration **immutable** — tidak boleh diedit setelah dijalankan; buat versi baru untuk perubahan.

---

## DO NOT (akan ditolak code review / CI)

- `new XServiceImpl()` / `new XController()` di luar context Spring — pakai constructor injection.
- `return null` sebagai sinyal error dari service — pakai `throw new NotFoundException(...)`.
- `instanceof Exception` di controller untuk routing error manual — serahkan ke `GlobalExceptionHandler`.
- `System.getenv(...)` / `@Value` di dalam package `modules/` — gunakan `AppProperties`.
- `spring.jpa.hibernate.ddl-auto=create` atau `update` di production.
- Mengedit migration Flyway yang sudah pernah dijalankan.
- Kolom tipe `LONGTEXT`, `MEDIUMTEXT`, `DATETIME` di migration (tidak portabel).
- Collation hardcode di SQL migration.
- `@Autowired` field injection — pakai constructor injection.
- Service tanpa interface `I*Service`.
- Modul baru tanpa test.
- Modul dengan REST controller tanpa entri `docs/API.md`.
- Hardcode secret/kredensial di kode atau migration.
- Logika bisnis di controller atau view.
- Query DB langsung di controller (bypass service layer).

---

## Checklist Sebelum Menganggap Selesai (Definition of Done)

- [ ] Mengikuti struktur package dan pola di atas.
- [ ] Constructor injection (bukan field injection).
- [ ] Service implements `I*Service`; tidak ada logika bisnis di controller.
- [ ] Error handling via `AppException` subclass; tidak ada `return null` sebagai error.
- [ ] Config via `AppProperties`; tidak ada `System.getenv()` di `modules/`.
- [ ] DDL via Flyway migration; `ddl-auto=validate` atau `none`.
- [ ] DTO dengan `@Valid` untuk semua input tulis.
- [ ] Security checklist terpenuhi (auth interceptor, CSRF, rate-limit, validasi upload).
- [ ] Test: minimal 1 test per modul; integration test bila ada service kompleks.
- [ ] `./mvnw verify` → **BUILD SUCCESS** (compiles + tests hijau).
- [ ] `README.md` diperbarui.
- [ ] `docs/API.md` diperbarui bila ada REST controller baru.

---

## Perintah Penting

```bash
# Verifikasi lengkap (compile + test) — WAJIB sebelum menganggap selesai
./mvnw verify

# Jalankan development server
./mvnw spring-boot:run

# Compile saja (cek error tipe)
./mvnw compile

# Jalankan test saja
./mvnw test

# Build JAR production
./mvnw package -DskipTests

# Buat migration baru (buat file SQL kosong di db/migration/)
# Konvensi: V{versi}__{deskripsi}.sql
# Contoh: V2__add_roles_table.sql
touch src/main/resources/db/migration/V2__add_roles_table.sql

# Lihat info Flyway (migration status)
./mvnw flyway:info
```

---

## Pola Referensi Termutakhir

Modul `access` (user/role/permission) dan `auth` adalah implementasi paling lengkap dan terbaru.
Ikuti pola mereka untuk modul baru: entity → repository → service interface + impl → DTO → controller → view → test → docs.
