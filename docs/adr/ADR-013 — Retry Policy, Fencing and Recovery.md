# ADR-013 — Retry Policy, Fencing and Recovery

**Status:** Accepted

**Date:** 2026-09-12

---

# Context

ADR-012 определил архитектурную модель Retry:

```text
Notification
    │
    │ 1:N
    ▼
NotificationDelivery
    │
    │ 1:N
    ▼
DeliveryAttempt
```

`NotificationDelivery` является единицей конкретной доставки через конкретный `NotificationChannel`, а `DeliveryAttempt` хранит историю отдельных попыток.

ADR-012 также определил:

* Retry Policy как отдельный Application-компонент;
* `nextAttemptAt` как состояние `NotificationDelivery`;
* атомарный claim на уровне `NotificationDelivery`;
* необходимость восстановления зависших `PROCESSING`;
* отсутствие гарантии ровно одной внешней доставки;
* Retry Scheduler как инфраструктурный механизм без бизнес-правил Retry.

При реализации TASK 27 были приняты дополнительные решения, необходимые для безопасной работы Retry при:

* параллельной обработке;
* нескольких экземплярах приложения;
* аварийном завершении приложения;
* повторной доставке после временной ошибки;
* восстановлении зависшей обработки;
* конкуренции между старым и новым владельцем одной `NotificationDelivery`.

Основная проблема recovery состоит в том, что простой переход:

```text
PROCESSING → READY
```

недостаточен.

Возможен сценарий:

```text
Worker A
    ↓
claim
    ↓
PROCESSING
    ↓
DeliveryAttempt #1
    ↓
внешняя доставка

Recovery
    ↓
PROCESSING → READY

Worker B
    ↓
claim
    ↓
PROCESSING
    ↓
DeliveryAttempt #2
    ↓
успешная доставка

Worker A
    ↓
завершает старую обработку
```

Без дополнительной защиты Worker A может перезаписать состояние, принадлежащее Worker B.

Следовательно, необходим механизм, который не позволяет устаревшему владельцу изменить состояние после потери владения.

---

# Decision

## 1. Retry выполняется на уровне NotificationDelivery

Retry не изменяет модель и lifecycle `Notification`.

Единицей повторной обработки является:

```text
NotificationDelivery
```

Она однозначно связывает:

```text
Notification
        +
NotificationChannel
```

Для одной `Notification` могут существовать несколько независимых `NotificationDelivery`.

Например:

```text
Notification
    │
    ├── NotificationDelivery → email/user1
    │
    ├── NotificationDelivery → email/user2
    │
    └── NotificationDelivery → telegram/user1
```

Каждая доставка имеет собственное состояние и собственную историю попыток.

---

## 2. DeliveryAttempt является историей, а не владельцем Retry-состояния

`DeliveryAttempt` хранит факт конкретной попытки:

```text
id
notificationDelivery
attemptNumber
startedAt
completedAt
result
errorCode
```

`DeliveryAttempt` не содержит:

* `nextAttemptAt`;
* состояние Retry;
* destination;
* полный текст Notification;
* секреты.

Текущее состояние Retry хранится только в `NotificationDelivery`.

Таким образом:

```text
NotificationDelivery
    ├── status
    └── nextAttemptAt

DeliveryAttempt
    └── история выполненных попыток
```

---

## 3. Retry Policy является отдельным Application-компонентом

Решение о повторной попытке принимает `RetryPolicy`.

Общая схема:

```text
DeliveryResult
      ↓
RetryPolicy
      ↓
RetryDecision
```

`RetryPolicy` определяет:

* разрешён ли повтор;
* время следующей попытки;
* исчерпаны ли попытки.

`RetryPolicy` не зависит от:

* JPA;
* PostgreSQL;
* Delivery Adapter;
* Scheduler;
* конкретного способа хранения.

Delivery Adapter только классифицирует технический результат доставки.

---

## 4. Зафиксирована классификация результата доставки

Результат доставки имеет три состояния:

```text
SUCCESS
TEMPORARY_FAILURE
PERMANENT_FAILURE
```

