# ADR-009 — RefreshToken Security Boundary

**Status:** Accepted

**Date:** 2026-08-18

---

# Context

В первоначальной Domain Model
RefreshToken был определен
как Domain Entity
в рамках TASK 6 — Identity and Subscription Domain.

RefreshToken содержал:

- UUID identity;
- User;
- expiresAt;
- revokedAt.

Также были определены
операции lifecycle:

- expiration;
- revocation;
- usability.

При подготовке TASK 10 —
Identity and Subscription Persistence —
было проведено повторное архитектурное
исследование ответственности RefreshToken.

---

# Problem

Необходимо определить,
является ли RefreshToken
частью Business Domain Model
или Security/Application concern.

Основной критерий:

> Имеет ли RefreshToken самостоятельное
> бизнесовое значение в предметной области
> BlackoutRadar независимо от authentication?

---

# Analysis

RefreshToken используется
для продолжения authentication session
и получения нового Access Token.

Его lifecycle включает:

- issuance;
- expiration;
- revocation;
- rotation;
- validation.

Эти операции относятся
к authentication/security.

RefreshToken не участвует
непосредственно в бизнес-процессах:

- Address;
- Subscription;
- TransformerStation;
- PowerOutage;
- Matching;
- Notification;
- Outage Processing.

Нет бизнесового правила,
в котором RefreshToken является
частью предметной логики BlackoutRadar.

---

# Decision

`RefreshToken` **не является
частью Business Domain Model**.

RefreshToken относится
к Security/Application boundary.

RefreshToken должен быть реализован
в Security Phase.

В частности:

- RefreshToken не является Domain Entity;
- RefreshToken не находится
  в domain.identity;
- RefreshToken lifecycle является
  Security responsibility;
- token storage является
  Security/Persistence responsibility;
- token hashing является
  Security responsibility;
- token rotation является
  Security responsibility.

---

# Domain Boundary

Business Domain Model
не содержит:

- RefreshToken Entity;
- raw refresh token;
- tokenHash;
- token expiration state;
- token revocation state;
- token rotation state.

Domain Model также не содержит
RefreshToken-specific Port.

---

# Security Boundary

Security владеет:

- Refresh Token lifecycle;
- Access Token lifecycle;
- JWT;
- authentication credentials;
- token validation;
- token revocation;
- token rotation.

Конкретная реализация выполняется
в Security Phase.

---

# Persistence Boundary

Физическое хранение Refresh Token
остается частью Persistence Model.

Существующая таблица:

refresh_token

содержит:

- id;
- user_id;
- token_hash;
- expires_at;
- revoked_at;
- created_at;
- updated_at.

Database Model не изменяется
данным ADR.

Liquibase changesets
не изменяются данным ADR.

Refresh Token Persistence
будет использована будущей
Security implementation.

---

# Consequences

## Positive

### 1. Чистый Business Domain

Domain Model содержит
только предметные сущности
BlackoutRadar.

Security-specific concepts
не загрязняют Domain.

### 2. Четкое разделение ответственности

Business Domain:

- Subscription;
- Address;
- PowerOutage;
- Notification;
- User;
- TransformerStation.

Security:

- Authentication;
- Authorization;
- JWT;
- Access Token;
- Refresh Token.

### 3. Упрощение Persistence

Не требуется mapping:

Domain RefreshToken
↔
Persistence RefreshTokenEntity.

Не требуется специальный
RefreshTokenCredential
на Domain Port boundary.

### 4. Replaceable Security Infrastructure

Конкретный механизм authentication
может изменяться
без изменения Business Domain Model.

### 5. Отсутствие persistence leakage

`token_hash` остается
техническим Security/Persistence representation.

---

# Negative Consequences

### 1. RefreshToken больше не является Domain Entity

Security lifecycle нельзя реализовывать
через Domain Entity.

Lifecycle реализуется
в Security/Application layer.

### 2. Security получает собственную модель

