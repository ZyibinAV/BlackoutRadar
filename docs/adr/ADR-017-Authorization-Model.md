# ADR-017 — Authorization Model

**Статус:** Accepted

**Дата:** 2026-09-21

---

## Context

TASK 31 определяет модель Authorization для BlackoutRadar.

Authentication и Authorization являются разными ответственностями:

- Authentication подтверждает личность пользователя;
- Authorization определяет, какие действия разрешены аутентифицированному пользователю.

После TASK 29 и TASK 30 в системе уже существует Security representation пользователя и JWT-based Access Token.

Необходимо определить:

- где находится ответственность за Authorization;
- как представлены роли;
- как `UserRole` связан с Security authorities;
- где выполняется проверка доступа;
- как разделяются RBAC и проверка владения ресурсом;
- должен ли JWT содержать роль пользователя;
- как изменение роли пользователя влияет на Authorization;
- какие границы должны сохраняться между Domain и Security.

---

# Problem

Необходимо избежать смешения Business Domain Model и Spring Security Authorization Model.

В текущей архитектуре:

- Domain `User` содержит роль пользователя;
- `UserRole` является частью Domain Model;
- `AuthenticatedUser` является Security representation;
- Spring Security используется для Authentication;
- JWT является Security representation.

Необходимо сохранить эту границу и определить правила Authorization без проникновения Spring Security в Domain.

---

# Decision

## 1. Authorization является ответственностью Security

Authorization является Security responsibility.

Security отвечает за:

- определение доступности защищенных операций;
- преобразование Security identity в authorities;
- проверку ролей;
- endpoint authorization;
- method-level authorization;
- проверку security policy.

Domain Model не выполняет Spring Security authorization checks.

---

## 2. UserRole остается атрибутом Domain User

`UserRole` остается частью Domain Model.

Текущий набор ролей:

```text
USER
ADMIN
```

Роль является атрибутом `User`, а не Security Entity.

Domain может знать:

```text
User
    ↓
UserRole
```

Domain не должен знать:

- `GrantedAuthority`;
- `ROLE_USER`;
- `ROLE_ADMIN`;
- `Authentication`;
- `SecurityContext`;
- `@PreAuthorize`;
- Spring Security;
- другие механизмы реализации Authorization.

Таким образом:

```text
Domain
    User
      ↓
   UserRole
```

и:

```text
Security
    UserRole
      ↓
GrantedAuthority
```

являются разными уровнями модели.

---

## 3. Mapping UserRole → Spring Security authority выполняется в Security

Security преобразует Domain `UserRole` в Spring Security authority.

Используется mapping:

```text
UserRole.USER
    ↓
ROLE_USER

UserRole.ADMIN
    ↓
ROLE_ADMIN
```

`ROLE_USER` и `ROLE_ADMIN` являются Security representation и не являются частью Domain Model.

---

## 4. RBAC используется как базовая модель Authorization

Основной механизм Authorization — role-based access control.

Роль определяет, какие категории операций доступны пользователю.

При этом роль не является единственным возможным условием доступа.

Authorization policy может дополнительно учитывать:

- identity пользователя;
- владение конкретным ресурсом;
- другие явно определенные security conditions.

---

## 5. RBAC и ownership являются разными проверками

Необходимо разделять:

### RBAC

Отвечает на вопрос:

> Может ли пользователь с данной ролью выполнять этот тип операции?

Например:

```text
ADMIN → управление пользователями
USER  → пользовательские операции
```

### Ownership

Отвечает на вопрос:

> Может ли данный пользователь выполнять операцию именно над этим ресурсом?

Например:

```text
User A
    ↓
Subscription A
```

Пользователь не должен получать доступ к `Subscription B` только потому, что он имеет роль `USER`.

RBAC и ownership не объединяются в одну проверку.

---

## 6. Authorization выполняется за пределами Domain Model

Authorization checks не добавляются в Domain entities.

Domain entity не должна содержать:

```text
isAllowed(...)
hasAuthority(...)
isAdmin(...)
canAccess(...)
```

если эти методы предназначены именно для Spring Security authorization policy.

Security/Application boundary отвечает за enforcement authorization policy.

При необходимости бизнес-правила предметной области остаются в Domain.

---

## 7. Endpoint Authorization находится на Security/Web boundary

Доступ к защищенным API определяется на границе Web/Security.

Authorization policy должна применяться к реальным защищенным endpoint'ам.

Принцип:

```text
HTTP Request
    ↓
Authentication
    ↓
Authorization
    ↓
Application Use Case
    ↓
Domain
```

Domain не должен знать, что вызов был разрешен через Spring Security.

На текущем этапе REST Web Layer еще не является завершенной частью Security implementation.

Поэтому TASK 31 не должен создавать искусственные endpoint'ы только ради демонстрации Authorization.

---

## 8. Method-level Authorization находится за пределами Domain

Для Application/Web entry points допускается method-level authorization средствами Security framework.

Например:

```text
@PreAuthorize(...)
```

если это необходимо для конкретного Application/Web entry point.

Spring Security annotations и другие механизмы method-level authorization не должны попадать в Domain.

Не следует одновременно дублировать одну и ту же Authorization policy несколькими независимыми механизмами без необходимости.

---

## 9. JWT остается минимальным

JWT Access Token сохраняет минимальный набор claims, установленный ADR-015:

```text
iss
sub
aud
iat
exp
jti
```

Role claim в JWT не добавляется автоматически в рамках TASK 31.

То есть:

```text
JWT
 ├─ iss
 ├─ sub
 ├─ aud
 ├─ iat
 ├─ exp
 └─ jti
```

а не:

```text
JWT
 └─ role
```