Правила:

```text
SUCCESS
    → NotificationDelivery = SENT

PERMANENT_FAILURE
    → NotificationDelivery = FAILED

TEMPORARY_FAILURE
    → RetryPolicy
```

Если Retry Policy разрешает повтор:

```text
PROCESSING
    ↓
READY
    +
nextAttemptAt
```

Если повтор запрещён:

```text
PROCESSING
    ↓
FAILED
```

`Notification` при этом не получает новый Retry-статус.

---

## 5. Зафиксирована текущая Retry Policy

Текущая реализация использует максимум **3 попытки на одну NotificationDelivery**.

Для временных ошибок:

```text
attempt #1
    ↓
retry через 1 минуту

attempt #2
    ↓
retry через 5 минут

attempt #3
    ↓
retry запрещён
    ↓
FAILED
```

Установлен максимальный предел задержки:

```text
15 минут
```

Для:

```text
SUCCESS
PERMANENT_FAILURE
```

повторная попытка не создаётся.

Для попытки с номером:

```text
attemptNumber < 1
```

работа с Retry Policy считается некорректной и отклоняется.

Jitter в текущую реализацию не входит.

---

## 6. Claim Retry выполняется атомарно в PostgreSQL

Несколько экземпляров приложения могут одновременно попытаться обработать одну `NotificationDelivery`.

Используется атомарный переход:

```text
READY
  ↓
PROCESSING
```

с проверкой:

```text
status = READY
AND
nextAttemptAt IS NULL OR nextAttemptAt <= now
```

Только один конкурентный обработчик получает право продолжить обработку.

Если claim не выполнен:

* `DeliveryAttempt` не создаётся;
* внешний Delivery Provider не вызывается;
* состояние текущего владельца не изменяется.

JVM-блокировки, `synchronized` и другие in-memory механизмы не используются.

---

## 7. Для защиты от устаревшего владельца используется ownership token

Одного атомарного claim недостаточно для безопасного recovery.

Каждый успешный claim получает уникальный:

```text
processingToken UUID
```

Токен является техническим идентификатором текущего владельца обработки.

Он хранится только в persistence-модели:

```text
notification_delivery.processing_token
```

`processingToken` не является частью Domain Model.

---

## 8. Завершение обработки выполняется только владельцем

После внешней доставки итоговое изменение `NotificationDelivery` выполняется условно:

```text
id = deliveryId
AND
status = PROCESSING
AND
processingToken = ownerToken
```

Если условие не выполнено:

```text
0 rows updated
```

означает, что обработчик потерял право изменять состояние.

В таком случае старый обработчик не имеет права записывать:

```text
SENT
FAILED
READY
```

поверх состояния нового владельца.

Таким образом реализуется fencing устаревшего владельца.

---

## 9. Recovery зависшей обработки переводит NotificationDelivery обратно в READY

При обнаружении зависшей обработки:

```text
PROCESSING
    ↓
READY
```

и:

```text
processingToken → NULL
```

При этом незавершённый `DeliveryAttempt` не закрывается искусственным результатом.

Если внешний провайдер мог уже принять сообщение, приложение не знает достоверно, была ли внешняя доставка выполнена.

Поэтому система не должна записывать ложный:

```text
SUCCESS
```

или:

```text
FAILURE
```

для незавершённой попытки.

---

## 10. Recovery применяется только к последней незавершённой попытке

Недостаточно проверить наличие любого старого незавершённого `DeliveryAttempt`.

Recovery применяется только если последняя незавершённая попытка действительно считается зависшей.

Условие:

```text
EXISTS incomplete attempt
    startedAt < stuckBefore

AND

NOT EXISTS incomplete attempt
    startedAt >= stuckBefore
```

Это предотвращает восстановление `NotificationDelivery`, если после старой незавершённой попытки уже существует более новая активная попытка.

---

## 11. После Recovery создаётся новая попытка

Recovery не создаёт новый `DeliveryAttempt`.

После перехода:

```text
PROCESSING → READY
```

