# ADR-012 — Retry and Delivery Attempt Processing

**Status:** Accepted

**Date:** 2026-09-11

---

# Context

Notification Engine отвечает за обработку и доставку созданного `Notification`.

Текущий lifecycle `Notification`:

```text
PENDING
   ↓
PROCESSING
   ↓
SENT

PROCESSING
   ↓
FAILED
```

`PROCESSING` означает, что Notification Engine принял Notification в обработку.

Notification не содержит технические сведения о доставке, Retry или конкретном канале.

Текущая модель Notification хранит:

* `id`;
* `subscription_id`;
* `power_outage_id`;
* `message`;
* `status`;
* `created_at`;
* `updated_at`.

После TASK27A переход:

```text
PENDING → PROCESSING
```

защищён атомарным claim в PostgreSQL. Это предотвращает одновременную обработку одной PENDING Notification несколькими экземплярами приложения.

Notification может иметь несколько включённых `NotificationChannel`, в том числе несколько каналов одного типа.

Например:

```text
User
│
├── NotificationChannel(email, user1@example.com)
├── NotificationChannel(email, user2@example.com)
└── NotificationChannel(telegram, @user1)
```

Поэтому для Retry недостаточно идентифицировать канал только через `channel_type`.

Для полноценного Retry необходимо определить:

* конкретную единицу доставки;
* связь единицы доставки с конкретным `NotificationChannel`;
* историю отдельных попыток;
* результат каждой попытки;
* повторную обработку конкретного канала;
* временные и постоянные ошибки;
* время следующей попытки;
* ограничение количества попыток;
* конкурентный захват Retry;
* восстановление после аварийного завершения приложения.

---

# Decision

## 1. NotificationDelivery является отдельной моделью

Между `Notification` и `DeliveryAttempt` вводится отдельная модель `NotificationDelivery`.

Итоговая структура:

```text
Notification
    │
    │ 1:N
    ▼
NotificationDelivery
    │
    ├── NotificationChannel
    │
    │ 1:N
    ▼
DeliveryAttempt
```

Ответственности моделей:

### Notification

Представляет бизнес-потребность в уведомлении.

### NotificationDelivery

Представляет необходимость доставить конкретное `Notification` через конкретный `NotificationChannel`.

`NotificationDelivery` является текущим состоянием обработки конкретной доставки и содержит состояние Retry.

### DeliveryAttempt

Представляет исторический факт одной фактической попытки доставки.

Таким образом:

```text
Notification
    ↓
"это уведомление необходимо доставить"

NotificationDelivery
    ↓
"это уведомление необходимо доставить через конкретный канал"

DeliveryAttempt
    ↓
"была выполнена конкретная попытка этой доставки"
```

---

# 2. Retry выполняется на уровне конкретной NotificationDelivery

Notification может иметь несколько включённых `NotificationChannel`.

Каждый конкретный канал обрабатывается независимо.

Например:

```text
Notification
│
├── NotificationDelivery → Email #1
│       └── SUCCESS
│
└── NotificationDelivery → Telegram #1
        └── TEMPORARY_FAILURE
```

При повторной обработке Telegram:

```text
Telegram #1
    ↓
RETRY
    ↓
SUCCESS
```

Email повторно не отправляется.

Единицей Retry является:

```text
NotificationDelivery
```

а не вся `Notification`.

---

# 3. NotificationDelivery однозначно идентифицирует конкретный канал

`NotificationDelivery` должен хранить ссылку на конкретный `NotificationChannel`.

Концептуально:

```text
NotificationDelivery
    ├── notification_id
    └── notification_channel_id
```

Использование только:

```text
notification_id + channel_type
```

не допускается.

Причина:

ADR-011 разрешает несколько `NotificationChannel` одного типа для одного пользователя.

Например:

```text
Notification
│
├── email → user1@example.com
└── email → user2@example.com
```

Оба канала имеют:

```text
channel_type = email
```

но являются разными конкретными каналами.

Поэтому Retry должен быть привязан именно к:

```text
notification_channel_id
```

---

# 4. NotificationDelivery хранит текущее состояние доставки

`NotificationDelivery` является текущим состоянием конкретной доставки.