если для этого не появится отдельное обоснованное требование.

Причина:

- не увеличивать JWT без необходимости;
- не дублировать Security state;
- не создавать дополнительный источник Authorization data;
- избежать устаревшей роли в уже выданном Access Token после изменения роли пользователя.

---

## 10. Role change не должен требовать изменения Domain architecture

Роль пользователя может изменяться как состояние `User`.

Authorization использует актуальную Security identity и актуальную роль пользователя согласно реализации Security/Application flow.

Уже выданный JWT не становится отдельным источником истины о роли, поскольку role claim в JWT не используется.

При необходимости немедленного отзыва уже выданных Access Token это является отдельным Security Hardening / Token Lifecycle вопросом и не определяется данным ADR.

---

## 11. JWT authentication получает актуальное состояние пользователя по sub

JWT остаётся минимальным и содержит `sub` как `User.id`.

При JWT authentication Security получает актуальное состояние пользователя по `sub` через Persistence boundary:

```text
valid JWT
    ↓
current user lookup
    ↓
active check
    ↓
UserRole
    ↓
GrantedAuthority
```

Правила:

- роль не хранится в JWT и берётся из актуального состояния пользователя;
- если пользователь отсутствует — authentication отклоняется;
- если пользователь inactive — authentication отклоняется;
- если пользователь active, authority определяется mapping данного ADR:

```text
USER  → ROLE_USER
ADMIN → ROLE_ADMIN
```

Разрешение пользователя является Security responsibility и выполняется за пределами Domain Model через существующий persistence repository. Новый Domain Port для этого не вводится.

---

# Domain Boundary

Domain Model знает:

```text
User
    ↓
UserRole
```

Domain Model не знает:

- Spring Security;
- `GrantedAuthority`;
- `ROLE_USER`;
- `ROLE_ADMIN`;
- Authentication;
- SecurityContext;
- JWT;
- authorization annotations;
- endpoint security;
- Security authorization filters.

---

# Security Boundary

Security владеет:

- Authentication;
- Authorization;
- Security identity;
- authorities;
- role-to-authority mapping;
- endpoint authorization;
- method-level authorization;
- JWT validation;
- Access Token;
- Security policy enforcement.

---

# Authorization Model

Базовая схема:

```text
User
  ↓
UserRole
  ↓
Security Identity
  ↓
GrantedAuthority
  ↓
Authorization Policy
  ↓
Allowed / Denied
```

Для операций над конкретными ресурсами:

```text
Authenticated User
        ↓
      Role
        ↓
  RBAC Policy
        ↓
Ownership / Resource Policy
        ↓
 Allowed / Denied
```

RBAC не заменяет ownership checks.

Ownership checks не являются частью `UserRole`.

---

# Consequences

## Positive

### 1. Сохраняется Domain First Architecture

Domain остается независимым от Spring Security.

### 2. Security имеет собственную модель Authorization

Spring Security authorities не проникают в Domain.

### 3. UserRole остается естественным атрибутом User

Не требуется искусственно переносить роль из Domain в Security.

### 4. JWT остается минимальным

Не появляется дополнительный role claim без необходимости.

### 5. RBAC и ownership не смешиваются

Роль определяет категорию разрешенных операций, а ownership определяет доступ к конкретному ресурсу.

### 6. Возможна замена Security implementation

Domain не зависит от конкретного механизма Authorization.

---

# Negative Consequences

### 1. Существует преобразование между моделями

Security должна преобразовывать:

```text
UserRole
    ↓
GrantedAuthority
```

### 2. Authorization policy распределена по Security/Web boundary

Необходима аккуратная организация правил доступа, чтобы не получить дублирование.

### 3. Ownership требует отдельной проверки

Одной проверки роли недостаточно для операций над пользовательскими ресурсами.

### 4. Изменение роли может потребовать отдельной Security policy

Если проекту потребуется немедленная инвалидация уже выданных Access Token после изменения роли, это должно быть определено отдельным Security решением.

---

# Scope

ADR распространяется на:

- TASK 31;
- Authorization;
- User roles;
- Security authorities;
- RBAC;
- ownership checks;
- endpoint authorization;
- method-level authorization;
- связь Authorization с JWT.

ADR не определяет:

- OAuth2 Provider;
- CSRF policy;
- CORS policy;
- Security Headers;
- Secrets Management;
- отдельный механизм немедленной инвалидации Access Token;
- конкретную REST API структуру;
- конкретные Application use cases, которые появятся позднее.

---

# Related Decisions

- ADR-001 — Domain First Architecture
- ADR-007 — Replaceable Infrastructure
- ADR-009 — RefreshToken Security Boundary
- ADR-015 — JWT Access Token and Security Token Model
- ADR-016 — Refresh Token Rotation Family and Replay Protection

---

# Architectural Rules Resulting from ADR

1. Authorization является Security responsibility.
2. `UserRole` остается атрибутом Domain `User`.
3. Domain не зависит от Spring Security Authorization.
4. `GrantedAuthority` не является частью Domain Model.
5. `ROLE_USER` и `ROLE_ADMIN` являются Security representation.
6. Security преобразует `UserRole` в authorities.
7. RBAC является базовой моделью Authorization.
8. RBAC и ownership являются разными проверками.
9. Ownership не определяется только ролью пользователя.
10. Endpoint authorization находится на Web/Security boundary.
11. Method-level authorization находится за пределами Domain.
12. Domain entities не содержат Spring Security authorization checks.
13. JWT остается минимальным согласно ADR-015.
14. Role claim в JWT не добавляется без отдельного обоснования.
15. Authorization policy не должна дублироваться без необходимости.
16. Изменение данных Authorization Model, определенных данным ADR, требует нового ADR.