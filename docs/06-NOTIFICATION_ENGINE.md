# Notification Engine

> Подсистема доставки уведомлений пользователям.

---

# Назначение

Документ описывает архитектуру
подсистемы уведомлений:

* поддерживаемые каналы доставки;
* границу ответственности Notification Engine;
* модель `NotificationDelivery`;
* историю `DeliveryAttempt`;
* Retry Processing;
* Retry Policy;
* конкурентную обработку;
* Recovery;
* взаимодействие с Delivery Adapter.

Notification Engine отвечает
на вопрос:

> **«Как Notification обработать и доставить пользователю?»**

Notification Domain отвечает
за предметный объект `Notification`
и его доменные правила.

Notification Engine отвечает
за обработку и доставку
готового `Notification`.

---

# Навигация

| Раздел       | Ссылка                                      |
| ------------ | ------------------------------------------- |
| ⬅ Предыдущий | [05-MATCHING_ENGINE](05-MATCHING_ENGINE.md) |
| 🏠 README    | [README](README.md)                         |
| ➡ Следующий  | [07-SECURITY](07-SECURITY.md)               |

---

# Связанные ADR

* [ADR-003 — Outage Processing Pipeline](adr/ADR-003-Outage-Processing-Pipeline.md)
* [ADR-006 — Matching Engine](adr/ADR-006-Matching-Engine.md)
* [ADR-007 — Replaceable Infrastructure](adr/ADR-007-Replaceable-Infrastructure.md)
* [ADR-011 — Notification Channels and Extensible Delivery](adr/ADR-011-Notification-Channels-and-Extensible-Delivery.md)
* [ADR-012 — Retry and Delivery Attempt Processing](adr/ADR-012-Retry-and-Delivery-Attempt-Processing.md)
* [ADR-013 — Retry Policy, Fencing and Recovery](adr/ADR-013-Retry-Policy-Fencing-and-Recovery.md)

---

# Связанные документы

* [02-DOMAIN_MODEL](02-DOMAIN_MODEL.md)
* [05-MATCHING_ENGINE](05-MATCHING_ENGINE.md)
* [03-DATABASE](03-DATABASE.md)
* [07-SECURITY](07-SECURITY.md)

---

# Связанные диаграммы

* [Notification Pipeline](diagrams/detailed/09-notification-pipeline.puml)

---

# Notification Engine

## Назначение

Notification Engine
отвечает за обработку
и доставку уведомлений
пользователям.

Подсистема получает
готовый `Notification`
из Application / Processing Flow
и обеспечивает его обработку
через доступные каналы доставки.

Notification Engine:

* не создаёт `Notification`;
* не принимает решение
  о необходимости уведомления;
* не формирует содержание сообщения;
* не изменяет бизнес-смысл `Notification`.

---

# Место в архитектуре

Outage Processing Pipeline:

```text
DuplicateResolver
       ↓
PowerOutage
       ↓
CandidateFinder
       ↓
Matching Engine
       ↓
Match
       ↓
Application / Processing Flow
       ↓
Notification
       ↓
Notification Engine
```

Предшествующая обработка:

```text
Source
   ↓
Scheduler
   ↓
Provider Registry
   ↓
ProviderContext
   ↓
OutageProvider
   ↓
ParsedOutage
```

относится к Parser Subsystem.

Notification Engine
не зависит от конкретного источника
данных об отключении.

---

# Граница ответственности

## Notification Domain

Notification Domain отвечает за:

* `Subscription`;
* `PowerOutage`;
* текст `Notification`;
* состояние `Notification`;
* доменные правила `Notification`;
* уникальность `Subscription + PowerOutage`;
* сохранение `Notification`.

Notification Domain
не определяет конкретный
канал доставки.

`Notification` не содержит:

* `NotificationChannel`;
* `channelType`;
* `destination`;
* `deliveryStatus`;
* `retryCount`;
* `nextAttemptAt`;
* `attemptNumber`;
* `processingToken`.

---

## Application / Processing Flow

Application / Processing Flow
отвечает за переход:

```text
Match
   ↓
Notification
```

После успешного `Match`
Application:

1. проверяет наличие `Notification`
   для пары `Subscription + PowerOutage`;
2. формирует текст `Notification`;
3. создаёт `Notification`
   со статусом `PENDING`;