Концептуально она должна позволять определить:

* какой Notification доставляется;
* через какой конкретный NotificationChannel;
* находится ли доставка в обработке;
* требуется ли Retry;
* когда разрешена следующая попытка;
* завершена ли доставка;
* завершилась ли она окончательной ошибкой.

Состояние Retry относится к `NotificationDelivery`, а не к `Notification`.

---

# 5. Состояние NotificationDelivery

Для `NotificationDelivery` вводится отдельное техническое состояние.

Концептуально:

```text
READY
   ↓
PROCESSING
   ↓
SENT

PROCESSING
   ↓
FAILED
```

`FAILED` является terminal state
и не переходит обратно в `READY`.

При временной ошибке
и разрешённом Retry:

```text
PROCESSING
   ↓
TEMPORARY_FAILURE
   ↓
READY
```

при этом:

```text
nextAttemptAt
```

определяет момент, после которого доставка может быть повторно захвачена.

Конкретные имена состояний могут быть уточнены при реализации Domain Model, но они не должны добавляться в lifecycle `Notification`.

`RETRY_PENDING` не вводится в `Notification`.

---

# 6. DeliveryAttempt хранит историю, а не текущее состояние Retry

`DeliveryAttempt` является исторической записью конкретной попытки.

Каждая фактическая попытка создаёт отдельную запись.

Например:

```text
Notification
│
└── NotificationDelivery → Telegram
      │
      ├── DeliveryAttempt #1 → TEMPORARY_FAILURE
      ├── DeliveryAttempt #2 → TEMPORARY_FAILURE
      └── DeliveryAttempt #3 → SUCCESS
```

История попыток не должна изменяться для отражения будущего Retry.

Следовательно, `DeliveryAttempt` не является владельцем текущего состояния Retry.

Текущее состояние и `nextAttemptAt` принадлежат `NotificationDelivery`.

---

# 7. Attempt Number

`attemptNumber` нумеруется отдельно для каждой `NotificationDelivery`.

То есть фактически номер относится к:

```text
Notification + NotificationChannel
```

Например:

```text
Notification #1
│
├── Email #1
│     └── attempt 1
│
├── Email #2
│     └── attempt 1
│
└── Telegram #1
      ├── attempt 1
      ├── attempt 2
      └── attempt 3
```

Разные конкретные каналы одного типа имеют независимую нумерацию.

---

# 8. Минимальная модель NotificationDelivery

`NotificationDelivery` должна содержать только состояние конкретной доставки и управления Retry.

Концептуально определяются:

| Поле                      | Назначение                                     |
| ------------------------- | ---------------------------------------------- |
| `id`                      | идентификатор единицы доставки                 |
| `notification_id`         | Notification                                   |
| `notification_channel_id` | конкретный NotificationChannel                 |
| `status`                  | текущее состояние доставки                     |
| `next_attempt_at`         | время, после которого разрешён следующий Retry |
| `created_at`              | время создания                                 |
| `updated_at`              | время изменения                                |

Конкретные типы полей и технические ограничения определяются во время реализации persistence.

---

# 9. Минимальная модель DeliveryAttempt

`DeliveryAttempt` содержит только сведения, необходимые для истории фактических попыток.

Концептуально определяются:

| Поле                       | Назначение                        |
| -------------------------- | --------------------------------- |
| `id`                       | идентификатор попытки             |
| `notification_delivery_id` | конкретная NotificationDelivery   |
| `attempt_number`           | номер попытки                     |
| `started_at`               | начало попытки                    |
| `completed_at`             | завершение попытки                |
| `result`                   | результат попытки                 |
| `error_code`               | безопасный технический код ошибки |

`DeliveryAttempt` не содержит:

* `next_attempt_at`;
* текущий Retry status;
* destination;
* полный текст Notification;
* секреты.

`nextAttemptAt` принадлежит `NotificationDelivery`.

---

# 10. Destination не сохраняется в DeliveryAttempt

`NotificationChannel.destination()` не дублируется в истории попыток.

Причины:

* destination является пользовательскими данными;
* DeliveryAttempt не должен создавать дополнительную копию PII;
* destination может измениться после попытки;
* для Retry достаточно однозначно определить конкретный `NotificationChannel`.

