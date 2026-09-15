# ADR-014 — Concurrent Notification Finalization

**Status:** Accepted

**Date:** 2026-09-13

---

# Context

В архитектуре BlackoutRadar один `Notification` может иметь несколько независимых `NotificationDelivery`.

Например:

```text
Notification
│
├── NotificationDelivery → Email #1
├── NotificationDelivery → Email #2
└── NotificationDelivery → Telegram #1
```

Каждая `NotificationDelivery` обрабатывается независимо через Retry Processing.

Состояние конкретной доставки:

```text
READY
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

или при разрешённом Retry:

```text
PROCESSING
    ↓
READY
    ↓
PROCESSING
```

При этом итоговое состояние `Notification` зависит от состояния всех его `NotificationDelivery`.

Действующие правила:

```text
все NotificationDelivery → SENT
        ↓
Notification → SENT
```

```text
все NotificationDelivery завершены
и хотя бы одна → FAILED
        ↓
Notification → FAILED
```

```text
существует READY или PROCESSING
        ↓
Notification остаётся PROCESSING
```

Эти правила уже определены общей моделью Notification Engine и ADR-012.

Проблема возникает при параллельном завершении нескольких `NotificationDelivery`.

Например:

```text
Delivery #1 worker              Delivery #2 worker
       │                                │
       ├── SENT                         │
       │                                ├── SENT
       │                                │
       ├── проверить доставки           │
       │                                ├── проверить доставки
       │                                │
       └──────────────┬─────────────────┘
                      ↓
              конкурентное решение
```

Если каждый worker независимо прочитает состояние доставок и самостоятельно изменит `Notification`, возможна гонка.

Например, один worker может увидеть:

```text
Email  → SENT
Telegram → PROCESSING
```

и оставить `Notification` в `PROCESSING`.

Одновременно другой worker может завершить Telegram и принять другое решение.

Поэтому итоговое состояние `Notification` должно определяться через сериализованное короткое действие на уровне PostgreSQL.

При этом сами `NotificationDelivery` должны продолжать обрабатываться независимо и параллельно.

---

# Decision

## 1. Финализация Notification является отдельной операцией

После успешного fenced-завершения `NotificationDelivery` выполняется попытка финализации соответствующего `Notification`.

Финализация не является частью Retry Policy.

Retry Policy отвечает только за решение:

```text
Retry или не Retry
```

Финализация отвечает только за определение итогового состояния `Notification`.

---

## 2. Финализация выполняется только после успешного fenced completion

Worker имеет право инициировать финализацию только если он успешно сохранил конечное состояние своей `NotificationDelivery` с использованием действующего ownership token.

Порядок:

```text
NotificationDelivery
        ↓
Retry Processing
        ↓
fenced final state update
        ↓
успешное сохранение
        ↓
Notification finalization
```

Если fenced update не выполнен:

```text
Notification finalization
```

не запускается данным worker.

Устаревший worker не должен изменять итоговое состояние `Notification`.

## 2.1. Fenced completion и финализация выполняются в одной короткой транзакции

Fenced completion `NotificationDelivery` и финализация `Notification` выполняются в одной короткой транзакции базы данных. Внешняя доставка выполняется вне этой транзакции. Это исключает состояние, при котором terminal `NotificationDelivery` уже зафиксирована, а обязательная финализация `Notification` не была зафиксирована.

Если транзакция финального завершения откатывается, `NotificationDelivery` не остаётся терминально завершённой в результате этой операции.

---

## 3. Для финализации используется блокировка строки Notification в PostgreSQL

Финализатор получает короткую блокировку строки соответствующего `Notification`.

Концептуально:

```text
begin transaction
        ↓
lock Notification row
        ↓
read current NotificationDelivery states
        ↓
calculate final state
        ↓
update Notification if required
        ↓