4. сохраняет `Notification`.

Формирование текста
не является ответственностью
Notification Engine.

---

## Notification Engine

Notification Engine отвечает за:

* обработку готового `Notification`;
* работу с доступными каналами;
* создание и обработку `NotificationDelivery`;
* координацию доставки;
* передачу сообщения Delivery Adapter;
* обработку результата доставки;
* Retry Processing;
* применение Retry Policy;
* координацию `DeliveryAttempt`;
* завершение конкретной доставки.

Notification Engine не отвечает за:

* получение outage data;
* Parser;
* DuplicateResolver;
* CandidateFinder;
* Matching;
* создание `Match`;
* принятие решения
  о необходимости `Notification`;
* изменение содержания `Notification`.

---

# Создание NotificationDelivery

После создания `Notification`
для каждого включённого
`NotificationChannel`,
который должен получить
данное уведомление,
создаётся отдельная
`NotificationDelivery`.

Например:

```text
Notification
│
├── NotificationDelivery → Email #1
├── NotificationDelivery → Email #2
└── NotificationDelivery → Telegram #1
```

Каждая `NotificationDelivery`
обрабатывается независимо.

Это позволяет:

* иметь несколько каналов;
* иметь несколько каналов одного типа;
* повторять только неуспешную доставку;
* не связывать Retry с жизненным циклом `Notification`.

---

# NotificationDelivery

`NotificationDelivery`
является отдельной единицей
доставки и Retry.

Она связывает:

```text
Notification
       +
конкретный NotificationChannel
```

и хранит текущее состояние
конкретной доставки.

Основные поля:

```text
id
notification
notificationChannel
status
nextAttemptAt
```

Техническое поле:

```text
processingToken
```

не является частью Domain Model
и используется для конкурентной обработки
и fencing.

---

# NotificationDelivery Lifecycle

`NotificationDelivery`
имеет состояния:

```text
READY
PROCESSING
SENT
FAILED
```

Основной успешный путь:

```text
READY
   ↓
PROCESSING
   ↓
SENT
```

Постоянная ошибка:

```text
PROCESSING
   ↓
FAILED
```

Временная ошибка,
для которой Retry разрешён:

```text
PROCESSING
   ↓
READY
   +
nextAttemptAt
```

После наступления `nextAttemptAt`
доставка снова может быть принята
в обработку.

`Notification`
при этом не получает
нового Retry-статуса.

Статус `RETRY_PENDING`
для `Notification`
не используется.

---

# DeliveryAttempt

`DeliveryAttempt`
является исторической записью
конкретной попытки доставки.

Каждая фактическая попытка
создаёт отдельную запись.

Например:

```text
NotificationDelivery
│
├── DeliveryAttempt #1 → TEMPORARY_FAILURE
├── DeliveryAttempt #2 → TEMPORARY_FAILURE
└── DeliveryAttempt #3 → SUCCESS
```

`DeliveryAttempt` не является
владельцем текущего состояния Retry.

Текущее состояние Retry
и `nextAttemptAt`
принадлежат `NotificationDelivery`.

---

# Нумерация попыток

`attemptNumber`
нумеруется отдельно
для каждой `NotificationDelivery`.

То есть номер относится
к конкретной паре:

```text
Notification
      +
NotificationChannel
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

Разные каналы одного типа
имеют независимую нумерацию.

---

# Результат доставки

Delivery Adapter возвращает
классифицированный результат:

```text
SUCCESS
TEMPORARY_FAILURE
PERMANENT_FAILURE
```

## SUCCESS

Доставка успешно завершена.

```text
PROCESSING
    ↓
SENT
```

Retry для данной
`NotificationDelivery`
больше не выполняется.

---

## TEMPORARY_FAILURE

Ошибка потенциально
может исчезнуть при повторной попытке.

Примеры:

* временная недоступность
  внешнего сервиса;
* сетевой сбой;
* временная ошибка SMTP;
* временный отказ внешнего провайдера.

Retry Policy определяет,
разрешён ли повтор.

---

## PERMANENT_FAILURE

Ошибка считается постоянной,
и автоматический Retry
не выполняется.

```text
PROCESSING
    ↓
