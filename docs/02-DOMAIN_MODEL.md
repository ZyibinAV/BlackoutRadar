# BlackoutRadar — Domain Model

> Предметная модель проекта BlackoutRadar.

---

# Назначение

Документ описывает предметную модель
и основные бизнес-правила BlackoutRadar.

Domain Model отвечает на вопрос:

> Какие объекты и правила являются частью
> предметной области BlackoutRadar?

Domain Model не описывает:

- Persistence;
- PostgreSQL;
- JPA;
- Hibernate;
- Spring;
- Spring Security;
- JWT;
- OAuth2;
- REST API;
- конкретные инфраструктурные реализации.

---

# Навигация

| Раздел | Ссылка |
|---|---|
| ⬅ Предыдущий | [01-ARCHITECTURE](01-ARCHITECTURE.md) |
| 🏠 Документация | [README](README.md) |
| ➡ Следующий | [03-DATABASE](03-DATABASE.md) |

---

# Основные принципы Domain Model

## 1. Каждая сущность имеет единственную ответственность

Ни одна сущность
не должна выполнять
обязанности другой сущности.

---

## 2. Предметная область независима от инфраструктуры

Доменные сущности
не зависят
от:

- Spring Framework;
- Spring Security;
- JPA;
- Hibernate;
- PostgreSQL;
- Liquibase;
- SMTP;
- Telegram;
- MinIO;
- других инфраструктурных технологий.

---

## 3. Все взаимодействия выполняются через бизнес-процессы

Доменные сущности
не взаимодействуют напрямую
с инфраструктурными компонентами.

---

## 4. Центром предметной области является PowerOutage

Основные бизнес-процессы
строятся вокруг обработки
событий отключения электроэнергии.

---

## 5. Адрес является каноническим объектом

Все операции
по поиску и сопоставлению
выполняются только
с использованием канонического Address.

---

## 6. История предметной области сохраняется

Завершенные события,
уведомления
и другие значимые объекты
не удаляются без необходимости.

---

# Identity

## User

### Назначение

Представляет зарегистрированного пользователя системы.

User является владельцем:

- подписок;
- адресов через Subscription;
- уведомлений через Subscription;
- пользовательских настроек.

### Ответственность

User отвечает за:

- персональные данные;
- учетную запись;
- настройки профиля;
- роль;
- состояние учетной записи.

### Не отвечает

User не отвечает за:

- поиск отключений;
- обработку событий;
- Matching;
- отправку уведомлений;
- работу парсеров;
- authentication;
- authorization;
- JWT;
- Refresh Token lifecycle.

### Жизненный цикл

Создание

↓

Подтверждение регистрации

↓

Активное использование

↓

Блокировка при необходимости

↓

Удаление / архивирование

### Основные связи

User

↓

Subscription

↓

Notification

### Основные инварианты

У пользователя:

- существует уникальный email;
- существует одна учетная запись;
- может быть несколько Subscription;
- используется одна роль из утвержденного набора ролей.

---

# Subscription

## Назначение

Представляет подписку пользователя
на мониторинг конкретного канонического адреса.

### Ответственность

Subscription отвечает за:

- пользователя;
- адрес мониторинга;
- период мониторинга;
- состояние активности;
- период доступности сервиса;
- набор TransformerStation.

### Основные связи

User

↓

Subscription

↓

Address

Subscription

↓

TransformerStation

Subscription

↓

Notification

### Notification Delivery и финальное состояние

`Notification` не содержит состояние конкретной доставки, Retry state или `processingToken`.

Конкретная доставка представлена отдельным `NotificationDelivery`.

Итоговое состояние `Notification` определяется Application / Processing Flow на основании актуального состояния всех связанных `NotificationDelivery`.

Механизм конкурентной финализации через PostgreSQL не является частью Domain Model.

Domain Model не знает о:

* PostgreSQL row lock;
* ownership token;
* fencing;
* Retry Scheduler;
* Recovery Scheduler;
* Delivery Adapter.


### Инварианты

Subscription:

- обязательно имеет User;
- обязательно имеет Address;
- имеет monitoringStart;
- имеет monitoringEnd;
- monitoringStart меньше monitoringEnd;
- может существовать без TransformerStation;
- может содержать несколько TransformerStation;
- одна TransformerStation не может быть добавлена повторно;
- serviceAccessUntil является отдельным состоянием;
- истечение serviceAccessUntil автоматически не изменяет isActive.

---

# TransformerStation

## Назначение

Представляет трансформаторную подстанцию,
связанную с Subscription
и используемую в дальнейшем Matching.

### Ответственность

TransformerStation отвечает только за:

- identity;
- название подстанции.

### Не отвечает

TransformerStation не содержит:

- Matching logic;
- outage processing;
- notification logic;
- persistence logic.

---

# Address Catalog

