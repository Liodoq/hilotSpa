# HilotSpa — Day 2 Plan & Reading List
**Friday, 14 August 2026** · read with coffee, we start at 10:30

---

## Part 1 — Where we actually are

**Repo:** commit `05a4029`, working tree clean. 49 Java files, backend only.

| Sprint 0 | Status |
|---|---|
| 0.1 Fix trivial model bugs | ✅ Done |
| 0.2 Forms → many PatientIntake | ✅ Done |
| 0.3 UUID primary keys (36 files) | ✅ Done |
| **0.4 Security fields on `User`** | **← today** |
| 0.5 Core entities (Appointment, Therapist, Room, ServiceProtocol, Contraindication, AuditLog) | ⬜ |
| 0.6 Spring Security + JWT + RBAC | ⬜ |
| 0.7 Flyway, Redis, secrets | 🟡 `.env` done, rest pending |
| 0.8 Angular scaffold | ⬜ |

**Bugs:** 22 of 24 closed. Two open — **B14** (no Spring Security at all) and **B19** (zero tests). B14 *is* today's work.

**Not yet verified:** the build. Everything yesterday was static review — I have no network on the bridge, so I can't compile. First thing at 10:30.

**What exists right now:** 6 entities (User, Demographics, Branch, Forms, PatientIntake, Massage), each with Model + Repository + Service + Impl + Transform + Controller. Clean layering. UUID keys throughout. No auth of any kind — every endpoint is wide open to anyone who can reach port 8080.

---

## Part 2 — What we're doing today

### First: verify (5 min)

```powershell
cd hilotspa-backend\backend
docker compose down -v
.\mvnw clean compile
```

`down -v` is not optional — the old schema has `integer` primary keys and Hibernate cannot convert those to `uuid`.

### Then: Sprint 0.4 — security fields on `User` (~30 min)

One file, four changes:

| Field | Why |
|---|---|
| `passwordHash` | There is currently nowhere to store a password. Process Rule #1 requires "a valid user session before any booking transaction." |
| `enabled` | Deactivate staff without deleting their audit trail. |
| `branch` FK | Process Rule #5: Branch Staff are "technically restricted at the query level to view and modify only data associated with their assigned Branch ID." Impossible without this. Null for customers and admins. |
| `role` → enum | Currently a free-text `String`. Any typo — `"admin"`, `"ADMIN "`, `"Adminstrator"` — silently becomes a role nobody has. An `@Enumerated` enum makes invalid roles unrepresentable. |

### Then: Sprint 0.5 — the missing core entities (rest of the day)

`Appointment`, `Therapist`, `Room`, `ServiceProtocol`, `Contraindication`, `AuditLog`.

This is the biggest single gap between your paper and your code. Right now there is no way to *book* anything — the whole booking system has no table to write to. Six entities carry: FR#2 (conversational booking), Process Rule #4 (resource locking), Process Rule #3 (contraindication filtering), and the admin audit log.

### Today's stretch goal: 0.6 — Spring Security + JWT

Probably tomorrow, but the reading below sets it up so we're not starting cold.

---

## Part 3 — Reading list

Three essential, three optional. Roughly 45 minutes for the essentials.