FAILED
```

Конкретную техническую
классификацию выполняет
Delivery Adapter.

Notification Engine
не содержит SMTP-, HTTP-,
Telegram- или иной транспортной
логики классификации ошибок.

---

# Retry Policy

Retry Policy является
отдельной Application-компонентой.

Она не является частью
Notification Domain.

Концептуальная последовательность:

```text
DeliveryResult
      ↓
RetryPolicy
      ↓
RetryDecision
```

Retry Policy определяет:

* разрешён ли Retry;
* время следующей попытки;
* исчерпан ли лимит попыток.

---

# Текущая Retry Policy

Текущая реализация использует
максимум **3 попытки**
на одну `NotificationDelivery`.

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

Максимальная задержка:

```text
15 минут
```

Для:

```text
SUCCESS
PERMANENT_FAILURE
```

повторная попытка
не создаётся.

Если лимит попыток исчерпан,
`NotificationDelivery`
переходит в:

```text
FAILED
```

Jitter в текущую реализацию
не входит.

---

# Retry Processing

Retry выполняется
на уровне `NotificationDelivery`,
а не `Notification`.

Общая схема:

```text
NotificationDelivery
        ↓
Retry Processing
        ↓
Atomic Claim
        ↓
DeliveryAttempt
        ↓
Delivery Adapter
        ↓
Delivery Result
        ↓
Retry Policy
```

Для временной ошибки:

```text
TEMPORARY_FAILURE
        ↓
RetryPolicy
        ↓
retry allowed
        ↓
READY + nextAttemptAt
```

или:

```text
TEMPORARY_FAILURE
        ↓
RetryPolicy
        ↓
retry forbidden
        ↓
FAILED
```

---

# Atomic Claim

Перед фактической обработкой
`NotificationDelivery`
выполняется атомарный claim.

```text
READY
  ↓
atomic claim
  ↓
PROCESSING
```

Claim выполняется
на уровне PostgreSQL.

Проверяется:

```text
status = READY
AND
(
    nextAttemptAt IS NULL
    OR
    nextAttemptAt <= now
)
```

Только один конкурентный
обработчик получает право
продолжить обработку.

Если claim не выполнен:

* `DeliveryAttempt`
  не создаётся;
* внешний Delivery Provider
  не вызывается;
* состояние текущего владельца
  не изменяется.

JVM-блокировки,
`synchronized`
и другие in-memory механизмы
не используются.

---

# Ownership Token и Fencing

Одного атомарного claim
недостаточно для безопасного
Recovery.

После успешного claim
создаётся уникальный:

```text
processingToken : UUID
```

Token является техническим
идентификатором владельца
текущей обработки.

Он хранится
в persistence-модели
`NotificationDelivery`.

`processingToken`
не входит в Domain Model.

Схема:

```text
READY
  ↓
atomic claim + processingToken
  ↓
PROCESSING
```

Финальное изменение состояния
должно выполняться только
владельцем соответствующего token.

Это защищает от ситуации,
когда старый обработчик
продолжает работу после Recovery
и нового claim.

---

# Почему нужен Fencing

Возможна следующая ситуация:

```text
Worker A
   ↓
claim
   ↓
PROCESSING + token A
   ↓
DeliveryAttempt #1
```

Worker A зависает.

Recovery:

```text
PROCESSING + token A
   ↓
Recovery
   ↓
READY + token NULL
```

После этого:

```text
Worker B
   ↓
claim
   ↓
PROCESSING + token B
   ↓
DeliveryAttempt #2
```

Worker A может завершиться позже.

Он не должен иметь возможности
изменить состояние,
созданное Worker B.

Поэтому финальная запись
проверяет:

```text
processingToken = token текущего Worker
```

Если token уже не принадлежит
текущему Worker,
изменение не выполняется.

---

# Recovery

Recovery предназначен
для восстановления
зависших `NotificationDelivery`.

Ситуация:

```text
PROCESSING
    ↓
DeliveryAttempt incomplete
```

Если последняя незавершённая
попытка считается зависшей,
Recovery возвращает доставку:

```text
PROCESSING
    ↓