Подсистема Address Catalog
отвечает за хранение,
нормализацию
и идентификацию адресов.

Все остальные подсистемы
используют только канонические адреса.

---

## Region

Административный регион.

---

## RegionalDistrict

Административный район региона.

Связь с Region обязательна.

---

## City

Населенный пункт.

City принадлежит одному Region.

City может иметь:

- RegionalDistrict;
- CityDistrict;
- Street.

---

## CityDistrict

Район города.

CityDistrict принадлежит одному City.

CityDistrict является optional
для Address.

---

## Street

Улица внутри City.

Street принадлежит одному City.

Street не принадлежит CityDistrict.

---

## House

Value Object,
представляющий номер дома.

House содержит:

- houseNumber;
- houseAddition;
- canonicalHouse.

House является immutable.

---

## Address

Каноническое представление
конкретного адреса.

Address содержит:

- Street;
- optional CityDistrict;
- House.

### Инварианты

Address:

- всегда имеет Street;
- всегда имеет House;
- может иметь CityDistrict;
- CityDistrict должен принадлежать тому же City,
  что и Street.

Persistence-specific `city_id`
не является частью Domain Model.

---

# Outage

## Source

Представляет источник,
из которого система получает информацию
об отключениях электроэнергии.

Source содержит:

- identity;
- name;
- sourceType;
- providerType;
- configuration;
- schedule;
- active state.

Source не отвечает за обработку
полученных событий.

---

## PowerOutage

Центральная доменная сущность
обработки отключения электроэнергии.

PowerOutage содержит:

- identity;
- Source;
- startTime;
- endTime;
- reason;
- status;
- связанные адреса.

### Инварианты

PowerOutage:

- имеет Source;
- имеет минимум один Address;
- startTime меньше endTime.

PowerOutage является основным объектом
Outage Processing Pipeline.

---

## PowerOutageAddress

Представляет связь
между PowerOutage и Address.

Может дополнительно содержать
TransformerStation.

---

## Candidate

Временный результат CandidateFinder.

Candidate не является самостоятельным
persisted Business Domain Entity.

Candidate реализуется в рамках Phase 6.

---

## Match

Результат Matching Engine.

Match представляет сопоставление
PowerOutage с Subscription.

Match является результатом Matching Engine
и не является частью технического Notification lifecycle.

Match не является самостоятельным persisted объектом.

Match реализуется в рамках Phase 6.

---

# Notification

## Назначение

Notification представляет необходимость
уведомить пользователя
о найденном совпадении.

Notification:

- относится к Subscription;
- относится к PowerOutage;
- имеет собственный lifecycle;
- не зависит технически от Match;
- не определяет канал доставки.

## Notification Uniqueness

Для одной пары:

Subscription
+
PowerOutage

допускается
не более одного Notification.

Это является доменным правилом.

Идемпотентность создания
обеспечивается Application /
Processing Flow.

Application перед созданием
Notification проверяет существующий
Notification через NotificationPort.

Физическая защита уникальности
обеспечивается Persistence / Database.

Notification не получает
техническую зависимость от Match.

---

## NotificationStatus

Допустимые состояния:

- PENDING;
- PROCESSING;
- SENT;
- FAILED.

---

## Notification lifecycle

PENDING

↓

PROCESSING

↓

SENT

или

PROCESSING

↓

FAILED

Retry state и DeliveryAttempt не являются частью
Domain Model сущности Notification.

NotificationDelivery и DeliveryAttempt являются
отдельными Domain Models подсистемы уведомлений.

---

## NotificationChannel

`NotificationChannel` представляет пользовательский способ
получения уведомлений.

Связь:

User
│
│ 1:N
▼
NotificationChannel

NotificationChannel содержит:

- id;
- user;
- type;
- destination;
- enabled.

`type` является расширяемым идентификатором и не фиксируется
в Domain Model перечислением конкретных каналов.

Один пользователь может иметь несколько каналов,
в том числе несколько каналов одного типа.

`NotificationChannel` является пользовательской настройкой
и не является частью `Notification`.

Notification не содержит channel,
destination или channel-specific delivery state.

Основное архитектурное решение:

ADR-011 — Notification Channels and Extensible Delivery.

---

## NotificationDelivery

`NotificationDelivery` представляет конкретную единицу доставки Notification через конкретный `NotificationChannel`.

Связь:

```text
Notification
    │
    │ 1:N
    ▼
NotificationDelivery
    │
    │ N:1
    ▼
NotificationChannel
```

`NotificationDelivery` содержит:

* `id`;
* `Notification`;
* `NotificationChannel`;
* `DeliveryStatus`;
* `nextAttemptAt`.

Допустимые состояния:

```text
READY
PROCESSING
SENT
FAILED
```

`NotificationDelivery` является текущим состоянием конкретной доставки.

