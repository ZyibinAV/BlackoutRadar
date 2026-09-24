# BlackoutRadar — Security

> Модель безопасности проекта BlackoutRadar.

---

# Назначение

Документ описывает архитектурные
и security requirements проекта.

Security отвечает за:

- Authentication;
- Authorization;
- Access Token;
- JWT;
- Refresh Token;
- OAuth2 Client;
- Password Security;
- Security Context;
- security-related infrastructure.

Security не является частью
Business Domain Model.

---

# Security Boundary

Security является отдельной архитектурной
ответственностью.

Security-specific concepts
не являются Domain Entities.

К Security concepts относятся:

- Authentication;
- Authorization;
- Access Token;
- JWT;
- Refresh Token;
- OAuth2 Client;
- Password credentials;
- Security Context.

Domain Model не должна содержать
security-specific entities
или infrastructure-specific
security state.

---

# Основные компоненты

Подсистема включает:

- Authentication;
- Authorization;
- JWT;
- Access Token;
- Refresh Token;
- OAuth2 Client;
- Password Encoder;
- Security Context.

---

# Authentication

Authentication отвечает
за подтверждение личности пользователя.

После успешной проверки
система создает
Security Context.

## Поддерживаемые способы

На первом этапе:

- Email + Password.

На последующих этапах:

- GitHub OAuth2.

Архитектура допускает
добавление других Provider.

## Основные принципы

Authentication:

- не определяет права;
- не принимает бизнес-решения;
- только подтверждает личность.

Authentication не изменяет
Business Domain Model.

---

## Local Authentication

Local Authentication является Security responsibility.

Первый поддерживаемый способ:
Email + Password.

Authentication не изменяет Business Domain Model.

### Registration

Регистрация выполняется через Application / Security boundary.

Flow:

raw password
↓
PasswordEncoder
↓
passwordHash
↓
UserPort.register(...)
↓
atomic persistence
↓
User + local password credential

Raw password:

- не передается в Domain;
- не передается в Persistence;
- не хранится;
- не логируется;
- не возвращается API.

`passwordHash` не является частью Domain `User`.

Для регистрации используется отдельная операция
`UserPort.register(...)`.

Она атомарно создает пользователя
и его локальные credentials.

Конкурентная регистрация
с одинаковым email
защищается Database unique constraint.

Database является окончательной
границей конкурентной корректности.

Обычный `UserPort.save(User)`
не используется как замена
операции локальной регистрации.

Результаты регистрации:

- CREATED;
- ALREADY_EXISTS.

`ALREADY_EXISTS` является нормальным
результатом конкурентной регистрации,
а не механизмом синхронизации через exception.

### Password Verification

При входе AuthenticationProvider
получает:

- User через `UserPort.findByEmail(...)`;
- passwordHash через `UserPort.findPasswordHash(...)`.

Проверка выполняется через
`PasswordEncoder.matches(...)`.

`passwordHash` не добавляется
в Domain `User`.

### Login

Flow:

email + password
↓
AuthenticationManager
↓
AuthenticationProvider
↓
UserPort
↓
PasswordEncoder.matches(...)
↓
Authentication
↓
SecurityContext

AuthenticationProvider:

- проверяет наличие пользователя;
- проверяет возможность аутентификации
  активной учетной записи;
- проверяет password;
- создает успешный Authentication
  либо сообщает об ошибке.

Неуспешная authentication
не должна раскрывать,
существует ли указанный email.

### Security Representation

Spring Security representation
пользователя находится
за пределами Domain Layer.

Domain `User`:

- не реализует `UserDetails`;
- не зависит от Spring Security;
- не содержит Authentication;
- не содержит SecurityContext;
- не содержит passwordHash.

SecurityContext является
частью Security infrastructure.

### Transaction Boundary

Registration выполняется
в рамках Application transaction boundary.

Создание User и passwordHash
должно быть одной атомарной
persistence operation.

Login не изменяет
Business Domain Model.

---

# Authorization

Authorization определяет,
какие действия доступны пользователю.

## Роли

На текущем этапе используются:

- USER;
- ADMIN.

## UserRole и Security Authority

`UserRole` является атрибутом Domain `User`.

Domain знает:

```text
User
  ↓
UserRole
```

Domain не знает:

- `GrantedAuthority`;
- `ROLE_USER`;
- `ROLE_ADMIN`;
- `Authentication`;
- `SecurityContext`;
- Spring Security authorization mechanisms.