следующий Worker выполняет обычный claim и только после успешного claim создаёт новую попытку.

Например:

```text
DeliveryAttempt #1
    PROCESSING
    ↓
    приложение завершилось

Recovery
    ↓
NotificationDelivery = READY

Worker B
    ↓
claim
    ↓
DeliveryAttempt #2
```

История сохраняется.

Номер попытки увеличивается:

```text
1 → 2 → 3
```

---

## 12. Recovery не закрывает незавершённую попытку

Незавершённая попытка остаётся:

```text
completedAt = NULL
result = NULL
```

Это отражает реальное состояние системы:

```text
результат внешней операции неизвестен
```

Такое поведение необходимо для корректной истории и для сохранения семантики at-least-once delivery.

---

## 13. Retry Scheduler не содержит бизнес-правил

Retry Scheduler отвечает только за поиск готовых `NotificationDelivery` и запуск Retry Processing:

```text
Retry Scheduler
      ↓
find READY NotificationDelivery
      ↓
Retry Processing
```

Scheduler не принимает решения:

* делать ли Retry;
* сколько попыток разрешено;
* какую задержку выбрать;
* является ли ошибка временной или постоянной;
* какой канал использовать.

Эти решения находятся вне Scheduler.

---

## 14. Recovery Scheduler является отдельным инфраструктурным механизмом

Recovery вынесен в отдельный Scheduler.

```text
Recovery Scheduler
      ↓
find stuck NotificationDelivery
      ↓
recoverStuck
```

Recovery Scheduler не содержит:

* Retry Policy;
* Delivery Port;
* Channel Registry;
* Delivery Adapter;
* логики создания `DeliveryAttempt`;
* логики изменения `Notification`.

Retry Scheduler и Recovery Scheduler являются независимыми механизмами:

```text
Retry Scheduler
    → READY deliveries

Recovery Scheduler
    → stuck PROCESSING deliveries
```

---

## 15. Notification lifecycle не изменяется

Lifecycle `Notification` остаётся:

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

Retry не добавляет:

```text
RETRY_PENDING
```

или другие новые статусы `Notification`.

Retry-состояние существует на уровне:

```text
NotificationDelivery
```

---

## 16. Внешняя доставка имеет семантику at-least-once

Система не гарантирует ровно одну внешнюю доставку.

Возможен сценарий:

```text
1. Adapter отправил сообщение
2. Provider принял сообщение
3. приложение завершилось
4. SUCCESS не сохранён
5. Recovery восстановил Delivery
6. сообщение отправлено повторно
```

В результате внешний провайдер может получить сообщение дважды.

Следовательно:

```text
Delivery semantics = at-least-once
```

Гарантия ровно одной внешней доставки возможна только при наличии соответствующей идемпотентности внешнего провайдера.

Введение такой идемпотентности данным ADR не фиксируется.

---

## 17. Persistence остаётся техническим слоем

`processingToken` является техническим состоянием persistence и не добавляется в Domain Model.

Persistence-модель:

```text
notification
    │
    │ 1:N
    ▼
notification_delivery
    │
    ├── notification_channel
    ├── status
    ├── next_attempt_at
    └── processing_token
         │
         │ 1:N
         ▼
delivery_attempt
```

Domain остаётся свободным от:

* JPA;
* Hibernate;
* PostgreSQL;
* Spring;
* persistence ownership token.

---

## 18. Ошибки и журналирование не должны раскрывать пользовательские данные

Retry и Delivery Processing не должны журналировать:

* полный destination;
* полный текст Notification;
* SMTP credentials;
* секреты;
* полное содержимое исключений, если оно может содержать пользовательские данные или секреты;
* `processingToken`.

Для диагностики используются безопасные технические сведения:

* `deliveryId`;
* тип операции;
* безопасный технический код ошибки;
* класс исключения без его полного сообщения.

Требование распространяется как на Delivery Adapter, так и на Retry/Recovery Scheduler.

---

# Consequences

## Положительные

