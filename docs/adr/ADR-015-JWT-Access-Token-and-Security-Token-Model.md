# ADR-015 — JWT Access Token and Security Token Model

**Status:** Accepted

**Date:** 2026-09-20

---

# Context

TASK 30 реализует Security Token Model для BlackoutRadar.

После принятия ADR-009 необходимо сохранить архитектурную границу:

Business Domain Model

не должен зависеть от:

- JWT;
- Access Token;
- Refresh Token;
- token claims;
- token storage;
- token lifecycle;
- Spring Security token representation.

Необходимо определить модель Access Token и правила представления authentication identity после TASK 29.

---

# Problem

Необходимо определить:

- формат Access Token;
- модель JWT claims;
- идентификатор пользователя в Access Token;
- границы Security representation;
- правила интеграции JWT со Spring Security;
- запрещенные данные в JWT;
- границу между Business Domain Model и Security Token Model.

---

# Decision

## 1. Access Token

Access Token реализуется как короткоживущий подписанный JWT.

JWT является Security representation.

JWT не является:

- Domain Entity;
- Domain Value Object;
- Business Domain event;
- частью Domain User.

---

## 2. JWT Claims

Минимальный набор стандартных claims:

- `iss` — issuer;
- `sub` — идентификатор пользователя;
- `aud` — audience;
- `iat` — время выпуска;
- `exp` — время окончания действия;
- `jti` — уникальный идентификатор JWT.

Дополнительные claims не добавляются без конкретного требования Security или Authorization.

---

## 3. Subject

`sub` содержит `User.id`.

Email не используется как основной идентификатор пользователя в JWT.

Причина:

User UUID является стабильным техническим идентификатором пользователя, тогда как email является изменяемым пользовательским атрибутом.

---

## 4. JWT не содержит credentials

JWT не должен содержать:

- raw password;
- passwordHash;
- credentials;
- refresh token;
- refresh token hash;
- authentication secret;
- другие секретные данные.

Password authentication выполняется отдельно от JWT issuance.

---

## 5. JWT не содержит Business Domain state

JWT не используется как переносчик произвольного Business Domain Model.

В частности, Access Token не содержит:

- Subscription;
- Address;
- PowerOutage;
- Match;
- Notification;
- TransformerStation;
- другие доменные объекты.

---

## 6. Security Identity

Security representation пользователя отделена от Domain User.

`AuthenticatedUser` не является Domain Entity.

Security identity должна содержать только необходимые для Security данные.

На текущем этапе основным идентификатором является:

- `userId`.

Authorization-specific данные могут быть добавлены в рамках TASK 31.

---

## 7. Authentication Integration

После успешной authentication:

AuthenticationManager

→ Authentication

→ Access Token issuance

→ JWT.

При обращении к защищенному API:

Bearer token

→ JWT validation

→ Authentication

→ SecurityContext.

Domain Model не участвует непосредственно в JWT parsing или validation.

---

## 8. JWT Validation

JWT должен проверяться по следующим основным параметрам:

- подпись;
- issuer;
- audience;
- expiration;
- issued-at при необходимости;
- обязательные claims.

Недействительный JWT не должен приводить к созданию authenticated SecurityContext.

---

## 9. Signing

Access Token должен использовать подписанный JWT.

Конкретная криптографическая конфигурация является Infrastructure/Security concern и не должна попадать в Domain.

Предпочтительным вариантом является асимметричная подпись, позволяющая отделить выпуск токенов от их проверки.

Конкретный алгоритм и управление ключами фиксируются в реализации TASK 30.

---

# Domain Boundary

Business Domain Model не содержит:

- JWT;
- AccessToken Entity;
- JWT claims;
- token secret;
- signing key;
- JwtDecoder;
- JwtEncoder;
- Authentication;
- SecurityContext.

Domain User остаётся независимым от Spring Security.

---

# Security Boundary

Security владеет:

- Access Token;
- JWT;
- JWT claims;
- JWT issuance;
- JWT validation;
- signing;
- verification;
- authentication integration.

---

# Configuration Boundary

Срок жизни Access Token не является архитектурной константой.

Он должен задаваться конфигурацией Security.

Архитектура не фиксирует конкретное значение срока жизни.

---

# Consequences

## Positive

### 1. Чистая Domain Model

JWT и Security state не проникают в Business Domain.

### 2. Минимальный Token Model

JWT содержит только данные, необходимые для Security.

### 3. Независимость от Spring Security

Domain User не зависит от Security framework.

### 4. Возможность замены механизма token issuance

JWT implementation находится за Security/Infrastructure boundary.

### 5. Подготовка Authorization

TASK 31 сможет использовать Security identity без изменения Business Domain Model.

---

# Negative Consequences

### 1. Security получает собственную модель

Security representation пользователя отделена от Domain User.

### 2. JWT требует отдельной configuration

Необходимо управлять:

- issuer;
- audience;
- signing keys;
- expiration.

### 3. JWT не содержит произвольные пользовательские данные

При необходимости дополнительных данных они должны быть явно обоснованы Security/Authorization requirements.

---

# Scope

ADR распространяется на:

- TASK 30;
- Access Token;
- JWT;
- Security identity;
- JWT validation;
- authentication integration.

ADR не определяет:

- Authorization policy;
- роли и права доступа;
- GitHub OAuth2;
- Security Hardening;
- окончательную CSRF/CORS policy.

Эти вопросы относятся к последующим TASK.

---

# Architectural Rules Resulting from ADR

1. Access Token реализуется как JWT.
2. JWT является Security representation.
3. JWT не является Domain Entity.
4. `sub` содержит `User.id`.
5. JWT не содержит password.
6. JWT не содержит passwordHash.
7. JWT не содержит refresh token.
8. JWT не содержит refresh token hash.
9. JWT не содержит Business Domain entities.
10. Domain User не зависит от JWT.
11. Domain User не зависит от Spring Security.
12. Security identity отделена от Domain User.
13. JWT validation находится за пределами Domain.
14. Access Token lifecycle является Security responsibility.
15. Изменение этих правил требует нового ADR.