Security преобразует Domain role в Security authority:

```text
UserRole.USER
    ↓
ROLE_USER

UserRole.ADMIN
    ↓
ROLE_ADMIN
```

`ROLE_USER` и `ROLE_ADMIN` являются Security representation.

---

## RBAC

Базовая модель Authorization — RBAC.

Role определяет категорию операций, доступных пользователю.

RBAC отвечает на вопрос:

> Может ли пользователь с данной ролью выполнять этот тип операции?

---

## Ownership

RBAC не заменяет проверку владения конкретным ресурсом.

Ownership отвечает на вопрос:

> Может ли данный пользователь выполнять операцию именно над этим ресурсом?

Например, роль `USER` сама по себе не предоставляет доступ ко всем `Subscription`.

Для пользовательских ресурсов может потребоваться:

```text
authenticated user
        ↓
role check
        ↓
ownership check
        ↓
access
```

RBAC и ownership являются отдельными проверками.

---

## Endpoint Authorization

Authorization защищенных API выполняется на Web/Security boundary.

Общий поток:

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

Domain Model не выполняет Spring Security authorization checks.

---

## Method-level Authorization

Method-level Authorization может использоваться на Web/Application entry points средствами Security framework.

Например:

```text
@PreAuthorize(...)
```

если это требуется конкретным use case.

Spring Security annotations и другие механизмы method-level authorization не должны попадать в Domain Model.

Одна и та же Authorization policy не должна без необходимости дублироваться несколькими независимыми механизмами.

---

## JWT и Authorization

JWT остается минимальным согласно ADR-015.

Role claim в JWT не добавляется автоматически.

Текущий JWT содержит:

```text
iss
sub
aud
iat
exp
jti
```

Отсутствие role claim означает, что JWT не является отдельным источником Authorization role state.

Если в будущем потребуется добавить role claim, это должно быть отдельно обосновано и зафиксировано архитектурным решением.

---

## JWT authentication и актуальная роль

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

- role не хранится в JWT;
- роль берётся из актуального состояния пользователя;
- missing user отклоняется;
- inactive user отклоняется;
- активный пользователь получает authority по mapping:

```text
USER  → ROLE_USER
ADMIN → ROLE_ADMIN
```

Разрешение пользователя выполняется за пределами Domain Model через существующий persistence repository. Новый Domain Port для этого не вводится.

---

## Изменение роли

`UserRole` является состоянием пользователя.

Изменение роли не требует изменения Domain/Security boundary.

Поскольку текущий JWT не содержит role claim, Authorization не зависит от устаревшего значения роли, записанного в ранее выданном JWT.

Немедленная инвалидация уже выданных Access Token после изменения роли является отдельным Security Hardening / Token Lifecycle вопросом и данным разделом не определяется.

## USER

Имеет доступ
только к собственным данным
в соответствии с API policy.

## ADMIN

Имеет право:

- управлять пользователями;
- управлять Source;
- выполнять административные операции.

## Основные принципы

Авторизация строится
по принципу Least Privilege.

Каждый пользователь получает
только необходимые права.

---

# Access Token

Access Token используется
для доступа к защищенным API.

Access Token является
краткоживущим security credential.

Access Token не является
Domain Entity.

---

# JWT

JWT используется
для аутентификации
REST API.

## Назначение

Предоставить краткоживущий
Access Token.

## Основные принципы

JWT:

- не хранится в базе данных;
- имеет ограниченный срок действия;
- используется для подтверждения
  личности пользователя;
- не является частью Business Domain Model.

## Содержимое

JWT содержит
минимально необходимую информацию.

Персональные данные
не должны дублироваться
в токене.

JWT claims не являются
Domain Model.

---

# Refresh Token

Refresh Token является
Security/Application concept.

Refresh Token **не является
Domain Entity**.

Он используется
для получения нового Access Token
без повторного ввода
учетных данных пользователя.

## Основные принципы

Refresh Token:

- хранится в защищенном виде
  в базе данных;
- имеет более длительный срок действия,
  чем Access Token;
- может быть отозван;
- может участвовать в token rotation;
- не является частью Business Domain Model.

## Lifecycle

Создание

↓

Использование

↓

Rotation / Renewal

↓

Revocation

или

↓

Expiration

## Security responsibility

Refresh Token lifecycle включает:

- issuance;
- validation;
- expiration;
- revocation;
- rotation;
- secure storage.

Все перечисленные операции
являются Security concerns.

## Storage

Persistence representation
Refresh Token хранится
в существующей таблице:

refresh_token

Физическая модель включает:

- id;
- user_id;
- token_hash;
- expires_at;
- revoked_at;
- created_at;
- updated_at.

Исходное значение Refresh Token
не должно храниться в базе данных
в открытом виде.

## Domain Boundary

Business Domain Model
не содержит:

- RefreshToken Entity;
- tokenHash;
- raw refresh token;
- RefreshToken lifecycle;
- token rotation state.

Refresh Token реализуется
в Security Phase.

---

# OAuth2 Client

Система поддерживает
аутентификацию через внешних
OAuth2 Provider.

На текущем этапе
поддерживаются GitHub и VK.

Архитектура допускает подключение
других OAuth2 Provider
без изменения Business Domain Model.

## Назначение

Предоставить пользователю возможность
входа без создания локального пароля.

## Основные принципы

OAuth2 Client:

- используется только
  для аутентификации;
- не заменяет механизм авторизации;
- после успешного входа пользователь
  получает внутренний Security Context;
- не проникает в Domain Model.

## Поддержка новых Provider

Добавление нового Provider
не требует изменения Domain Layer.

Подключение выполняется
на Security / Infrastructure level.

---

## OAuth2 Authentication

### OAuth2 providers

Поддерживаются:

```text
GitHub
VK
```

Используется общий OAuth2 authentication flow.

### OAuth2 flow

```text
GitHub / VK
    ↓
Authorization Code + PKCE
    ↓
Spring Security OAuth2 Client
    ↓
provider-specific identity
    ↓
ExternalIdentityData
    ↓
ExternalIdentityAuthenticationService
    ↓
User resolution / creation
    ↓
AuthenticatedUser
    ↓
SecurityContext
    ↓
existing JWT + Refresh Token
```

### OAuth2 boundary

- OAuth2 находится в Security/Infrastructure boundary;
- `OAuth2User` не передается в Domain;
- Spring Security OAuth2 types не входят в Domain;
- provider-specific response не входит в Business Domain Model;
- Domain User не получает `githubId`;
- Domain User не получает `vkId`;
- Domain Model не получает OAuth2-specific entity.

### External identity

```text
User 1:N ExternalIdentity
```

`ExternalIdentity` является Security/Persistence concept.

Уникальность:

```text
(provider, provider_subject)
(user_id, provider)
```

### Provider-neutral model

`ExternalIdentityData` содержит:

```text
provider
subject
email
emailVerified
```

Provider-specific mapping выполняется отдельно:

```text
GitHubIdentityMapper
VkIdentityMapper
```

Оба преобразуют данные провайдера в `ExternalIdentityData`.

### User resolution

Порядок разрешения пользователя:

```text
OAuth2 success
    ↓
find ExternalIdentity by provider + subject
    ↓
если найдена → получить User
    ↓
проверить active
    ↓
AuthenticatedUser
```

Если identity не найдена:

```text
validate usable email
    ↓
create User
    ↓
create ExternalIdentity
    ↓
AuthenticatedUser
```

Создание User и ExternalIdentity должно быть атомарным.

### Создание нового OAuth2 User

```text
role = USER
isActive = true
passwordHash = NULL
email = provider email
```

`nickname`, `about`, `avatar` автоматически из OAuth2-профиля не синхронизируются.

### Email

- email необходим при первом создании User;
- если usable email отсутствует — новый User не создается;
- отсутствие email не приводит к изменению Domain Model;
- совпадение OAuth2 email с существующим Local User не приводит к автоматическому связыванию аккаунтов.

### Existing Local User

```text
OAuth2 email == existing User email
```

не является основанием для автоматического linking.

Связывание Local User и внешней identity относится к отдельной будущей функциональности.

### Existing External Identity

Если:

```text
(provider, provider_subject)
```

уже существует:

- новый User не создается;
- identity не переносится другому User;
- разрешается существующий User;
- проверяется `isActive`;
- при inactive User authentication отклоняется.

### JWT

OAuth2 после успешной аутентификации использует существующую JWT-модель.

JWT claims не изменяются.

Не добавлять:

```text
provider
email
role
```

в JWT.

`sub` продолжает содержать:

```text
User.id
```

### Refresh Token