Для этого используется:

```text
notification_channel_id
```

Retry обращается к соответствующему `NotificationChannel`.

Не сохраняются также:

* SMTP credentials;
* пароли;
* access tokens;
* refresh tokens;
* полный текст Notification;
* полная строка исключения;
* другие секреты.

Технические ошибки должны представляться безопасным кодом или классификацией, достаточной для Retry и диагностики.

---

# 11. Результат доставки

Текущего результата:

```text
successful = true / false
```

недостаточно для Retry.

Результат доставки должен позволять различать:

```text
SUCCESS
TEMPORARY_FAILURE
PERMANENT_FAILURE
```

## SUCCESS

Доставка успешно завершена.

Retry для этой `NotificationDelivery` больше не выполняется.

## TEMPORARY_FAILURE

Ошибка потенциально может исчезнуть при повторной попытке.

Примеры категорий:

* временная недоступность внешнего сервиса;
* сетевой сбой;
* временная ошибка SMTP;
* временный отказ внешнего провайдера.

Retry Policy может разрешить повторную попытку.

## PERMANENT_FAILURE

Повторная попытка не должна выполняться автоматически.

Примеры категорий:

* заведомо некорректный адрес;
* постоянная ошибка конфигурации доставки;
* ошибка, для которой повторная отправка не имеет смысла.

Конкретная классификация технических ошибок выполняется конкретным Delivery Adapter.

Notification Engine не должен содержать SMTP-, HTTP-, Telegram- или иной транспортной логики для определения типа ошибки.

---

# 12. Delivery Adapter отвечает за технический результат

Граница остаётся следующей:

```text
Notification Engine
        ↓
Delivery Port
        ↓
Channel Registry
        ↓
Delivery Adapter
        ↓
External Delivery Provider
```

Delivery Adapter:

* выполняет техническую доставку;
* классифицирует технический результат;
* не принимает бизнес-решение о количестве Retry;
* не определяет общую Retry Policy.

Retry Policy находится выше конкретного Delivery Adapter.

---

# 13. RetryPolicy является отдельной Application-компонентой

Retry Policy не реализуется непосредственно внутри Notification Engine через набор условных операторов.

Концептуально:

```text
DeliveryResult
      ↓
RetryPolicy
      ↓
RetryDecision
```

Retry Policy определяет:

* разрешён ли Retry;
* когда разрешена следующая попытка;
* исчерпан ли лимит попыток.

Конкретные значения:

* максимального числа попыток;
* задержек;
* максимальной задержки;
* backoff;

не фиксируются данным ADR.

Они являются отдельным решением и частью последующей реализации Retry Policy.

---

# 14. nextAttemptAt

Для временной ошибки может существовать:

```text
nextAttemptAt
```

Это техническое состояние Retry и не является частью `Notification`.

`nextAttemptAt` относится к конкретной `NotificationDelivery`.

Например:

```text
NotificationDelivery
    status = READY
    nextAttemptAt = 12:05
```

Retry Scheduler может выбрать такую доставку только после наступления:

```text
nextAttemptAt <= current time
```

---

# 15. Notification Lifecycle

Существующий lifecycle Notification сохраняется:

```text
PENDING
   ↓
PROCESSING
   ↓
SENT

PROCESSING
   ↓
FAILED
```

Новый статус:

```text
RETRY_PENDING
```

не вводится.

Причина: ожидание Retry является техническим состоянием обработки конкретного канала и не является новым бизнес-состоянием Notification.

Retry-состояние хранится на уровне:

```text
NotificationDelivery
```

---

# 16. Когда создаётся NotificationDelivery

После создания Notification для каждого конкретного включённого `NotificationChannel`, который должен получить данное уведомление, создаётся соответствующая `NotificationDelivery`.

Например:

```text
Notification
│
├── NotificationDelivery → Email #1
├── NotificationDelivery → Email #2
└── NotificationDelivery → Telegram #1
```

Каждая `NotificationDelivery` существует независимо.

Это позволяет:

* доставлять каналы независимо;
* повторять только неуспешный канал;
* сохранять историю каждого канала;
* не отправлять повторно уже успешные каналы.

---