* Конкурентная обработка одной `NotificationDelivery` контролируется атомарным claim.
* Несколько экземпляров приложения могут безопасно выполнять Retry.
* Устаревший Worker не может перезаписать состояние нового владельца.
* Recovery не требует блокировки внешнего провайдера.
* История попыток сохраняется отдельно от текущего состояния.
* Незавершённые внешние операции не получают искусственный результат.
* Retry Policy можно изменять независимо от Delivery Adapter.
* Retry Scheduler и Recovery Scheduler имеют простые и независимые обязанности.
* Notification Domain Model не загрязняется техническим состоянием Retry.
* Архитектура сохраняет возможность добавления новых каналов доставки.
* Семантика at-least-once явно зафиксирована.

## Отрицательные

* Появляется дополнительное техническое состояние `processingToken`.
* Retry требует отдельной модели `NotificationDelivery`.
* Recovery требует отдельного Scheduler.
* Необходимы атомарные операции persistence.
* Поведение после аварии может привести к повторной внешней доставке.
* Для полной защиты от дублей внешний провайдер должен поддерживать идемпотентность.
* Архитектура доставки становится сложнее простой схемы `Notification → Delivery Adapter`.

---

# Explicitly Out of Scope

Данный ADR не фиксирует:

* интеграцию Match → Notification → Notification Engine → Retry Processing;
* Telegram Delivery Adapter;
* HTTP Delivery Adapter;
* идемпотентность конкретного внешнего провайдера;
* dead-letter механизм;
* административный Retry;
* отдельный Retry API;
* метрики и мониторинг Retry;
* изменение Notification lifecycle;
* новые статусы Notification;
* хранение destination в DeliveryAttempt;
* хранение полного текста Notification в DeliveryAttempt;
* изменение правил выбора `NotificationChannel`;
* распределённые блокировки поверх PostgreSQL;
* отмену уже выполняющегося внешнего запроса;
* гарантию ровно одной внешней доставки.

---

# Related Decisions

* ADR-001 — Domain First Architecture
* ADR-003 — Outage Processing Pipeline
* ADR-007 — Replaceable Infrastructure
* ADR-011 — Notification Channels and Extensible Delivery
* ADR-012 — Retry and Delivery Attempt Processing

ADR-012 определяет общую модель Retry и Delivery Attempt.

ADR-013 фиксирует конкретные архитектурные решения, принятые при реализации TASK 27:

```text
Retry Policy
        ↓
NotificationDelivery
        ↓
Atomic Claim
        ↓
Ownership Token / Fencing
        ↓
DeliveryAttempt
        ↓
Recovery
```

Эти решения являются уточнением ADR-012 и не заменяют его.

---

# Implementation Result

В рамках TASK 27 реализованы:

* `NotificationDelivery`;
* `DeliveryAttempt`;
* `DeliveryStatus`;
* `DeliveryAttemptResult`;
* `DeliveryResult` с классификацией результата;
* `RetryPolicy`;
* `RetryDecision`;
* `FixedRetryPolicy`;
* `RetryProcessingService`;
* атомарный claim `NotificationDelivery`;
* `processingToken`;
* fenced state update;
* recovery зависших доставок;
* `RetryScheduler`;
* `RecoveryScheduler`;
* persistence для `notification_delivery`;
* persistence для `delivery_attempt`;
* Liquibase changesets;
* интеграционные проверки конкурентной обработки и recovery.

Архитектура Retry после TASK 27:

```text
Notification
      │
      │ 1:N
      ▼
NotificationDelivery
      │
      ├── NotificationChannel
      │
      ├── Retry State
      │
      ├── Ownership Token
      │
      └── DeliveryAttempt
              │
              │ 1:N
              ▼
        Attempt History


Retry Scheduler
      ↓
Retry Processing
      ↓
Atomic Claim
      ↓
DeliveryAttempt
      ↓
Delivery Adapter


Recovery Scheduler
      ↓
Stuck Detection
      ↓
Fenced Recovery
      ↓
READY
```

Notification lifecycle при этом остаётся независимым от Retry lifecycle.