READY
```

При этом:

* `processingToken` очищается;
* незавершённый `DeliveryAttempt`
  не изменяется;
* новая попытка не создаётся
  самим Recovery;
* внешний Delivery Provider
  Recovery не вызывается;
* Retry Policy Recovery
  не принимает;
* Notification не изменяется.

Следующая обработка
создаёт новый `DeliveryAttempt`.

Например:

```text
DeliveryAttempt #1
    ↓
incomplete

Recovery
    ↓

NotificationDelivery = READY

    ↓

DeliveryAttempt #2
```

---

# Recovery и конкурентность

Recovery не должен
восстанавливать доставку,
если существует более новая
незавершённая попытка,
которая ещё считается активной.

Для определения зависшей
доставки используется условие:

```text
status = PROCESSING

AND

EXISTS incomplete attempt
startedAt < stuckBefore

AND

NOT EXISTS incomplete attempt
startedAt >= stuckBefore
```

Это предотвращает ситуацию,
при которой старая незавершённая
попытка приводит к восстановлению
уже новой активной обработки.

---

# Retry Scheduler

`RetryScheduler`
является Infrastructure-механизмом.

Он:

1. находит `NotificationDelivery`,
   готовые к Retry;
2. передаёт их
   в Retry Processing.

Схема:

```text
RetryScheduler
      ↓
READY NotificationDelivery
      ↓
Retry Processing
```

Scheduler не содержит
бизнес-правил Retry.

Он не определяет:

```text
if attempts > 3
if smtp error
if telegram error
```

Retry Policy остаётся
ответственностью Application.

---

# Recovery Scheduler

`RecoveryScheduler`
является отдельным
Infrastructure-механизмом.

Он:

1. находит зависшие
   `NotificationDelivery`;
2. запускает Recovery
   для каждой найденной доставки.

Схема:

```text
RecoveryScheduler
      ↓
Stuck Detection
      ↓
Fenced Recovery
      ↓
READY
```

Recovery Scheduler
не содержит:

* Retry Policy;
* Delivery Adapter;
* channel-specific logic;
* логику создания `DeliveryAttempt`;
* логику изменения `Notification`.

---

# Каналы доставки

Notification Engine
не зависит от конкретного
способа доставки.

Каждый канал
представлен через:

```text
NotificationChannel
        ↓
Delivery Port
        ↓
Channel Registry
        ↓
Delivery Adapter
```

Один пользователь
может иметь:

* несколько каналов;
* несколько каналов одного типа.

Точный дубликат:

```text
user + type + destination
```

запрещается.

---

# Delivery Port

`DeliveryPort`
является Application-level
границей между обработкой
уведомления и конкретным
механизмом доставки.

Общая схема:

```text
Retry Processing
      ↓
Delivery Port
      ↓
Channel Registry
      ↓
Delivery Adapter
      ↓
External Delivery Provider
```

Notification Engine
не содержит условной логики
вида:

```text
if email
else if telegram
else if sms
```

Выбор Adapter выполняется
через Channel Registry.

---

# Delivery Adapter

Delivery Adapter находится
в Infrastructure.

Adapter отвечает только
за техническую доставку.

Он:

* получает готовое сообщение;
* использует конкретный
  `NotificationChannel`;
* выполняет техническую
  операцию доставки;
* классифицирует технический
  результат;
* возвращает результат
  через Delivery Port.

Adapter:

* не создаёт Notification;
* не принимает решение
  о необходимости уведомления;
* не определяет Retry Policy;
* не определяет количество попыток;
* не изменяет бизнес-содержание
  Notification.

---

# Email Delivery Adapter

На текущем этапе реализован:

```text
EmailDeliveryAdapter
```

Email является первой
конкретной Infrastructure
реализацией Delivery Adapter.

Adapter использует:

* `NotificationChannel.destination()`
  как получателя;
* готовый `Notification.message()`;
* внешнюю конфигурацию
  SMTP.

SMTP credentials
не хранятся в исходном коде.

Технические ошибки Email
классифицируются следующим образом:

```text
MailParseException
    ↓
PERMANENT_FAILURE
```

Другие `MailException`:

```text
MailException
    ↓
TEMPORARY_FAILURE
```

---

# Независимость каналов

Каждая `NotificationDelivery`
обрабатывается независимо.

Например:

```text
Notification
│
├── Email #1
│      └── SENT
│
├── Email #2
│      └── TEMPORARY_FAILURE
│             ↓
│           READY
│             ↓
│           Retry
│
└── Telegram #1
       └── SENT
