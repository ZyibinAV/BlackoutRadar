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
используется GitHub OAuth2.

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

---

# Реализация Security

Security implementation
выполняется поэтапно.

## TASK 29

Local Authentication.

## TASK 30

JWT and Refresh Token Security.

Включает:

- Access Token;
- JWT;
- Refresh Token;
- secure storage;
- expiration;
- revocation;
- rotation;
- authentication integration.

## TASK 31

Authorization.

## TASK 32

GitHub OAuth2.

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