Retry state относится к `NotificationDelivery`, а не к `Notification`.

`NotificationDelivery` не содержит:

* ownership token;
* destination отдельно от `NotificationChannel`;
* полный текст сообщения;
* технические сведения PostgreSQL.

Ownership token является persistence-specific механизмом и не входит в Domain Model.

## DeliveryAttempt

`DeliveryAttempt` представляет одну фактически выполненную попытку доставки.

Содержит:

* `id`;
* `NotificationDelivery`;
* `attemptNumber`;
* `startedAt`;
* `completedAt`;
* `DeliveryAttemptResult`;
* `errorCode`.

`DeliveryAttempt` является историей и не владеет текущим Retry state.

`nextAttemptAt` не является полем `DeliveryAttempt`.

## DeliveryAttemptResult

Допустимые результаты:

```text
SUCCESS
TEMPORARY_FAILURE
PERMANENT_FAILURE
```

`SUCCESS` означает успешное завершение попытки.

`TEMPORARY_FAILURE` допускает принятие Retry Decision.

`PERMANENT_FAILURE` означает, что автоматический Retry для этой попытки не выполняется.

Конкретная классификация технических ошибок выполняется Delivery Adapter.

## Domain Boundary

`Notification` сохраняет собственный lifecycle:

```text
PENDING
    ↓
PROCESSING
    ↓
SENT
```

или:

```text
PROCESSING
    ↓
FAILED
```

`Notification` не получает:

* Retry count;
* nextAttemptAt;
* DeliveryAttempt;
* NotificationChannel;
* ownership token.

Retry является частью Notification Processing, но не новым бизнес-состоянием Notification.


---

# Security Boundary

Security concepts
не являются частью Business Domain Model.

К ним относятся:

- Authentication;
- Authorization;
- Access Token;
- JWT;
- Refresh Token;
- OAuth2 Client;
- Password Security.

В частности:

> Refresh Token не является Domain Entity.

Refresh Token используется
для продолжения authentication session
и получения нового Access Token.

Его lifecycle:

- issuance;
- expiration;
- revocation;
- rotation

является Security concern.

Refresh Token реализуется
в Security Phase
и не входит в Domain Model.

Domain Model не содержит:

- RefreshToken Entity;
- tokenHash;
- raw refresh token;
- JWT claims;
- SecurityContext;
- OAuth2-specific state.

---

# Domain Entity Semantics

Все Domain Entities используют UUID identity.

Equality определяется
через identity.

Domain Entities:

- immutable, где это предусмотрено моделью;
- не используют JPA annotations;
- не используют Spring annotations;
- не используют MapStruct annotations;
- не используют Lombok.

---

# Связанные документы

- [00-VISION](00-VISION.md)
- [00.5-GLOSSARY](00.5-GLOSSARY.md)
- [01-ARCHITECTURE](01-ARCHITECTURE.md)
- [03-DATABASE](03-DATABASE.md)
- [04-PARSER](04-PARSER.md)
- [05-MATCHING_ENGINE](05-MATCHING_ENGINE.md)
- [06-NOTIFICATION_ENGINE](06-NOTIFICATION_ENGINE.md)
- [07-SECURITY](07-SECURITY.md)

---

# Связанные ADR

- [ADR-001 — Domain First Architecture](adr/ADR-001-Domain-First-Architecture.md)
- [ADR-002 — Canonical Address Model](adr/ADR-002-Canonical-Address-Model.md)
- [ADR-003 — Outage Processing Pipeline](adr/ADR-003-Outage-Processing-Pipeline.md)
- [ADR-005 — PowerOutage Event Model](adr/ADR-005-PowerOutage-Event-Model.md)
- [ADR-006 — Matching Engine](adr/ADR-006-Matching-Engine.md)
- [ADR-007 — Replaceable Infrastructure](adr/ADR-007-Replaceable-Infrastructure.md)
- [ADR-009 — RefreshToken Security Boundary](<adr/ADR-009-RefreshToken Security Boundary.md>)
- [ADR-011 — Notification Channels and Extensible Delivery](<adr/ADR-011-Notification Channels and Extensible Delivery.md>)
- [ADR-012 — Retry and Delivery Attempt Processing](<adr/ADR-012-Retry and Delivery Attempt Processing.md>)
- [ADR-013 — Retry Policy, Fencing and Recovery](<adr/ADR-013 — Retry Policy, Fencing and Recovery.md>)
- [ADR-014 — Concurrent Notification Finalization](adr/ADR-014 — Concurrent Notification Finalization.md)


---

# Связанные диаграммы

- [Domain Model](diagrams/detailed/05-domain-model.puml)
- [Package Responsibility](diagrams/overview/03-package-responsibility.puml)
- [Security Flow](diagrams/detailed/10-security-flow.puml)