```

Ошибка одного канала
не должна препятствовать
обработке других каналов.

Retry применяется
только к конкретной
`NotificationDelivery`.

---

# At-Least-Once Delivery

Внешняя доставка имеет
семантику:

```text
at-least-once
```

Fencing защищает
состояние приложения
от устаревшего обработчика.

Однако fencing
не гарантирует отсутствие
дубликатов во внешней системе.

Возможна ситуация:

```text
Application
    ↓
Delivery Provider
    ↓
сообщение принято
    ↓
Application аварийно завершилось
```

Если приложение не успело
сохранить успешный результат,
Recovery может привести
к повторной доставке.

Поэтому архитектура
не гарантирует:

```text
ровно одна внешняя доставка
```

если внешний провайдер
не поддерживает идемпотентность.

Идемпотентность конкретного
внешнего провайдера является
отдельным архитектурным вопросом.

---

# Обработка ошибок

Результат доставки
определяется Delivery Adapter.

После этого Application
использует Retry Policy.

Общий процесс:

```text
Delivery Adapter
       ↓
Delivery Result
       ↓
DeliveryAttempt
       ↓
RetryPolicy
       ↓
RetryDecision
```

## SUCCESS

```text
NotificationDelivery
    PROCESSING
        ↓
      SENT
```

## PERMANENT_FAILURE

```text
NotificationDelivery
    PROCESSING
        ↓
      FAILED
```

## TEMPORARY_FAILURE

При разрешённом Retry:

```text
NotificationDelivery
    PROCESSING
        ↓
READY + nextAttemptAt
```

При запрете Retry:

```text
NotificationDelivery
    PROCESSING
        ↓
FAILED
```

---

# История

`Notification`
остаётся исторически значимым
доменным объектом.

Ошибка доставки
не удаляет `Notification`.

История фактических попыток
хранится отдельно:

```text
Notification
      │
      │ 1:N
      ↓
NotificationDelivery
      │
      │ 1:N
      ↓
DeliveryAttempt
```

`DeliveryAttempt`
не хранит:

* destination;
* полный текст Notification;
* SMTP credentials;
* access tokens;
* refresh tokens;
* полную строку исключения;
* текущее состояние Retry;
* `nextAttemptAt`.

---

# Логирование

Notification Delivery
и Retry Processing
не должны раскрывать
пользовательские данные
или секреты через журналы.

Не следует передавать
в логи:

* полный `Throwable`;
* полный текст исключения;
* destination;
* полный текст Notification;
* SMTP credentials;
* access tokens;
* refresh tokens.

Для диагностики используются:

* `NotificationDelivery.id`;
* безопасный технический
  `errorCode`;
* класс технического исключения
  без сообщения, если это необходимо.

`NotificationDelivery.toString()`
и `DeliveryAttempt.toString()`
не должны раскрывать связанные
объекты, которые могут содержать
destination или полный текст
Notification.

---

# Процесс обработки Notification

После создания `Notification`
обработка выполняется
в несколько уровней.

## Этап 1 — Получение Notification

Application передаёт
готовый `Notification`.

Новое уведомление
имеет статус:

```text
PENDING
```

---

## Этап 2 — Принятие Notification

Для первоначальной обработки
используется атомарный переход:

```text
PENDING
   ↓
atomic claim
   ↓
PROCESSING
```

Только один конкурентный
обработчик получает право
продолжить обработку
данного Notification.

---

## Этап 3 — Определение каналов

Для Notification
определяются включённые
`NotificationChannel`.

Для каждого канала
создаётся отдельная
`NotificationDelivery`.

```text
Notification
│
├── NotificationDelivery
│       └── Email #1
│
├── NotificationDelivery
│       └── Email #2
│
└── NotificationDelivery
        └── Telegram #1
```

---

## Этап 4 — Создание DeliveryAttempt

Перед фактической доставкой
успешно захваченная
`NotificationDelivery`
получает новый
`DeliveryAttempt`.

Номер попытки определяется
отдельно для этой
`NotificationDelivery`.

---

## Этап 5 — Передача Adapter

Retry Processing
использует готовый
`Notification.message()`.

Сообщение не формируется
и не изменяется
в Notification Engine.

Доставка выполняется
через:

```text
Delivery Port
      ↓
