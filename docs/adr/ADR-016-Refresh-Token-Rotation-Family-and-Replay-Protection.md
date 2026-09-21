# ADR-016 — Refresh Token Rotation Family and Replay Protection

**Status:** Accepted

**Date:** 2026-09-20

---

# Context

ADR-009 определил Refresh Token как Security concern и передал его реализацию в Security Phase.

TASK 30 требует определить lifecycle Refresh Token.

Необходимо обеспечить:

- expiration;
- revocation;
- rotation;
- защиту от повторного использования;
- изоляцию независимых authentication sessions.

Простая модель одного долгоживущего Refresh Token не обеспечивает необходимую защиту после компрометации токена.

---

# Problem

Необходимо определить:

- как связаны последовательные Refresh Tokens;
- как выполняется rotation;
- как определяется повторное использование;
- что происходит после обнаружения повторного использования;
- как изолируются разные authentication sessions;
- как обеспечивается атомарность rotation.

---

# Decision

## 1. Refresh Token является opaque credential

Refresh Token не является JWT.

Refresh Token генерируется как криптографически случайное значение.

Raw Refresh Token не хранится в Database.

В Database хранится только его безопасное представление — `token_hash`.

---

## 2. Rotation Family

Каждая независимая authentication session получает собственную Refresh Token Family.

Пример:

Family F1:

R1 → R2 → R3 → R4

Каждый новый Refresh Token относится к той же Family.

Другая authentication session получает другую Family:

Family F2:

R5 → R6 → R7

Family F1 и Family F2 не связаны между собой.

---

## 3. Refresh Token rotation

При нормальном refresh:

1. Security получает raw Refresh Token.
2. Вычисляется его hash.
3. Выполняется поиск соответствующей записи.
4. Проверяется существование токена.
5. Проверяется expiration.
6. Проверяется отсутствие revocation.
7. Выполняется атомарная rotation.
8. Старый Refresh Token становится revoked.
9. Создается новый Refresh Token.
10. Новый Refresh Token получает тот же `family_id`.
11. Выпускается новый Access Token.

Таким образом:

R1 → R2

после успешного refresh R1 больше не является действующим Refresh Token.

---

## 4. Replay Detection

Повторное использование уже revoked Refresh Token считается обнаружением повторного использования Refresh Token.

Пример:

R1 → R2

После успешного refresh:

R1 = revoked

Если после этого снова предъявлен R1:

обнаруживается reuse.

---

## 5. Family Revocation

При обнаружении повторного использования Refresh Token вся соответствующая Family отзывается.

Пример:

Family F1:

R1 → R2 → R3

Если R1 используется повторно после rotation:

Family F1 становится недействительной.

Все действующие Refresh Tokens этой Family отзываются.

---

## 6. Изоляция Authentication Sessions

Revocation одной Family не должна автоматически отзывать Refresh Tokens другой Family.

Пример:

Family F1 → revoked

Family F2 → остаётся действующей.

Это позволяет отдельно завершать скомпрометированную authentication session.

---

## 7. Family Identifier

Каждый Refresh Token содержит Security/Persistence-level `family_id`.

`family_id` не является частью Domain Model.

`family_id` используется только для управления Security lifecycle Refresh Tokens.

---

## 8. Database Model

Существующая таблица `refresh_token` расширяется полем:

- `family_id`.

Итоговая модель содержит:

- `id`;
- `user_id`;
- `family_id`;
- `token_hash`;
- `expires_at`;
- `revoked_at`;
- `created_at`;
- `updated_at`.

`family_id` является обязательным для Refresh Token.

Для `family_id` создается индекс.

`token_hash` должен иметь уникальность.

---

## 9. Atomic Rotation

Rotation выполняется атомарно в одной Database transaction.

Операции:

- проверка токена;
- блокировка соответствующей записи;
- revocation старого токена;
- создание нового токена

не должны выполняться как независимые операции.

JVM-level locks не используются как основной механизм защиты.

Database является источником истины для конкурентного доступа.

---

## 10. Concurrent Refresh

Если два запроса одновременно пытаются выполнить rotation одного Refresh Token, только один запрос должен успешно выполнить rotation.

Второй запрос обнаруживает, что исходный Refresh Token уже revoked.

Повторное использование revoked Refresh Token рассматривается как reuse.

Таким образом, клиент должен сериализовать refresh-запросы и не отправлять несколько одновременных запросов с одним Refresh Token.