OAuth2 authentication использует существующую модель Refresh Token и Rotation Family.

Не создается отдельная OAuth2-модель Refresh Token.

ADR-009, ADR-015 и ADR-016 остаются действующими.

### Account Linking

```text
Account linking не входит в TASK 32.
```

Не реализуется автоматическое связывание:

```text
Local User ↔ GitHub
Local User ↔ VK
```

Это отдельная будущая функциональность.

### Provider-specific rules

Для GitHub:

- используется стабильный provider subject;
- email не является identity key;
- для получения подтвержденного email используется соответствующий GitHub scope;
- provider login не используется как identity key.

Для VK:

- используется стабильный идентификатор пользователя VK;
- email может отсутствовать;
- при отсутствии usable email первый User не создается.

---

## Security Hardening

Security Hardening выполняется после завершения базовых механизмов:

- Local Authentication;
- JWT Authentication;
- Refresh Token;
- Authorization;
- OAuth2 Authentication.

TASK 33 усиливает существующую Security implementation и не изменяет установленную Security Model.

### Security Representation

Security representation пользователя должна содержать только данные, необходимые для authentication и authorization.

Запрещается сохранять или передавать через Security representation:

- raw password;
- passwordHash;
- Refresh Token;
- Refresh Token hash;
- OAuth2 access token, если он больше не требуется для authentication;
- provider credentials;
- иные секретные значения.

После успешной authentication credentials должны быть очищены и не должны оставаться в Authentication или SecurityContext.

Security representation не должна раскрывать чувствительные данные через:

- `toString()`;
- исключения;
- логи;
- диагностические сообщения.

### JWT Hardening

JWT остаётся Security representation согласно ADR-015.

В рамках TASK 33 не изменяются:

- JWT claims;
- `sub = User.id`;
- отсутствие role claim;
- JWT validation model;
- Access Token model.

Hardening проверяет безопасную обработку JWT:

- validation;
- expiration;
- issuer;
- audience;
- signature;
- required claims;
- обработку недействительных токенов;
- отсутствие утечки JWT в логах и исключениях.

### Refresh Token Hardening

Refresh Token остаётся opaque token и не является частью Domain Model.

Существующая модель сохраняется:

- hash-only persistence;
- expiration;
- revocation;
- rotation;
- Rotation Family;
- replay detection;
- family-wide revocation;
- concurrent rotation protection.

TASK 33 не изменяет модель Refresh Token, Rotation Family или replay protection.

Hardening проверяет:

- безопасную обработку raw token;
- отсутствие raw token в persistence;
- отсутствие raw token и hash в логах;
- корректную обработку expired token;
- корректную обработку revoked token;
- корректную обработку replay;
- безопасное поведение при concurrent refresh.

### Security Configuration

Security-critical configuration должна проходить проверку при запуске приложения.

К security-critical configuration относятся:

- JWT signing configuration;
- JWT issuer;
- JWT audience;
- Access Token expiration;
- Refresh Token expiration;
- OAuth2 client credentials;
- другие секреты, необходимые для Security infrastructure.

Некорректная обязательная security configuration не должна приводить к запуску приложения в неизвестном или небезопасном состоянии.

### Secrets

Секреты не должны находиться:

- в исходном коде;
- в Domain Model;
- в persistence entities;
- в Git;
- в логах;
- в исключениях;
- в диагностических сообщениях.

Секреты должны передаваться через конфигурацию инфраструктуры.

### Security Error Handling

Ошибки Security должны иметь безопасное внешнее представление.

Запрещается раскрывать:

- наличие или отсутствие пользователя;
- password verification details;
- JWT validation internals;
- Refresh Token state;
- token hash;
- OAuth2 provider credentials;
- OAuth2 access token;
- внутренние stack traces.

### Security Diagnostics

Security-события могут логироваться для диагностики, но диагностическая информация не должна содержать credentials или другие секретные данные.

Допустимо фиксировать факт события:

- authentication failure;
- authorization failure;
- JWT rejection;
- Refresh Token rejection;
- Refresh Token replay detection;
- OAuth2 authentication failure.

При этом сами credentials и токены в журнал не записываются.

### Web Security Policy

TASK 33 определяет требования к будущей Web Security boundary, но не реализует полноценный REST/Web слой.

В текущем проекте отсутствуют:

- REST Authentication API;
- HTTP OAuth2 callback integration;
- HTTP response model;
- redirect model;
- SecurityFilterChain для будущей Web integration.

Поэтому TASK 33 не создаёт искусственный Web слой.

CSRF, CORS и Security Headers рассматриваются как Web Security policy.

Их непосредственная реализация выполняется вместе с соответствующей Web/REST integration.

### Scope Restrictions

TASK 33 не включает:

- изменение Domain Model;
- изменение Database Schema;
- Liquibase migrations;
- изменение JWT claims;
- добавление role claim;
- изменение Refresh Token model;
- token blacklist;
- изменение Rotation Family;
- изменение replay protection;
- отдельную модель Security Session;
- немедленную инвалидацию Access Token при изменении роли;
- REST Authentication API;
- HTTP OAuth2 callback integration;
- endpoint-specific authorization rules;
- Account Linking;
- OAuth2 profile synchronization.

Если для решения одной из этих задач потребуется новое архитектурное решение, implementation TASK 33 останавливается до его отдельного согласования.

---

# Password Security

Для локальной аутентификации
используются только безопасные
методы хранения паролей.

## Основные принципы

Пароль:

- никогда не хранится
  в открытом виде;
- никогда не возвращается API;
- никогда не записывается
  в журнал событий.

## Хеширование

Пароли хранятся
в виде криптографического hash.

Используемый алгоритм должен:

- поддерживаться Spring Security;
- считаться актуальным
  на момент реализации;
- соответствовать текущим
  security recommendations.

---

# Cookies

При использовании Cookie
должны применяться
актуальные рекомендации
по безопасности.

## Основные принципы

Cookie:

- имеют ограниченную область действия;
- используют HttpOnly там,
  где это применимо;
- используют Secure при HTTPS;
- используют актуальную политику SameSite.

---

# Security Headers

Приложение должно использовать
актуальные HTTP security headers.

## Рекомендуемые механизмы

- Content Security Policy;
- X-Content-Type-Options;
- Referrer Policy;
- Permissions Policy;
- Strict-Transport-Security
  при использовании HTTPS.

Конкретный набор headers
может изменяться по мере развития
security standards.

Архитектура не должна зависеть
от конкретных значений headers.

---

# Secrets Management

Все секреты приложения
должны храниться
вне исходного кода.

## К секретам относятся

- JWT Secret;
- OAuth2 Client Secret;
- SMTP Credentials;
- MinIO Credentials;
- параметры подключения к базе данных;
- другие конфиденциальные данные.

## Основные принципы

Секреты:

- не хранятся в Git;
- не размещаются в исходном коде;
- не фиксируются в журнале событий.

## Источники конфигурации

Архитектура допускает:

- переменные окружения;
- внешние хранилища секретов;
- специализированные системы управления секретами.

Конкретный способ зависит
от среды выполнения.

---

# Security Error Handling

Security errors
не должны раскрывать
лишнюю внутреннюю информацию.

API должен возвращать
безопасные security responses.

Подробная диагностическая информация
должна оставаться
внутри server-side diagnostics
и logging policy.

## Notification Delivery Logging

Notification Delivery и Retry Processing не должны раскрывать пользовательские данные или секреты через журналы.

В логах Retry Processing и Scheduler не должны передаваться:

* полный `Throwable`;
* полный текст исключения;
* destination;
* полный текст Notification;
* SMTP credentials;
* access tokens;
* refresh tokens.

Для диагностики используются:

* идентификатор `NotificationDelivery`;
* безопасный технический код ошибки;
* класс технического исключения без его сообщения, если это необходимо.

`NotificationDelivery.toString()` и `DeliveryAttempt.toString()` не должны раскрывать связанные объекты, которые могут содержать destination или полный текст Notification.


---

# Security Architecture Rules

1. Security-specific concepts
   не являются Business Domain Entities.
2. Domain Model не зависит
   от Spring Security.
3. Domain Model не зависит
   от JWT.
4. Domain Model не зависит
   от OAuth2.
5. Domain Model не зависит
   от token storage.
6. Refresh Token не является
   Domain Entity.
7. Refresh Token persistence
   реализуется в Security Phase.
8. Security infrastructure
   является заменяемой Infrastructure.
9. Security не должна изменять
   Domain Model без отдельного
   архитектурного решения.