Channel Registry
      ↓
Delivery Adapter
```

---

## Этап 6 — Получение результата

Adapter возвращает:

```text
SUCCESS
TEMPORARY_FAILURE
PERMANENT_FAILURE
```

Результат записывается
в `DeliveryAttempt`.

---

## Этап 7 — Retry Decision

При `TEMPORARY_FAILURE`
Retry Policy определяет,
будет ли следующая попытка.

```text
TEMPORARY_FAILURE
        ↓
RetryPolicy
        ↓
RetryDecision
```

При разрешённом Retry:

```text
PROCESSING
    ↓
READY
    +
nextAttemptAt
```

При запрете:

```text
PROCESSING
    ↓
FAILED
```

---

# Notification Lifecycle

Retry не изменяет
бизнес-жизненный цикл
`Notification`.

Lifecycle `Notification`:

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

Retry lifecycle
существует отдельно
на уровне `NotificationDelivery`.

Таким образом:

```text
Notification lifecycle
        ≠
NotificationDelivery lifecycle
```

и:

```text
DeliveryAttempt history
        ≠
Retry state
```

---

# Разделение ответственности

## Matching Engine

Отвечает на вопрос:

> «Нужно ли уведомить эту Subscription об этом PowerOutage?»

Результат:

```text
Match
```

Matching Engine:

* не создаёт Notification;
* не выбирает канал;
* не вызывает Delivery Adapter;
* не зависит от Retry Policy.

---

## Application / Processing Flow

Отвечает за переход:

```text
Match
   ↓
Notification
```

и создание готового
`Notification`.

---

## Notification

Отвечает за:

* Subscription;
* PowerOutage;
* текст;
* состояние;
* доменные правила;
* уникальность
  `Subscription + PowerOutage`.

---

## NotificationDelivery

Отвечает за:

* конкретную доставку;
* конкретный NotificationChannel;
* текущее состояние доставки;
* `nextAttemptAt`.

---

## DeliveryAttempt

Отвечает за:

* историю фактических попыток;
* номер попытки;
* время начала и завершения;
* результат;
* безопасный технический код ошибки.

---

## Retry Policy

Отвечает за:

* возможность Retry;
* время следующей попытки;
* ограничение количества попыток.

---

## Retry Processing

Отвечает за координацию:

```text
claim
→ DeliveryAttempt
→ Delivery Adapter
→ result
→ Retry Policy
→ next state
```

---

## Retry Scheduler

Отвечает только за
запуск готовых Retry.

---

## Recovery Scheduler

Отвечает только за
обнаружение и восстановление
зависших `NotificationDelivery`.

---

## Delivery Adapter

Отвечает на вопрос:

> «Как выполнить техническую доставку через конкретный канал?»

---

# Расширяемость

Архитектура Notification Engine
предусматривает подключение
новых способов доставки.

Для нового канала необходимо:

1. реализовать новый Delivery Adapter;
2. зарегистрировать тип канала;
3. подключить Adapter
   через Channel Registry.

Основная модель:

```text
Notification
      ↓
NotificationDelivery
      ↓
NotificationChannel
      ↓
Delivery Port
      ↓
Channel Registry
      ↓
Delivery Adapter
```

Добавление нового канала
не требует изменения:

* Matching Engine;
* Parser;
* `Notification`;
* Retry Policy;
* существующих Delivery Adapter.

---

# Архитектурные ограничения

Notification Engine
не должен:

* создавать `Match`;
* выполнять Matching;
* получать outage data;
* обращаться к внешним outage sources;
* изменять Subscription;
* изменять PowerOutage;
* принимать решение
  о необходимости Notification;
* изменять содержание Notification;
* содержать транспортную логику
  конкретного канала.

Domain Model не знает о:

* Retry Scheduler;
* Recovery Scheduler;
* PostgreSQL claim;
* ownership token;
* JPA;
* конкретных Delivery Adapter;
* SMTP;
* Telegram;
* механизме backoff.

---

# Persistence

Persistence-модели
Retry отделены от Domain Model
с точки зрения JPA.

Используется структура:

```text
notification
      │
      │ 1:N
      ↓
