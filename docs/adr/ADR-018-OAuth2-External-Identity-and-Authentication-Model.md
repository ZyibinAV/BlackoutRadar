# ADR-018 — OAuth2 External Identity and Authentication Model

## Статус

**Accepted**

## Дата

2026-09-22

## Контекст

BlackoutRadar поддерживает локальную аутентификацию и существующую Security-модель:

```text
Local Authentication
    ↓
AuthenticatedUser
    ↓
JWT Access Token
    ↓
Refresh Token
```

Необходимо добавить аутентификацию через внешних OAuth2 Provider.

На текущем этапе поддерживаются:

- GitHub;
- VK.

OAuth2 является Security / Infrastructure concern и не должен проникать в Business Domain Model.

Необходимо определить:

- границу OAuth2;
- представление внешней identity;
- связь внешней identity с Domain User;
- правила первого и повторного входа;
- правила работы с email;
- создание OAuth-пользователя;
- совместимость с JWT и Refresh Token;
- влияние на Database.

---

## Решение

### 1. OAuth2 Boundary

OAuth2 реализуется исключительно в Security / Infrastructure.

Используется:

```text
Authorization Code + PKCE
```

Spring Security OAuth2 Client является техническим механизмом реализации.

`OAuth2User` и другие Spring Security OAuth2-типы не должны попадать в Domain Model.

Общий поток:

```text
GitHub / VK
    ↓
Authorization Code + PKCE
    ↓
Spring Security OAuth2 Client
    ↓
Provider-specific identity
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
существующий JWT + Refresh Token flow
```

---

### 2. External Identity

Внешняя identity не является частью Domain User.

Используется отдельная Security / Persistence модель:

```text
ExternalIdentity
```

Связь:

```text
User 1 ─── N ExternalIdentity
```

Одна внешняя identity принадлежит только одному User.

Один User может иметь не более одной identity одного Provider.

---

### 3. ExternalIdentityData

После получения данных от Provider они преобразуются в общий внутренний формат:

```text
ExternalIdentityData
├── provider
├── subject
├── email
└── emailVerified
```

Provider-specific данные не передаются дальше этого слоя.

Для каждого Provider используется отдельный mapper:

```text
GitHub OAuth2 response
    ↓
GitHubIdentityMapper
    ↓
ExternalIdentityData
```

```text
VK OAuth2 response
    ↓
VkIdentityMapper
    ↓
ExternalIdentityData
```

---

### 4. Provider Subject

`subject` является стабильным идентификатором пользователя у конкретного Provider.

Email не используется как идентификатор внешней identity.

Не допускается использование:

- GitHub login;
- email;
- других изменяемых атрибутов

в качестве `provider_subject`.

---

### 5. Database Constraints

Таблица `external_identity` должна иметь ограничения:

```text
UNIQUE(provider, provider_subject)
UNIQUE(user_id, provider)
```

Они обеспечивают:

- невозможность привязать одну внешнюю identity к нескольким User;
- невозможность привязать несколько identity одного Provider к одному User;
- защиту от конкурентного создания одинаковой identity.

---

### 6. Первый OAuth2 Login

Если:

```text
provider + subject
```

не найден в `external_identity`:

1. проверяется наличие необходимого email;
2. проверяется допустимость email;
3. создаётся новый Domain User;
4. создаётся ExternalIdentity;
5. операции выполняются атомарно;
6. создаётся `AuthenticatedUser`.

Новый OAuth2 User получает:

```text
role = USER
isActive = true
passwordHash = NULL
```

Профильные поля User автоматически из OAuth2-профиля не синхронизируются.

---

### 7. Email

Email используется только для создания нового User.

Email не используется для поиска существующего OAuth2 User.

Если необходимый email отсутствует:

```text
OAuth2 authentication
    ↓
reject
```

User и ExternalIdentity не создаются.

Для GitHub используется подтверждённый основной email.

---

### 8. Existing Local User

Автоматическое связывание OAuth2 identity с существующим Local User по email запрещено.

Например:

```text
Local User:
user@example.com

GitHub:
user@example.com
```

не является основанием для автоматического linking.

Связывание аккаунтов является отдельной будущей функциональностью.

---

### 9. Repeat Login

Если `ExternalIdentity` найдена:

```text
ExternalIdentity
    ↓
User
    ↓
active check
    ↓
AuthenticatedUser
```

Email Provider при этом не используется для поиска User.

OAuth2 login не должен автоматически изменять:

- email;
- role;
- nickname;
- about;
- avatar;
- другие данные User.

---

### 10. Identity Reassignment

Существующая ExternalIdentity не может автоматически быть переназначена другому User.

Если:

```text
provider + subject
```

уже связан с User A, он не может быть связан с User B обычным OAuth2 login.

---

### 11. OAuth2 User Creation

Существующий:

```text
UserPort.register(User, passwordHash)
```

используется только для Local Authentication.

OAuth2 first login использует отдельный путь создания:

```text
OAuth2
    ↓
User creation
    +
ExternalIdentity creation
```

Domain User остаётся тем же существующим Domain Entity.

Новый Domain Entity для OAuth2 не вводится.

---

### 12. JWT

После успешной OAuth2 authentication используется существующий JWT механизм.

JWT contract не изменяется:

```text
iss
sub
aud
iat
exp
jti
```

```text
sub = User.id
```

В JWT не добавляются:

- provider;
- email;
- OAuth2 subject;
- role.

---

### 13. Refresh Token

OAuth2 authentication использует существующий Refresh Token механизм.

Используются существующие:

- Refresh Token;
- Rotation Family;
- expiration;
- revocation;
- replay protection.

Отдельная OAuth2 refresh-token модель не создаётся.

---

### 14. Domain Boundary

Domain Model не содержит:

- OAuth2;
- OAuth2User;
- GitHub account;
- VK account;
- ExternalIdentity;
- provider-specific identifiers;
- Access Token;
- Refresh Token;
- SecurityContext.

OAuth2 полностью остаётся за пределами Business Domain Model.

---

### 15. Account Linking

Account linking:

```text
Local User ↔ OAuth2 identity
```

не входит в TASK 32.

Это отдельная будущая функциональность.

---

## Последствия

### Положительные

- Domain Model не зависит от OAuth2;
- GitHub и VK используют общий механизм;
- добавление нового Provider не требует изменения Domain;
- email не используется как внешний идентификатор;
- существующая JWT-модель сохраняется;
- существующая Refresh Token модель сохраняется;
- Database constraints защищают identity от конфликтов;
- account linking можно реализовать отдельно.

### Ограничения

- OAuth2 User без необходимого email не может быть создан;
- автоматическое linking по email отсутствует;
- профиль User не синхронизируется автоматически с Provider;
- для нового Provider требуется provider-specific mapper.

---

## Связанные решения

- ADR-001 — Domain First Architecture
- ADR-007 — Replaceable Infrastructure
- ADR-009 — RefreshToken Security Boundary
- ADR-015 — JWT Access Token and Security Token Model
- ADR-016 — Refresh Token Rotation Family and Replay Protection
- ADR-017 — Authorization Model