# 17. Когда NotificationDelivery становится SENT

`NotificationDelivery` становится `SENT`, когда соответствующая доставка через конкретный `NotificationChannel` успешно завершена.

Например:

```text
Email #1
    attempt 1 → SUCCESS

Email #1 = SENT
```

После этого новая попытка для этой `NotificationDelivery` не создаётся.

---

# 18. Когда NotificationDelivery становится FAILED

`NotificationDelivery` становится `FAILED`, когда:

* получен `PERMANENT_FAILURE`; или
* Retry Policy запретила дальнейшие попытки; или
* допустимое количество попыток исчерпано.

История всех фактически выполненных попыток сохраняется.

Например:

```text
Telegram
├── attempt 1 → TEMPORARY_FAILURE
├── attempt 2 → TEMPORARY_FAILURE
└── attempt 3 → TEMPORARY_FAILURE
                    ↓
              limit exceeded
                    ↓
          NotificationDelivery = FAILED
```

---

# 19. Когда Notification становится SENT

Notification становится `SENT`, когда все необходимые `NotificationDelivery` завершены успешно.

Например:

```text
Email    → SENT
Telegram → SENT
```

→

```text
Notification = SENT
```

Успешные каналы не участвуют в последующих Retry.

---

# 20. Когда Notification становится FAILED

Notification становится `FAILED`, когда обработка завершена и остаётся хотя бы одна `NotificationDelivery` с окончательным результатом:

```text
FAILED
```

Например:

```text
Email:
  SENT

Telegram:
  FAILED
```

→

```text
Notification = FAILED
```

До наступления окончательного решения Notification может оставаться в:

```text
PROCESSING
```

Например:

```text
Email:
  SENT

Telegram:
  READY
  nextAttemptAt = future
```

В этом случае:

```text
Notification = PROCESSING
```

и Retry ещё не исчерпан.

---

# 21. Temporary Failure не означает немедленный FAILED

Если доставка завершилась:

```text
TEMPORARY_FAILURE
```

и Retry Policy разрешает повтор:

```text
NotificationDelivery
        │
        ├── current status = READY
        └── nextAttemptAt = future
```

Notification остаётся:

```text
PROCESSING
```

После наступления `nextAttemptAt` Retry Scheduler инициирует следующую попытку соответствующей `NotificationDelivery`.

---

# 22. Конкурентность Retry

Retry должен быть безопасен при:

* нескольких потоках;
* нескольких экземплярах приложения;
* одновременном запуске Scheduler;
* ручном и автоматическом запуске Retry.

Нельзя использовать:

* `synchronized`;
* JVM locks;
* in-memory locks;
* локальные карты занятых Notification;
* другие механизмы, работающие только внутри одного экземпляра JVM.

Защита Retry должна обеспечиваться атомарными операциями на уровне PostgreSQL.

---

# 23. Atomic Claim Retry

Перед выполнением повторной доставки `NotificationDelivery` должна быть атомарно захвачена одним обработчиком.

Концептуально:

```text
READY
  │
  │ nextAttemptAt <= now
  ▼
atomic claim
  │
  ▼
PROCESSING
```

Только обработчик, успешно выполнивший claim, получает право создавать и выполнять следующую `DeliveryAttempt`.

Другой экземпляр приложения должен получить:

```text
not claimed
```

и не выполнять доставку.

Конкретный SQL и форма persistence port определяются при реализации.

---

# 24. DeliveryAttempt создаётся после успешного claim

Порядок обработки Retry должен обеспечивать защиту от конкурентного создания одной и той же попытки.

Концептуально:

```text
Retry Scheduler
      ↓
find eligible NotificationDelivery
      ↓
atomic claim
      ↓
NotificationDelivery = PROCESSING
      ↓
create DeliveryAttempt
      ↓
Delivery Adapter
      ↓
DeliveryResult
```

Если claim не выполнен:

```text
DeliveryAttempt
```

не создаётся и внешняя доставка не выполняется.

---

# 25. Recovery зависшей доставки

Необходимо учитывать аварийное завершение:

```text
NotificationDelivery = PROCESSING
        ↓
DeliveryAttempt started
        ↓
external delivery
        ↓
application crash
```

