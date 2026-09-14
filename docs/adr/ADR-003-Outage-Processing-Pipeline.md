# ADR-003 — Outage Processing Pipeline

> Архитектурное решение о построении конвейера обработки событий отключений.

---

## Статус

**Accepted**

---

## Дата

2026-08-14

---

## Версия

1.3

---

## Навигация

| Раздел | Ссылка |
|---------|--------|
| ⬅ Предыдущий | [ADR-002](ADR-002-Canonical-Address-Model.md) |
| 🏠 Документация | [README](../README.md) |
| 📚 ADR | [ADR Index](README.md) |
| ➡ Следующий | [ADR-004](ADR-004-OutageProvider-Architecture.md) |

---

## Влияние

Данное решение оказывает влияние на:

- Parser;
- Matching Engine;
- Application / Processing Flow;
- Notification;
- Notification Engine.

---

## Связанные документы

- [04-PARSER](../04-PARSER.md)
- [05-MATCHING_ENGINE](../05-MATCHING_ENGINE.md)
- [06-NOTIFICATION_ENGINE](../06-NOTIFICATION_ENGINE.md)
- [02-DOMAIN_MODEL](../02-DOMAIN_MODEL.md)

---

## Связанные диаграммы

- [Parser Pipeline](../diagrams/detailed/07-parser-pipeline.puml)
- [Matching Pipeline](../diagrams/detailed/08-matching-pipeline.puml)
- [Notification Pipeline](../diagrams/detailed/09-notification-pipeline.puml)

---

# ADR-003 — Outage Processing Pipeline

**Статус:** Accepted

**Дата:** 2026-08-14

**Версия:** 1.3

---

# Назначение

Зафиксировать архитектурное решение,
определяющее последовательность обработки информации
об отключениях электроэнергии
в системе BlackoutRadar.

Данный документ определяет
единственный допустимый поток обработки данных
от момента получения информации
до создания уведомления пользователю
и последующей передачи Notification
в Notification Engine.

---

# Контекст

BlackoutRadar получает информацию
из различных внешних источников.

Источник может быть любым:

- HTML;
- PDF;
- DOCX;
- RTF;
- REST API;
- open-source parser;
- другие поставщики данных.

Независимо от источника,
вся информация должна проходить
единый процесс обработки.

---

# Проблема

Если каждый источник данных
будет самостоятельно:

- сохранять отключения;
- искать совпадения;
- выполнять дедупликацию;
- отправлять уведомления,

то система быстро станет
сильно связанной.

Любое изменение алгоритма
потребует изменения
всех поставщиков данных.

Подобная архитектура
не соответствует принципам
Domain First Architecture.

---

# Рассмотренные варианты

## Вариант 1 — Каждый Provider выполняет полный цикл

Provider

↓

Database

↓

Matching

↓

Notification

### Преимущества

- простая реализация.

### Недостатки

- дублирование логики;
- нарушение SRP;
- сложность сопровождения;
- сильная связанность компонентов.

---

## Вариант 2 — Общий Pipeline обработки

OutageProvider

↓

ParsedOutage

↓

DuplicateResolver

↓

PowerOutage

↓

CandidateFinder

↓

Matching Engine

↓

Notification

↓

Notification Engine

Каждый этап
решает только одну задачу.

### Преимущества

- слабая связанность;
- высокая расширяемость;
- простота тестирования;
- единый алгоритм обработки.

### Недостатки

- большее количество компонентов;
- более сложная первоначальная архитектура.

---

# Принятое решение

В проекте используется
единый Pipeline обработки отключений.

Независимо
от источника информации
данные проходят
одни и те же этапы обработки.

Ни один этап
не может быть пропущен.

Pipeline разделяет:

- дедупликацию;
- создание постоянного события;
- поиск кандидатов;
- Matching;
- создание Notification;
- доставку Notification.

> Уточнение TASK 21:
> Scheduler и Provider Subsystem
> (`Source` → `Scheduler` → `Provider Registry` → `ProviderContext` → `OutageProvider` → `ParsedOutage`)
> находятся **перед** входом `ParsedOutage`
> в Processing Pipeline
> и не смешиваются с самим Pipeline.
> Pipeline начинается с уже полученного `ParsedOutage`
> (целевой контракт `ParsedOutage` с `sourceId`).
> Сам Pipeline
> при этом не изменяется.

---

# Этапы Pipeline