Security implementation должна иметь
собственные модели и contracts
для Refresh Token.

Это увеличивает локальную сложность
Security subsystem.

### 3. TASK 6 требует архитектурной коррекции

Первоначальное решение TASK 6
с RefreshToken Domain Entity
становится устаревшим.

История TASK 6 сохраняется
в TASK_LOG.

---

# Impact on TASK 10

TASK 10 — Identity and Subscription Persistence
не включает RefreshToken.

Scope TASK 10:

- User;
- TransformerStation;
- Subscription.

RefreshToken Persistence
переносится в Security Phase.

---

# Impact on TASK 30

TASK 30 — JWT and Refresh Token Security
становится владельцем полной реализации:

- Access Token;
- JWT;
- Refresh Token;
- secure storage;
- validation;
- expiration;
- revocation;
- rotation;
- authentication integration.

---

# Impact on Database

Database Schema не изменяется.

Таблица refresh_token
остается необходимой.

Существующие:

- PK;
- FK;
- UNIQUE;
- indexes;
- timestamps

остаются без изменений.

---

# Impact on Liquibase

Liquibase не изменяется.

Новый changeset
для удаления Refresh Token
не требуется.

Новый changeset
для создания Refresh Token
не требуется.

Таблица уже существует
и будет использована Security Phase.

---

# Impact on Documentation

Необходимо синхронизировать:

- 02-DOMAIN_MODEL.md;
- 07-SECURITY.md;
- TASK_PLAN.md;
- TASK_LOG.md;
- 00.5-GLOSSARY.md;
- Domain Model diagram.

Не требуется изменение:

- Database ER diagram;
- Security Flow diagram;
- Package Responsibility diagram.

---

# Alternatives Considered

## Alternative 1 — оставить RefreshToken в Domain

Отклонено.

Причина:

RefreshToken не имеет
самостоятельного Business Domain meaning.

Его lifecycle является
Security lifecycle.

---

## Alternative 2 — добавить tokenHash в Domain

Отклонено.

Это еще сильнее смешивает
Business Domain Model
с Security/Persistence representation.

---

## Alternative 3 — оставить RefreshToken
в Domain, но скрыть tokenHash

Отклонено.

Это решает только
Persistence mapping problem,
но не решает проблему
неправильной архитектурной принадлежности
RefreshToken.

---

## Alternative 4 — создать RefreshTokenCredential
в Domain

Отклонено.

После удаления RefreshToken
из Domain необходимость
в такой abstraction исчезает.

Security layer должен самостоятельно
определять собственный credential contract.

---

# Architectural Rules Resulting from ADR

1. RefreshToken не является Domain Entity.
2. RefreshToken не размещается в domain.identity.
3. RefreshToken lifecycle является Security concern.
4. tokenHash не является Domain state.
5. raw refresh token не является Domain state.
6. Security infrastructure не проникает в Business Domain.
7. Database representation может существовать
   независимо от Domain Entity.
8. TASK 10 не реализует RefreshToken.
9. TASK 30 реализует RefreshToken Security.
10. Изменение этого решения требует нового ADR.

---

# Related Documents

- [02-DOMAIN_MODEL](../02-DOMAIN_MODEL.md)
- [03-DATABASE](../03-DATABASE.md)
- [07-SECURITY](../07-SECURITY.md)
- [TASK_PLAN](../TASK_PLAN.md)
- [TASK_LOG](../TASK_LOG.md)
- [ADR-001 — Domain First Architecture](ADR-001-Domain-First-Architecture.md)
- [ADR-007 — Replaceable Infrastructure](ADR-007-Replaceable-Infrastructure.md)

---

# Related Diagrams

- [Domain Model](../diagrams/detailed/05-domain-model.puml)
- [Security Flow](../diagrams/detailed/10-security-flow.puml)
- [Package Responsibility](../diagrams/overview/03-package-responsibility.puml)

---

# Status

**Accepted**