В результате:

* `NotificationDelivery` может остаться в `PROCESSING`;
* `DeliveryAttempt` может остаться без `completed_at` и результата.

Такая ситуация должна быть обнаруживаема.

Retry Processing должен иметь механизм обнаружения зависших обработок и возможности их безопасного восстановления.

Критерий того, когда обработка считается зависшей, является частью Retry Processing / Retry Policy и не фиксируется данным ADR конкретным числом времени.

---

# 26. Неопределённый результат внешней доставки

Система не может гарантировать отсутствие дублей, если внешний Delivery Provider уже принял сообщение, но приложение не успело сохранить результат.

Возможен сценарий:

```text
1. Delivery Adapter отправил сообщение
2. External Provider принял сообщение
3. приложение завершилось до сохранения SUCCESS
4. Recovery обнаружил незавершённую попытку
5. доставка выполняется повторно
```

В результате возможен дубль.

Следовательно, текущая архитектура обеспечивает:

* защиту от конкурентного запуска одной `NotificationDelivery`;
* сохранение истории попыток;
* безопасное управление Retry;
* восстановление после незавершённой обработки;

но не гарантирует:

```text
ровно одна внешняя доставка
```

если внешний провайдер не поддерживает идемпотентность.

Идемпотентность конкретного внешнего провайдера является отдельным вопросом и не вводится данным ADR.

---

# 27. Retry Scheduler

Retry Scheduler является инфраструктурным механизмом запуска обработки готовых Retry.

Он не содержит бизнес-правил Retry.

Его ответственность:

```text
найти NotificationDelivery,
готовые к Retry
       ↓
передать их в Retry Processing
```

Решение:

```text
retry или не retry
```

остаётся ответственностью Retry Policy.

Scheduler не должен содержать:

```text
if attempts > 3
if smtp error
if telegram error
```

и другие правила Retry.

---

# 28. Persistence

Для хранения текущего состояния и истории создаются отдельные persistence-модели.

Концептуально:

```text
notification
    │
    │ 1:N
    ▼
notification_delivery
    │
    │ 1:N
    ▼
delivery_attempt
```

Persistence entities являются техническими моделями и не становятся JPA Entity внутри Domain Model.

Архитектурное направление сохраняется:

```text
Domain / Application Port
        ↓
Persistence Adapter
        ↓
JPA
        ↓
Hibernate
        ↓
PostgreSQL
```

Изменения схемы выполняются только через Liquibase.

Persistence должна обеспечить:

* однозначную связь `NotificationDelivery` с конкретным `NotificationChannel`;
* эффективный поиск готовых Retry;
* атомарный claim;
* однозначность `attempt_number` внутри `NotificationDelivery`;
* получение истории попыток;
* защиту от конкурентного создания конфликтующих записей.

Конкретные индексы, foreign keys и unique constraints определяются во время реализации persistence.

---

# 29. Безопасность

`NotificationDelivery` и `DeliveryAttempt` не должны становиться источником утечки пользовательских данных.

Запрещено сохранять или журналировать:

* полный `Notification.message`;
* полный `NotificationChannel.destination`;
* SMTP credentials;
* пароли;
* access tokens;
* refresh tokens;
* полные тексты исключений, если они могут содержать пользовательские данные или секреты.

В истории следует хранить только технически необходимые безопасные сведения.

`error_code` должен быть безопасным техническим идентификатором, а не произвольным текстом исключения.

---

# 30. Архитектурные границы

Сохраняется существующее направление:

```text
Application
    ↓
Domain
```

и:

```text
Infrastructure
    ↓
Application
    ↓
Domain
```

Notification Domain не знает о:

* Retry implementation;
* Scheduler;
* PostgreSQL;
* JPA;
* SMTP;
* Telegram;
* конкретных Delivery Adapter;
* механизме backoff.

Notification Engine использует:

* Retry Policy;
* Delivery Port;
* состояние `NotificationDelivery`.

Delivery Adapter выполняет только техническую доставку и классификацию технического результата.

Retry Scheduler не принимает бизнес-решения.

Persistence Adapter отвечает за техническое хранение и атомарные операции.

---

# 31. Итоговая архитектура