### 🔴 Essential 1 — Mapping enums in JPA
**[The best way to map an Enum Type with JPA and Hibernate](https://vladmihalcea.com/the-best-way-to-map-an-enum-type-with-jpa-and-hibernate/)** — Vlad Mihalcea

*Directly today's 0.4.* Vlad Mihalcea is the most reliable Hibernate source on the internet; he's a Hibernate committer.

**Focus on:** the difference between `EnumType.ORDINAL` and `EnumType.STRING`, and why `ORDINAL` is dangerous.

**The thing to actually understand:** `ORDINAL` stores the enum's *position* — `CUSTOMER` = 0, `STAFF` = 1, `ADMIN` = 2. If someone later inserts a new role alphabetically in the middle of the enum, every existing row silently changes meaning. Your customers become staff. There is no error, no warning, nothing in the logs. For a role column controlling access to patient records, that's the worst kind of bug.

We'll use `EnumType.STRING`. Come back knowing why.

Also useful: **[Persisting Enums in JPA](https://www.baeldung.com/jpa-persisting-enums-in-jpa)** — Baeldung, if you want a second angle with more code.

### 🔴 Essential 2 — How Spring Security actually works
**[Spring Security Architecture](https://docs.spring.io/spring-security/reference/7.0/servlet/architecture.html)** — official docs, version 7.0

You're on Spring Boot 4.0.6, which pairs with **Spring Security 7**. Make sure any tutorial you read is 6.x or 7.x — Spring Security's config style changed substantially at 6.0, and there are thousands of outdated blog posts using `WebSecurityConfigurerAdapter`, which no longer exists. If you see that class, close the tab.

**Focus on:** the `FilterChainProxy` / `SecurityFilterChain` diagram, and the idea that security is a *chain of servlet filters* running before your controller.

**The thing to actually understand:** Spring Security isn't "inside" your controllers. It's a wall of filters your request passes through first. Once you have that picture, `@PreAuthorize`, JWT filters, and CORS all stop feeling like magic.

Companion: **[Authentication Architecture](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/architecture.html)** — `SecurityContext`, `Authentication`, `AuthenticationManager`.

### 🔴 Essential 3 — Never store a password
**[Password Storage](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/passwords/storage.html)** — official docs

**Focus on:** `PasswordEncoder`, BCrypt, and `DelegatingPasswordEncoder`.

**The thing to actually understand:** we're adding `passwordHash`, not `password`. You store a one-way hash and compare hashes — you never store, log, or return the password itself. BCrypt is deliberately *slow* to make brute-forcing expensive, and it salts every hash so two users with the password `hilot123` get completely different stored values.

This matters for your defense: you're storing clinical intake data on patients. "How do you protect credentials?" is a fair question, and "BCrypt via Spring Security's `DelegatingPasswordEncoder`" is a strong answer. Your NFR#2 explicitly claims data security.

---

### 🟡 Optional 4 — Role-based access control
**[Method Security](https://docs.spring.io/spring-security/reference/7.0/servlet/authorization/method-security.html)** — official docs

How `@PreAuthorize("hasRole('ADMIN')")` works. This is how we'll implement Process Rule #5 and FR#4.

Worth noting for later: **branch scoping is not something `@PreAuthorize` solves on its own.** Restricting staff to their own branch means filtering at the *query* level, not just blocking the endpoint. We'll design that in 0.6 — start thinking about it now.

### 🟡 Optional 5 — JWT the supported way
**[OAuth2 Resource Server — JWT](https://docs.spring.io/spring-security/reference/7.0/servlet/oauth2/resource-server/jwt.html)** — official docs

There's a real decision here for 0.6. Most tutorials hand-roll a `OncePerRequestFilter` with the `jjwt` library. Spring Security has built-in JWT support that does it properly. The built-in route is less code and better defended at a panel, but most blog posts you'll find use the hand-rolled approach.

We'll decide together. Skim this so you have an opinion.

### 🟡 Optional 6 — A worked example on your exact stack
**[Spring Boot 4 & Spring Security 7 — JWT + PostgreSQL example](https://github.com/MossaabFrifita/spring-boot-4-security-7-jwt)** (GitHub) and the companion series **[Mastering Spring Security 7 with Spring Boot 4](https://blog.masteringbackend.com/mastering-spring-security-7-with-spring-boot-4-and-java-21-part-7-jwt-authentication-and-authorization-for-secure-rest-ap-is)**

Same Spring Boot 4 / Security 7 / PostgreSQL combination you're on. Useful as a reference, **not** as something to copy wholesale — your branch-scoped RBAC is a requirement these examples don't have.

---

## Part 4 — Come back able to answer these

If you can answer these four, the reading did its job:

1. Why does `EnumType.STRING` matter for a `role` column specifically?
2. Where does Spring Security sit relative to your controllers?
3. Why is the field called `passwordHash` and not `password`?
4. Why isn't `@PreAuthorize("hasRole('STAFF')")` enough to stop Bulan staff from reading Sorsogon's patient records?

Number 4 is the interesting one — and it's the shape of your Process Rule #5.

---

## Sources

- [The best way to map an Enum Type with JPA and Hibernate — Vlad Mihalcea](https://vladmihalcea.com/the-best-way-to-map-an-enum-type-with-jpa-and-hibernate/)
- [Persisting Enums in JPA — Baeldung](https://www.baeldung.com/jpa-persisting-enums-in-jpa)
- [Spring Security 7.0 Reference](https://docs.spring.io/spring-security/reference/7.0/index.html)
- [Spring Security Architecture](https://docs.spring.io/spring-security/reference/7.0/servlet/architecture.html)
- [Authentication Architecture](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/architecture.html)
- [Password Storage](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/passwords/storage.html)
- [Method Security](https://docs.spring.io/spring-security/reference/7.0/servlet/authorization/method-security.html)
- [OAuth2 Resource Server — JWT](https://docs.spring.io/spring-security/reference/7.0/servlet/oauth2/resource-server/jwt.html)
- [spring-boot-4-security-7-jwt — GitHub](https://github.com/MossaabFrifita/spring-boot-4-security-7-jwt)
- [Mastering Spring Security 7 with Spring Boot 4 — Mastering Backend](https://blog.masteringbackend.com/mastering-spring-security-7-with-spring-boot-4-and-java-21-part-7-jwt-authentication-and-authorization-for-secure-rest-ap-is)