> Pipeline начинается
> с `DuplicateResolver`
> и уже полученного `ParsedOutage`.
> Получение данных (`Source` → `Scheduler` → `Provider Registry` → `ProviderContext` → `OutageProvider` → `ParsedOutage`)
> относится к Parser Subsystem (см. ADR-004, 04-PARSER)
> и находится перед Pipeline.

## 1. Дедупликация

DuplicateResolver определяет:

- новое событие;
- обновление существующего;
- дубликат.

Только после данного этапа
создается PowerOutage.

---

## 2. Поиск кандидатов

CandidateFinder
выполняет предварительный поиск
подписок,
которые потенциально
могут соответствовать
найденному отключению.

---

## 3. Сопоставление

Matching Engine
проверяет:

- адрес;
- трансформаторную подстанцию;
- период мониторинга;
- дополнительные правила.

Результатом является Match.

Matching Engine
не создаёт Notification
и не зависит
от способа его доставки.

---

## 4. Создание Notification

После успешного Match
Application / Processing Flow
создаёт Notification.

Notification представляет
доменный объект,
содержащий необходимость
уведомить пользователя
о найденном совпадении.

Notification
не имеет технической
зависимости от Match.

Текст Notification
формируется на уровне Application
до создания Domain Notification.

Notification Domain
не определяет конкретный формат
текста сообщения.

Match является
временным результатом
Matching Engine.

Связь:

Match

↓

Application / Processing Flow

↓

Notification

Правило создания Notification
после успешного Match
является правилом
Application / Processing Flow.

### 4.1. Idempotent Creation

Application / Processing Flow
перед созданием Notification
проверяет наличие существующего
Notification для пары:

Subscription
+
PowerOutage.

Используется существующий
NotificationPort.

Если Notification
для данной пары уже существует,
новый Notification
не создаётся.

Если Notification
для данной пары отсутствует,
Application / Processing Flow
создаёт новый Notification
со статусом PENDING
и сохраняет его через NotificationPort.

Таким образом:

существующий Notification
→ пропуск создания

отсутствующий Notification
→ создание Notification
→ PENDING.

Правило:

Subscription + PowerOutage
→ не более одного Notification.

Application использует проверку
существующего Notification
для идемпотентности повторной
обработки одного и того же Match.

Физическая защита уникальности
остаётся ответственностью
Persistence / Database через:

UNIQUE(subscription_id, power_outage_id).

Application не использует
DataIntegrityViolationException
как механизм обычного управления
этой конкуренцией.

JVM locks, synchronized
и другие Application-level locks
не используются.

---

## 5. Передача Notification

После создания Notification
Application / Processing Flow
передаёт готовый Notification
в Notification Engine.

Notification Engine
не создаёт Notification
и не принимает решение
о необходимости его создания.

Связь:

Notification

↓

Notification Engine

---

## 6. Обработка Notification

Notification Engine
принимает Notification
в обработку.

Notification имеет
следующие состояния:

- PENDING;
- PROCESSING;
- SENT;
- FAILED.

### PENDING

Notification создан
и ожидает обработки
Notification Engine.

### PROCESSING

Notification Engine
принял Notification
в обработку.

PROCESSING означает
состояние обработки
Notification Engine.

PROCESSING не является
состоянием конкретного
канала доставки
или Delivery Adapter.

### SENT

Notification Engine
успешно завершил
обработку Notification.

SENT означает
успешное завершение
операции доставки.

SENT не означает
гарантированное прочтение
или ознакомление пользователя
с сообщением.

### FAILED

Обработка Notification
завершилась ошибкой.

Notification не удаляется
и сохраняется в истории.

Notification Engine
может принять решение
о повторной обработке
на уровне Notification.

Актуальная модель:
решение о повторной обработке
определяется Retry Policy
на уровне NotificationDelivery
(ADR-012, ADR-013).

---

# Retry

Retry относится
к обработке Notification.

Retry Policy не является
частью текущего
Notification Domain.

Повторная обработка
FAILED Notification
не создаёт новый Notification.

Возможный flow:

FAILED

↓

Retry Decision

↓

PROCESSING

↓

SENT

Конкретные:

- количество попыток;
- интервалы;
- backoff;
- условия повторной обработки;
- время следующей обработки;
- история Delivery Attempt

определены
в рамках обработки Notification.

Детальная модель Retry
определена
в ADR-012
и ADR-013.

---

# Delivery Attempt

Delivery Attempt
не является частью
текущей Domain Model.

История попыток доставки
относится к обработке Notification.

Конкретная модель
Delivery Attempt,
ее хранение
и lifecycle
определены
в ADR-012
и ADR-013.

---

# Архитектурные границы

## OutageProvider