10. Domain User не содержит passwordHash.
11. Domain User не реализует UserDetails.
12. Local password hashing выполняется через PasswordEncoder.
13. Raw password не передается в Persistence.
14. Registration User + passwordHash выполняется атомарно.
15. Concurrent registration защищается Database unique constraint.
16. UserPort.register(...) является отдельной операцией регистрации.
17. UserPort.findPasswordHash(...) предоставляет credential data
    без добавления passwordHash в Domain User.
18. Authentication использует стандартный Spring Security flow.
19. AuthenticationProvider находится за пределами Domain Layer.
20. SecurityContext не является частью Business Domain Model.
21. Authorization является Security responsibility.
22. UserRole остается атрибутом Domain User.
23. Domain User не зависит от Spring Security Authorization.
24. GrantedAuthority не является частью Domain Model.
25. UserRole преобразуется Security в соответствующую GrantedAuthority.
26. RBAC и ownership являются разными проверками.
27. Endpoint authorization выполняется на Web/Security boundary.
28. Method-level authorization находится за пределами Domain.
29. JWT не получает role claim без отдельного архитектурного обоснования.
30. Authorization policy не должна без необходимости дублироваться несколькими механизмами.

---

# Реализация Security

Security implementation
выполняется поэтапно.

## TASK 29

Local Authentication.

## TASK 30 — JWT and Refresh Token Security

TASK 30 реализует Security Token Model.

### Access Token

Access Token:

- является короткоживущим JWT;
- подписывается Security infrastructure;
- содержит минимальный набор claims;
- использует `User.id` как `sub`;
- не содержит password;
- не содержит passwordHash;
- не содержит Refresh Token;
- не содержит token hash;
- не содержит Business Domain entities.

Основные claims:

- `iss`;
- `sub`;
- `aud`;
- `iat`;
- `exp`;
- `jti`.

JWT является Security representation и не является частью Domain Model.

### Authentication Integration

Authentication flow:

AuthenticationManager

→ AuthenticationProvider

→ Authentication

→ Access Token issuance

→ JWT.

Protected API flow:

Bearer Token

→ JWT validation

→ Authentication

→ SecurityContext.

Domain Model не участвует непосредственно в JWT parsing и validation.

### Refresh Token

Refresh Token:

- является opaque security credential;
- не является JWT;
- не является Domain Entity;
- не хранится в raw form;
- хранится только через `token_hash`;
- имеет `expires_at`;
- имеет `revoked_at`;
- принадлежит Rotation Family.

### Rotation Family

Каждая authentication session получает собственный `family_id`.

Пример:

R1 → R2 → R3 → R4

При успешной rotation старый Refresh Token отзывается, а новый получает тот же `family_id`.

### Replay Detection

Повторное использование уже revoked Refresh Token считается reuse.

При обнаружении reuse отзывается вся соответствующая Rotation Family.

Другие authentication sessions пользователя не затрагиваются.

### Concurrent Rotation

Rotation выполняется атомарно в Database transaction.

Только один concurrent request может успешно выполнить rotation одного Refresh Token.

JVM locks не используются как основной механизм синхронизации.

### Security Boundary

Domain Model не содержит:

- JWT;
- Access Token;
- Refresh Token;
- token hash;
- family_id;
- token expiration state;
- token revocation state;
- token rotation state;
- replay state.

Security-specific state находится за пределами Business Domain Model.

## TASK 31

Authorization.

## TASK 32

OAuth2 Authentication: GitHub + VK.

## TASK 33

Security Hardening.

---

# Связанные документы

- [00.5-GLOSSARY](00.5-GLOSSARY.md)
- [01-ARCHITECTURE](01-ARCHITECTURE.md)
- [02-DOMAIN_MODEL](02-DOMAIN_MODEL.md)
- [03-DATABASE](03-DATABASE.md)
- [TASK_PLAN](ai/TASK_PLAN.md)
- [TASK_LOG](ai/TASK_LOG.md)

---

# Связанные ADR

- [ADR-001 — Domain First Architecture](adr/ADR-001-Domain-First-Architecture.md)
- [ADR-007 — Replaceable Infrastructure](adr/ADR-007-Replaceable-Infrastructure.md)
- [ADR-009 — RefreshToken Security Boundary](<adr/ADR-009-RefreshToken Security Boundary.md>)

---

# Связанные диаграммы

- [Security Flow](diagrams/detailed/10-security-flow.puml)
- [Package Responsibility](diagrams/overview/03-package-responsibility.puml)