---

## 11. Family Revocation

При обнаружении reuse Security отзывает все действующие Refresh Tokens соответствующей Family.

Операция выполняется в Database transaction.

Уже revoked записи повторно менять не требуется.

---

## 12. Expiration

Каждый Refresh Token имеет собственный `expires_at`.

Истечение срока действия делает Refresh Token недействительным.

Expired Refresh Token не может использоваться для rotation.

Срок жизни Refresh Token является конфигурацией Security и не является Business Domain rule.

---

## 13. Logout

Logout может отзывать Refresh Token или соответствующую authentication session.

Access Token не требует обязательного Database lookup на каждый запрос.

Короткий срок жизни Access Token ограничивает период его действия после logout.

Политика logout и окончательная web/API модель уточняются в рамках TASK 30 и TASK 33, если это потребуется.

---

## 14. Security Boundary

Refresh Token lifecycle находится в Security/Application boundary.

Domain Model не содержит:

- RefreshToken Entity;
- raw Refresh Token;
- token hash;
- family_id;
- expiration state;
- revocation state;
- rotation state;
- replay state.

---

# Persistence Boundary

Persistence Model хранит Security state Refresh Tokens.

Database representation не является Domain Model.

Liquibase используется для изменения Database Schema.

Существующая таблица `refresh_token`,
описанная в ADR-009 на момент его принятия,
расширяется данным ADR полем `family_id`
с соответствующим индексом.

---

# Relation to ADR-009

Данный ADR уточняет и развивает
persistence и lifecycle решения ADR-009:

- Security Boundary из ADR-009
  (RefreshToken не является частью Business Domain Model)
  остается в силе без изменений;
- утверждения ADR-009 о неизменности
  Database Schema и Liquibase changesets
  относились к состоянию на 2026-08-18
  и уточнены разделом
  ## 8. Database Model данного ADR.

Связанный документ:

- [ADR-009 — RefreshToken Security Boundary](<ADR-009-RefreshToken Security Boundary.md>)

---

# Consequences

## Positive

### 1. Защита от повторного использования

После успешной rotation старый Refresh Token больше не может быть повторно использован без обнаружения.

### 2. Компрометация ограничивается Family

При обнаружении reuse отзывается конкретная authentication session, а не все sessions пользователя.

### 3. Одноразовость Refresh Token

Каждый Refresh Token используется только для одной успешной rotation.

### 4. Database является источником истины

Конкурентная rotation защищается Database transaction и locking.

### 5. Security state не проникает в Domain

Rotation Family полностью остается Security/Persistence concern.

---

# Negative Consequences

### 1. Дополнительная Database state

Необходимо хранить:

- family_id;
- token_hash;
- revoked_at.

### 2. Дополнительная сложность rotation

Refresh operation требует транзакционной обработки.

### 3. Повторный запрос может считаться reuse

Параллельные refresh-запросы одного клиента могут привести к обнаружению reuse.

Клиент должен обеспечивать последовательное использование Refresh Token.

### 4. Необходимость корректной конкурентной реализации

Ошибочная последовательность:

read → check → update

без защиты транзакцией недопустима.

---

# Scope

ADR распространяется на:

- TASK 30;
- Refresh Token;
- Refresh Token rotation;
- rotation family;
- replay detection;
- revocation;
- Refresh Token persistence.

ADR не определяет:

- Authorization;
- GitHub OAuth2;
- Security Hardening;
- окончательную CSRF/CORS policy;
- Business Domain behavior.

---

# Architectural Rules Resulting from ADR

1. Refresh Token является Security credential.
2. Refresh Token не является JWT.
3. Raw Refresh Token не хранится в Database.
4. Database хранит только `token_hash`.
5. Каждый Refresh Token принадлежит одной Rotation Family.
6. Каждая authentication session получает собственную Family.
7. Успешная rotation отзывает старый Refresh Token.
8. Новый Refresh Token получает тот же `family_id`.
9. Повторное использование revoked Refresh Token считается reuse.
10. Reuse приводит к revocation всей соответствующей Family.
11. Revocation одной Family не отзывает другие Family.
12. Rotation выполняется атомарно.
13. Database является источником истины для concurrent rotation.
14. `family_id` не является Domain state.
15. Refresh Token lifecycle не является Business Domain logic.
16. Изменение этого решения требует нового ADR.