Отвечает за:

- получение внешних данных;
- преобразование
  во внутреннюю ParsedOutage.

Не отвечает за:

- создание PowerOutage;
- дедупликацию;
- Matching;
- Notification;
- доставку.

---

## DuplicateResolver

Отвечает за:

- обнаружение дубликатов;
- определение необходимости
  создания или обновления
  PowerOutage.

Не отвечает за:

- Matching;
- Notification;
- доставку.

---

## CandidateFinder

Отвечает за:

- поиск потенциальных
  Subscription.

Не принимает
окончательное решение
о совпадении.

---

## Matching Engine

Отвечает за:

- окончательное сопоставление
  PowerOutage и Subscription;
- формирование Match.

Не отвечает за:

- создание Notification;
- доставку Notification;
- конкретный канал доставки.

---

## Application / Processing Flow

Отвечает за:

- передачу результата Match
  на следующий этап;
- создание Notification
  после успешного Match;
- передачу готового Notification
  в Notification Engine.

Application / Processing Flow
не изменяет бизнес-смысл
Match или Notification.

---

## Notification

Отвечает за:

- Subscription;
- PowerOutage;
- текст уведомления;
- состояние;
- уникальность
  Subscription + PowerOutage;
- сохранение истории.

Notification не отвечает за:

- Matching;
- выбор канала;
- доставку;
- Retry;
- Delivery Attempt.

---

## Notification Engine

Исходный перечень ответственности
(исторический контекст;
уточнён ADR-011, ADR-012, ADR-013):

Отвечает за:

- принятие Notification
  в обработку;
- выбор канала;
- подготовку сообщения;
- передачу Adapter;
- обработку результата;
- обновление состояния Notification;
- Retry Decision.

Актуальная модель после ADR-012,
ADR-013 и ADR-014:

Notification Engine:

* принимает готовый Notification;
* определяет включённые NotificationChannel;
* создает NotificationDelivery;
* передает NotificationDelivery в Retry Processing.

Notification Engine не принимает
самостоятельное решение о Retry.

Retry Decision принимает Retry Policy
на уровне NotificationDelivery.

Notification Engine не определяет
итоговое состояние Notification
по результату одной доставки.

Итоговое состояние Notification
определяется отдельной операцией
финализации после успешного
fenced-завершения NotificationDelivery.


Notification Engine
не отвечает за:

- получение outage data;
- Parser;
- DuplicateResolver;
- CandidateFinder;
- Matching;
- создание Notification.

---

# Архитектурные принципы

## 1. Каждый этап отвечает только за одну задачу

Каждый компонент Pipeline
имеет одну ответственность.

---

## 2. Этапы независимы

Изменение одного этапа
не должно требовать
изменения остальных.

---

## 3. Pipeline не зависит от источника данных

Любой источник информации
должен предоставлять
одинаковую внутреннюю модель
ParsedOutage.

---

## 4. Pipeline не зависит от способа уведомления

Email,
Telegram,
Push

являются только
способами доставки
Notification.

Изменение способа доставки
не должно изменять
бизнес-логику Pipeline.

---

## 5. Matching не зависит от доставки

Matching Engine
формирует Match
и не зависит
от Notification Engine
или конкретного Delivery Adapter.

---

## 6. Notification не зависит от Match технически

Notification создаётся
после успешного Match,
но не содержит
и не требует
объект Match.

Правило последовательности:

Match

↓

Application / Processing Flow

↓

Notification

является правилом
прикладного процесса.

---

## 7. Retry не является частью Domain Model

Retry Policy,
Delivery Attempt
и повторное планирование
относятся к
Notification Engine.

---

## 8. Доставка не влияет
на бизнес-решение

Ошибка доставки
не изменяет результат
Matching Engine.

Match остаётся
положительным результатом
сопоставления.

Ошибка доставки
изменяет состояние
Notification,
но не отменяет
сам факт Match.

---

# Причины выбора

Pipeline позволяет:

- отделить получение данных
  от бизнес-логики;
- исключить дублирование;
- обеспечить расширяемость;
- упростить тестирование;
- поддерживать единый алгоритм обработки;
- отделить Matching
  от доставки;
- отделить Notification Domain
  от Delivery Infrastructure;
- независимо развивать
  Retry и Delivery Processing.

---

# Положительные последствия

После принятия данного решения:

- упрощается сопровождение;
- уменьшается связанность компонентов;
- легко добавляются новые Provider;
- легко изменяются алгоритмы Matching;
- легко расширяется Notification Engine;
- Notification Domain
  не зависит от Match implementation;