```text
Application / Processing Flow
            ↓
       Notification
            │
            │ 1:N
            ▼
   NotificationDelivery
            │
            ├──────────────────┐
            │                  │
            ▼                  ▼
 NotificationChannel       Retry Policy
            │                  │
            ▼                  ▼
     Delivery Attempt     Retry Decision
            │                  │
            ▼                  ▼
     Delivery Adapter    nextAttemptAt
            │                  │
            ▼                  ▼
External Provider       Retry Scheduler
                              │
                              ▼
                        Atomic Claim
                              │
                              ▼
                     NotificationDelivery
```

Для нескольких каналов:

```text
Notification
│
├── NotificationDelivery → Email #1
│    └── Attempt #1 → SUCCESS
│
├── NotificationDelivery → Email #2
│    ├── Attempt #1 → TEMPORARY_FAILURE
│    └── Attempt #2 → SUCCESS
│
└── NotificationDelivery → Telegram #1
     ├── Attempt #1 → TEMPORARY_FAILURE
     ├── Attempt #2 → TEMPORARY_FAILURE
     └── Attempt #3 → SUCCESS
```

Каждая `NotificationDelivery` имеет собственное состояние и собственную историю попыток.

Успешная `Email #1` больше не отправляется.

Успешная `Email #2` больше не отправляется после второй попытки.

Telegram повторяется независимо от Email.

---

# Consequences

## Положительные

* Notification Domain не перегружается техническим состоянием Retry.
* История доставки отделена от текущего состояния Retry.
* Retry работает независимо для каждого конкретного канала.
* Несколько каналов одного типа поддерживаются корректно.
* Успешные каналы не отправляются повторно.
* `NotificationChannel` однозначно идентифицирует цель конкретной доставки.
* `DeliveryAttempt` остаётся исторической моделью.
* `nextAttemptAt` не смешивается с историей фактических попыток.
* Retry Policy можно изменять независимо от Delivery Adapter.
* Добавление новых каналов не требует изменения модели Notification.
* Конкурентность работает между несколькими экземплярами приложения.
* История попыток позволяет диагностировать проблемы доставки.
* Recovery после аварийного завершения становится частью явной архитектуры.
* Persistence-модели Retry остаются отделёнными от Domain Model.

## Отрицательные

* Архитектура становится сложнее текущей модели Notification.
* Появляется отдельная модель `NotificationDelivery`.
* Появляется отдельная таблица для текущего состояния доставки.
* Появляется отдельная таблица истории `DeliveryAttempt`.
* Появляется дополнительная логика конкурентного claim.
* Retry требует Scheduler или другого механизма запуска.
* Необходима классификация ошибок каждым Delivery Adapter.
* Необходим механизм восстановления зависших обработок.
* Без идемпотентности внешнего провайдера возможны повторные доставки после неопределённого результата.

---

# Explicitly Out of Scope

Данный ADR не фиксирует конкретную реализацию:

* максимального количества попыток;
* конкретных значений backoff;
* конкретной формулы exponential backoff;
* jitter;
* конкретного расписания Retry Scheduler;
* dead-letter механизма;
* конкретной реализации идемпотентности внешних провайдеров;
* конкретных SMTP-кодов ошибок;
* Telegram/HTTP-специфичной классификации ошибок;
* метрик и мониторинга Retry;
* административного интерфейса для ручного Retry;
* отдельного API для Retry;
* изменения Notification Domain Model для хранения Retry;
* нового статуса `Notification`;
* конкретного механизма восстановления зависшей `NotificationDelivery`;
* изменения правил выбора включённых `NotificationChannel`;
* хранения destination в истории попыток.

---

# Related Decisions

Связанные архитектурные решения:

* ADR-001 — Domain First Architecture
* ADR-003 — Outage Processing Pipeline
* ADR-007 — Replaceable Infrastructure
* ADR-011 — Notification Channels and Extensible Delivery

TASK27A определяет атомарный claim:

```text
PENDING → PROCESSING
```

Данный ADR расширяет тот же принцип на Retry Processing, не заменяя решение TASK27A.

Retry claim выполняется на уровне:

```text
NotificationDelivery
```

а не на уровне всей `Notification`.

---