notification_delivery
      │
      │ 1:N
      ↓
delivery_attempt
```

`notification_delivery`
хранит текущее состояние
конкретной доставки.

`delivery_attempt`
хранит историю
фактических попыток.

Все изменения
Database Schema выполняются
через Liquibase.

---

# Текущий scope

В рамках текущей архитектуры
реализованы:

* `Notification`;
* `NotificationChannel`;
* `NotificationDelivery`;
* `DeliveryAttempt`;
* `Notification Engine`;
* `DeliveryPort`;
* `DeliveryChannelRegistry`;
* `EmailDeliveryAdapter`;
* классификация результатов доставки;
* `RetryPolicy`;
* `RetryDecision`;
* `FixedRetryPolicy`;
* `RetryProcessingService`;
* atomic claim;
* `processingToken`;
* ownership fencing;
* stuck recovery;
* `RetryScheduler`;
* `RecoveryScheduler`;
* persistence `NotificationDelivery`;
* persistence `DeliveryAttempt`;
* Liquibase migrations;
* безопасное логирование доставки.

---

# Ограничения текущей реализации

В текущий scope не входят:

* dead-letter механизм;
* административный Retry API;
* метрики Retry;
* расширенные provider-specific Retry Policy;
* идемпотентность внешних Delivery Provider;
* изменение Notification lifecycle;
* дополнительные конкретные Delivery Adapter,
  кроме уже реализованного Email Adapter.

Эти вопросы являются отдельными
будущими решениями.

---

# Итоговая архитектура

```text
Application / Processing Flow
             ↓
        Notification
             ↓
    Notification Engine
             ↓
      NotificationDelivery
             ↓
       Retry Processing
             ↓
       Atomic Claim
             ↓
      DeliveryAttempt
             ↓
        Delivery Port
             ↓
       Channel Registry
             ↓
      Delivery Adapter
             ↓
 External Delivery Provider
```

Retry:

```text
Delivery Result
      ↓
  Retry Policy
      ↓
 Retry Decision
      ↓
READY + nextAttemptAt
```

Recovery:

```text
Recovery Scheduler
      ↓
Stuck Detection
      ↓
Fenced Recovery
      ↓
READY
```

Конкурентная обработка:

```text
READY
  ↓
atomic claim
  +
processingToken
  ↓
PROCESSING
  ↓
fenced final update
```

Notification lifecycle
при этом остаётся независимым:

```text
PENDING
   ↓
PROCESSING
   ↓
SENT / FAILED
```

Retry lifecycle
и история попыток
не становятся частью
бизнес-жизненного цикла
`Notification`.

---

# Связанные документы

* [00.5-GLOSSARY](00.5-GLOSSARY.md)
* [01-ARCHITECTURE](01-ARCHITECTURE.md)
* [02-DOMAIN_MODEL](02-DOMAIN_MODEL.md)
* [03-DATABASE](03-DATABASE.md)
* [05-MATCHING_ENGINE](05-MATCHING_ENGINE.md)
* [07-SECURITY](07-SECURITY.md)

---

# Связанные ADR

* [ADR-003 — Outage Processing Pipeline](adr/ADR-003-Outage-Processing-Pipeline.md)
* [ADR-006 — Matching Engine](adr/ADR-006-Matching-Engine.md)
* [ADR-007 — Replaceable Infrastructure](adr/ADR-007-Replaceable-Infrastructure.md)
* [ADR-011 — Notification Channels and Extensible Delivery](adr/ADR-011-Notification-Channels-and-Extensible-Delivery.md)
* [ADR-012 — Retry and Delivery Attempt Processing](adr/ADR-012-Retry-and-Delivery-Attempt-Processing.md)
* [ADR-013 — Retry Policy, Fencing and Recovery](adr/ADR-013-Retry-Policy-Fencing-and-Recovery.md)

---

# Диаграммы

* [Notification Pipeline](diagrams/detailed/09-notification-pipeline.puml)

---

| ⬅ Предыдущий                                | 🏠 README           | ➡ Следующий                   |
| ------------------------------------------- | ------------------- | ----------------------------- |
| [05-MATCHING_ENGINE](05-MATCHING_ENGINE.md) | [README](README.md) | [07-SECURITY](07-SECURITY.md) |