- Retry не загрязняет
  Domain Model;
- изменение канала доставки
  не требует изменения
  Matching Engine.

---

# Отрицательные последствия

Решение приводит к:

- увеличению количества компонентов;
- увеличению числа объектов;
- появлению отдельного
  Application / Processing Flow;
- более сложной первоначальной архитектуре;
- необходимости явно разделять
  Domain и Delivery lifecycle.

Данные недостатки
признаны допустимыми.

---

# Влияние на реализацию

Настоящее решение оказывает влияние
на следующие компоненты:

- OutageProvider;
- ParsedOutage;
- DuplicateResolver;
- PowerOutage;
- CandidateFinder;
- Matching Engine;
- Match;
- Application / Processing Flow;
- Notification;
- Notification Engine;
- Delivery Adapter;
- Retry Processing.

---

# Влияние на Domain Model

Notification остаётся
самостоятельным
доменным объектом.

Notification:

- не зависит технически
  от Match;
- имеет собственный lifecycle;
- имеет состояния:
  PENDING,
  PROCESSING,
  SENT,
  FAILED;
- сохраняет связь
  с Subscription;
- сохраняет связь
  с PowerOutage;
- ограничен уникальностью
  Subscription + PowerOutage.

Retry Policy
и Delivery Attempt
не входят
в текущую Domain Model.

---

# Влияние на Notification Engine

Notification Engine
получает уже созданный
Notification.

Engine:

Исходная последовательность
(исторический контекст;
актуальная модель — ниже):

1. принимает Notification;
2. переводит его
   в PROCESSING;
3. определяет канал;
4. подготавливает сообщение;
5. передаёт Adapter;
6. получает результат;
7. переводит Notification
   в SENT или FAILED;
8. при необходимости
   принимает Retry Decision.

Шаги 3–8 в актуальной архитектуре
выполняются на уровне NotificationDelivery
через Retry Processing;
конкретная Retry Policy
определяется ADR-013.

Конкретная Retry Policy
не определяется
на уровне данного ADR.

Retry Processing реализуется после создания Notification.

Retry выполняется на уровне NotificationDelivery.

Notification lifecycle не изменяется.

NotificationDelivery и DeliveryAttempt не являются состояниями Notification.

Конкретная реализация Retry Processing определяется ADR-012
и ADR-013.

---

# Влияние на Matching Engine

Matching Engine
завершает свою работу
формированием Match.

Он:

- не создаёт Notification;
- не выбирает канал;
- не вызывает Delivery Adapter;
- не зависит от Retry Policy.

Последующий переход:

Match

↓

Application / Processing Flow

↓

Notification

является следующим этапом
Pipeline.

---

# Влияние на Persistence

Pipeline ADR
не определяет
конкретную persistence
реализацию.

Notification persistence
остаётся ответственностью
Notification Persistence.

Текущая модель Notification
хранит:

- subscription_id;
- power_outage_id;
- message;
- status;
- created_at;
- updated_at.

Retry и Delivery Attempt
не входят
в Domain Model Notification.

Их persistence реализуется
через отдельные модели
NotificationDelivery
и DeliveryAttempt.

Конкретная модель Retry Processing
определяется ADR-012
и ADR-013.

---

# Совместимость с Replaceable Infrastructure

Notification Engine
и Delivery Adapter
являются частью
внешней инфраструктуры.

Domain Model
не зависит от:

- SMTP;
- Telegram;
- Push;
- конкретных Adapter;
- Spring;
- PostgreSQL;
- других инфраструктурных технологий.

Замена конкретного
механизма доставки
не должна изменять
Domain Model.

---

# Изменение ADR

Версия 1.1 уточнила
существующее архитектурное решение
и не изменяла
основной Pipeline
(выделение Application / Processing Flow
между Match и Notification,
разделение Notification Domain lifecycle,
Notification Engine processing,
Retry Processing, Delivery Attempt).

Версия 1.2 уточняет
границу Pipeline:

- `OutageProvider` и `ParsedOutage`
  исключены из списка этапов Pipeline;
- Pipeline начинается
  с `DuplicateResolver`
  (уже полученный `ParsedOutage`
  приходит извне);
- основная последовательность Pipeline:

```text
Основная последовательность Pipeline после Match:

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
↓
NotificationDelivery
↓
Retry Processing
↓
DeliveryPort
↓
Channel Registry
↓
Delivery Adapter
↓
External Delivery Provider
```

`NotificationDelivery` является конкретной единицей доставки Notification.