commit
```

Блокировка используется только на время принятия и сохранения итогового решения.

Она не удерживается во время:

* Delivery Adapter;
* внешнего сетевого вызова;
* Retry Processing;
* создания `DeliveryAttempt`;
* другой длительной работы.

Таким образом, техническая доставка разных `NotificationDelivery` может продолжаться параллельно.

---

## 4. Финализатор всегда читает актуальное состояние всех доставок

Финальное решение не строится только на результате текущей доставки.

После получения блокировки `Notification` финализатор повторно получает состояние всех связанных `NotificationDelivery`.

Проверяются как минимум:

```text
READY
PROCESSING
SENT
FAILED
```

Это необходимо для корректной работы при конкурентном завершении нескольких доставок.

---

## 5. Правило определения итогового состояния

Если существует хотя бы одна:

```text
READY
```

или:

```text
PROCESSING
```

то:

```text
Notification = PROCESSING
```

Финализация не переводит `Notification` в конечное состояние.

---

Если все `NotificationDelivery` имеют:

```text
SENT
```

то:

```text
Notification = SENT
```

---

Если все `NotificationDelivery` завершены и хотя бы одна имеет:

```text
FAILED
```

то:

```text
Notification = FAILED
```

Таким образом, приоритет проверки:

```text
READY / PROCESSING
        ↓
Notification = PROCESSING
```

затем:

```text
all SENT
        ↓
Notification = SENT
```

затем:

```text
all terminal + at least one FAILED
        ↓
Notification = FAILED
```

Если у `Notification` нет ни одной связанной `NotificationDelivery`
(например, нет включённых каналов), итоговым состоянием является:

```text
Notification = FAILED
```

---

## 6. Retry state не переносится в Notification

Финализация не вводит новых состояний `Notification`.

Не добавляются:

```text
RETRY_PENDING
RETRYING
DELIVERY_FAILED
```

и другие Retry-specific состояния.

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

Retry state остаётся исключительно на уровне `NotificationDelivery`.

---

## 7. NotificationDelivery продолжает обрабатываться независимо

Финализация `Notification` не должна сериализовать саму доставку.

Допускается:

```text
NotificationDelivery #1
        ↓
Retry Processing
        ↓
Delivery Adapter
```

одновременно с:

```text
NotificationDelivery #2
        ↓
Retry Processing
        ↓
Delivery Adapter
```

Сериализуется только короткое принятие итогового решения для одного `Notification`.

---

## 8. JVM locks не используются

Для защиты финализации запрещены:

* `synchronized`;
* JVM locks;
* `ReentrantLock`;
* in-memory locks;
* локальные карты занятых `Notification`;
* другие механизмы, работающие только внутри одного экземпляра JVM.

Защита должна работать между несколькими экземплярами приложения.

Для этого используется PostgreSQL.

---

## 9. Fencing и финализация решают разные задачи

`processingToken` и fencing защищают конкретную `NotificationDelivery` от устаревшего worker.

Финализация `Notification` решает другую задачу:

```text
каким должно быть итоговое состояние Notification
после изменения нескольких NotificationDelivery
```

Поэтому:

```text
NotificationDelivery fencing
        ≠
Notification finalization
```

Оба механизма используются совместно.

---

# Итоговый поток

```text
NotificationDelivery
        ↓
atomic claim
        ↓
PROCESSING
        ↓
DeliveryAttempt
        ↓
DeliveryPort
        ↓
Channel Registry
        ↓
Delivery Adapter
        ↓
Delivery Result
        ↓
Retry Policy
        ↓
fenced final state update
        ↓
Notification finalization
        ↓
lock Notification row
        ↓
read all NotificationDelivery
        ↓
SENT / FAILED / PROCESSING
```

---

# Consequences

## Положительные

* итоговое состояние `Notification` определяется по всем его доставкам;
* конкурентное завершение нескольких доставок не приводит к некорректной финализации;
* разные `NotificationDelivery` сохраняют независимую обработку;
* внешний вызов не выполняется под блокировкой `Notification`;
* решение работает между несколькими экземплярами приложения;
* существующие atomic claim и fencing из ADR-013 сохраняются;
* Retry state остаётся вне `Notification`.

## Отрицательные

* появляется дополнительная операция финализации;
* требуется короткая блокировка строки `Notification` в PostgreSQL;
* после получения блокировки необходимо повторно читать состояние `NotificationDelivery`;
* финализация требует отдельного тестирования конкурентных сценариев.

---

# Explicitly Out of Scope

Данный ADR не изменяет:

* модель `Notification`;
* lifecycle `Notification`;
* модель `NotificationDelivery`;
* модель `DeliveryAttempt`;
* Retry Policy;
* atomic claim;
* ownership token;
* fencing;
* Recovery;
* DeliveryPort;
* Channel Registry;
* Delivery Adapter;
* правила выбора `NotificationChannel`;
* at-least-once semantics.


Данный ADR также не определяет конкретный SQL-запрос или конкретный метод Persistence Port. Они являются частью реализации Persistence.