Каждая `NotificationDelivery` обрабатывается независимо.

Retry и состояние конкретной доставки принадлежат `NotificationDelivery`, а не `Notification`.

После успешного fenced-завершения `NotificationDelivery` запускается финализация `Notification`, которая принимает итоговое состояние на основании актуального состояния всех связанных доставок.


Предшествующая цепочка
`Source` → `Scheduler` → `Provider Registry` → `ProviderContext` → `OutageProvider` → `ParsedOutage`
относится к Parser Subsystem (ADR-004, 04-PARSER)
и находится перед Pipeline.

Новое архитектурное решение
для данного уточнения
не требуется.

Версия 1.3 уточняет
Notification Pipeline после реализации
TASK 27 и TASK 28.

Основная последовательность Pipeline
расширена до полной цепочки доставки:

```text
Notification
↓
Notification Engine
↓
NotificationDelivery
↓
Retry Processing
↓
DeliveryPort
↓
Channel Registry
↓
Delivery Adapter
↓
External Delivery Provider
```

`NotificationDelivery` является
самостоятельной единицей доставки
и Retry Processing.

Retry state и `DeliveryAttempt`
не входят в состояние `Notification`.

После успешного fenced-завершения
`NotificationDelivery` выполняется
финализация `Notification`.

Финализация:

* выполняется только после успешного fenced update;
* получает короткую блокировку строки `Notification` в PostgreSQL;
* повторно читает актуальное состояние всех связанных `NotificationDelivery`;
* определяет итоговое состояние `Notification`
  по состояниям всех доставок.

Правила финализации:

```text
есть READY или PROCESSING
        ↓
Notification = PROCESSING
```

```text
все NotificationDelivery → SENT
        ↓
Notification = SENT
```

```text
все NotificationDelivery завершены
и хотя бы одна → FAILED
        ↓
Notification = FAILED
```

Конкурентная обработка
`NotificationDelivery`
остается независимой и может
выполняться параллельно.

Данное уточнение соответствует
ADR-012, ADR-013 и ADR-014.

Новое состояние `Notification`
не вводится.

Retry state не переносится
в `Notification`.

Для изменения механизма
конкурентной финализации требуется
отдельное архитектурное решение.


---

# Статус

**Accepted**

Изменение основного Pipeline
допускается
только путем создания
нового ADR.

Уточнения границ
ответственности,
не изменяющие основной Pipeline,
могут вноситься
в рамках новой версии
данного ADR.

---

# См. также

## Документы

- [02-DOMAIN_MODEL](../02-DOMAIN_MODEL.md)
- [04-PARSER](../04-PARSER.md)
- [05-MATCHING_ENGINE](../05-MATCHING_ENGINE.md)
- [06-NOTIFICATION_ENGINE](../06-NOTIFICATION_ENGINE.md)

## ADR

- [ADR-001 — Domain First Architecture](ADR-001-Domain-First-Architecture.md)
- [ADR-002 — Canonical Address Model](ADR-002-Canonical-Address-Model.md)
- [ADR-004 — OutageProvider Architecture](ADR-004-OutageProvider-Architecture.md)
- [ADR-005 — PowerOutage Event Model](ADR-005-PowerOutage-Event-Model.md)
- [ADR-006 — Matching Engine](ADR-006-Matching-Engine.md)
- [ADR-007 — Replaceable Infrastructure](ADR-007-Replaceable-Infrastructure.md)
- [ADR-011 — Notification Channels and Extensible Delivery](ADR-011 — Notification Channels and Extensible Delivery.md)
- [ADR-012 — Retry and Delivery Attempt Processing](ADR-012 — Retry and Delivery Attempt Processing.md)
- [ADR-013 — Retry Policy, Fencing and Recovery](ADR-013 — Retry Policy, Fencing and Recovery.md)
- [ADR-014 — Concurrent Notification Finalization](ADR-014 — Concurrent Notification Finalization.md)


## Диаграммы

- [Parser Pipeline](../diagrams/detailed/07-parser-pipeline.puml)
- [Matching Pipeline](../diagrams/detailed/08-matching-pipeline.puml)
- [Notification Pipeline](../diagrams/detailed/09-notification-pipeline.puml)

---

| ⬅ Предыдущий | 📚 ADR | 🏠 README | ➡ Следующий |
|-------------|---------|-----------|-------------|
| [ADR-002](ADR-002-Canonical-Address-Model.md) | [ADR Index](README.md) | [Документация](../README.md) | [ADR-004](ADR-004-OutageProvider-Architecture.md) |