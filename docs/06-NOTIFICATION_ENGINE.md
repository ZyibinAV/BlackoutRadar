# Notification Engine

> Подсистема доставки уведомлений пользователям.

---

# Назначение

Документ описывает архитектуру
подсистемы уведомлений,
поддерживаемые каналы доставки,
границу ответственности
Notification Engine
и процесс обработки Notification.

Notification Engine отвечает
на вопрос:

> **«Как Notification доставляется пользователю?»**

Notification Domain отвечает
за предметный объект Notification
и его доменные правила.

Notification Engine отвечает
за обработку и доставку
готового Notification.

---

# Навигация

| Раздел | Ссылка |
|---------|--------|
| ⬅ Предыдущий | [05-MATCHING_ENGINE](05-MATCHING_ENGINE.md) |
| 🏠 README | [README](README.md) |
| ➡ Следующий | [07-SECURITY](07-SECURITY.md) |

---

# Связанные ADR

- [ADR-003 — Outage Processing Pipeline](adr/ADR-003-Outage-Processing-Pipeline.md)
- [ADR-006 — Matching Engine](adr/ADR-006-Matching-Engine.md)
- [ADR-007 — Replaceable Infrastructure](adr/ADR-007-Replaceable-Infrastructure.md)

---

# Связанные документы

- [02-DOMAIN_MODEL](02-DOMAIN_MODEL.md)
- [05-MATCHING_ENGINE](05-MATCHING_ENGINE.md)
- [03-DATABASE](03-DATABASE.md)

---

# Связанные диаграммы

- [Notification Pipeline](diagrams/detailed/09-notification-pipeline.puml)

---

# Notification Engine

## Назначение

Notification Engine
отвечает за обработку
и доставку уведомлений
пользователям.

Подсистема получает
готовые Notification
из Outage Processing Pipeline
и обеспечивает их обработку
через выбранный канал доставки.

Notification Engine
не создаёт Notification.

Notification Engine
не принимает решение
о необходимости уведомления.

---

# Место в архитектуре

Notification Engine
является заключительным этапом
Outage Processing Pipeline.

Pipeline имеет следующий вид:

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

Application / Processing Flow

↓

Notification

↓

Notification Engine

---

# Граница ответственности

## Notification Domain

Notification Domain отвечает за:

- Subscription;
- PowerOutage;
- текст уведомления;
- состояние Notification;
- предотвращение повторного уведомления;
- сохранение истории Notification.

Notification Domain
не зависит от:

- Spring;
- JPA;
- Hibernate;
- PostgreSQL;
- SMTP;
- Telegram;
- конкретных Delivery Adapter.

---

## Notification Engine

Notification Engine отвечает за:

- получение готового Notification;
- принятие Notification в обработку;
- определение канала доставки;
- подготовку сообщения;
- передачу сообщения Adapter;
- получение результата обработки;
- обновление состояния Notification;
- обработку ошибок доставки;
- принятие решения о повторной обработке.

Notification Engine НЕ отвечает за:

- получение outage data;
- Parser;
- DuplicateResolver;
- CandidateFinder;
- Matching;
- создание Match;
- принятие решения о необходимости Notification;
- изменение бизнес-смысла Notification.

---

# Создание Notification

Notification создаётся
в Application / Processing Flow
после успешного Match.

Notification Engine
не знает о технической реализации
Match и не зависит от объекта Match.

Pipeline:

Match

↓

Application / Processing Flow

↓

Notification

↓

Notification Engine

Таким образом,
Notification Engine получает
уже готовый Notification.

---

# Основные принципы

Notification Engine:

- не ищет совпадения;
- не получает данные
  из внешних источников;
- не принимает бизнес-решения
  о необходимости Notification;
- не изменяет предметную область;
- не зависит от конкретного
  канала доставки;
- использует отдельные Adapter
  для каналов доставки.

Его задача —

> **обработать и доставить Notification.**

---

# Жизненный цикл обработки

Notification имеет
следующие состояния:

- PENDING;
- PROCESSING;
- SENT;
- FAILED.

Notification Engine
использует эти состояния
для управления обработкой.

---

## PENDING

`PENDING` означает:

> Notification создан
> и ожидает обработки
> Notification Engine.

Это исходное состояние
нового Notification.

Notification Engine
может принять
PENDING Notification
в обработку.

Переход:

PENDING

↓

PROCESSING

---

## PROCESSING

`PROCESSING` означает:

> Notification Engine
> принял Notification
> в обработку.

PROCESSING является
состоянием обработки
Notification Engine.

Оно не является
состоянием конкретного
канала доставки.

PROCESSING не определяет:

- Email;
- Telegram;
- конкретный Adapter;
- номер попытки;
- количество попыток;
- Retry Policy;
- время следующей попытки;
- техническую ошибку доставки.

После принятия Notification
в обработку Engine:

1. определяет канал;
2. подготавливает сообщение;
3. передаёт сообщение Adapter;
4. получает результат обработки;
5. устанавливает итоговое состояние.

---

## SENT

`SENT` означает:

> Notification Engine
> успешно завершил
> обработку Notification.

Успешный результат Adapter
приводит к состоянию:

PROCESSING

↓

SENT

`SENT` означает успешное
завершение операции доставки.

`SENT` не означает:

- гарантированное прочтение;
- гарантированное ознакомление;
- подтверждение пользователем.

---

## FAILED

`FAILED` означает:

> Обработка Notification
> завершилась ошибкой.

Переход:

PROCESSING

↓

FAILED

Notification при этом:

- не удаляется;
- сохраняется в истории;
- остаётся доступным
  Notification Engine
  для возможной повторной обработки.

`FAILED` не означает,
что Notification
обязательно является
окончательно неуспешным.

Notification Engine
самостоятельно определяет,
нужна ли повторная обработка.

---

# Повторная обработка

Retry является
ответственностью
Notification Engine.

Повторная обработка
не создаёт новый Notification.

Например:

FAILED

↓

Retry Decision

↓

PROCESSING

↓

SENT

Повторная обработка
не нарушает правило:

Subscription + PowerOutage

↓

не более одного Notification.

---

# Retry Policy

Retry Policy
не является частью
Notification Domain.

Notification Engine
определяет:

- является ли ошибка
  временной;
- требуется ли повторная обработка;
- когда выполнять повторную обработку;
- сколько повторных обработок
  допускается.

Конкретная Retry Policy
будет определена
при реализации:

`TASK 27 — Retry and Delivery Processing`.

До этого момента
конкретные значения:

- количества попыток;
- интервала;
- backoff;
- времени следующей обработки;
- условий прекращения retry

не фиксируются.

---

# Delivery Attempts

История попыток доставки
относится к Notification Engine.

Delivery Attempt
не является частью
текущей Domain Model Notification.

Модель хранения
Delivery Attempt
будет определена
при проектировании
Retry and Delivery Processing.

До этого момента
Notification не содержит:

- attempt number;
- retry count;
- next retry time;
- retry policy;
- delivery error details.

---

# Каналы доставки

Notification Engine
не зависит
от конкретного способа доставки.

Каждый канал
рассматривается
как отдельный Adapter.

---

## Поддерживаемые каналы

На первом этапе проекта:

- Email.

На последующих этапах:

- Telegram.

Архитектура допускает
добавление новых каналов.

---

# Adapter

Adapter отвечает
за взаимодействие
с конкретным каналом доставки.

Adapter:

- получает подготовленное
  сообщение;
- выполняет операцию доставки;
- возвращает результат
  Notification Engine.

Adapter не принимает
бизнес-решение
о необходимости Notification.

Adapter не создаёт
Notification.

---

# Процесс обработки Notification

После создания Notification
Notification Engine
выполняет следующий процесс.

---

## Этап 1 — Получение Notification

Engine получает
готовый Notification.

Notification должен
находиться в состоянии:

`PENDING`

---

## Этап 2 — Принятие в обработку

Engine принимает
Notification в обработку.

Состояние:

PENDING

↓

PROCESSING

---

## Этап 3 — Определение канала

Engine определяет
канал доставки.

Конкретный канал
не является частью
доменного объекта Notification.

---

## Этап 4 — Подготовка сообщения

Engine подготавливает
сообщение для выбранного
канала доставки.

Подготовка может учитывать
особенности конкретного Adapter.

---

## Этап 5 — Передача Adapter

Engine передаёт
подготовленное сообщение
соответствующему Adapter.

Adapter выполняет
операцию доставки.

---

## Этап 6 — Получение результата

Engine получает
результат обработки
от Adapter.

При успешном результате:

PROCESSING

↓

SENT

При ошибке:

PROCESSING

↓

FAILED

---

## Этап 7 — Retry Decision

Если Notification
получил состояние `FAILED`,
Notification Engine
может принять решение
о повторной обработке.

Retry Decision
не является
доменным решением
Notification.

---

# Обработка ошибок

Ошибки доставки
одного Notification
не должны
останавливать
обработку остальных
Notification.

---

## Независимость доставки

Ошибка обработки
одного Notification
не должна
останавливать
обработку других
Notification.

Notification Engine
обрабатывает
каждый Notification
независимо.

---

## Временные ошибки

Временные ошибки
могут быть
повторно обработаны
в соответствии
с Retry Policy.

---

## Постоянные ошибки

Постоянная ошибка
может привести
к состоянию:

`FAILED`

Notification
при этом сохраняется
в истории.

---

## Логирование

Ошибки обработки
и доставки
подлежат логированию.

Логирование
не заменяет
обработку ошибок.

---

# История

Notification сохраняется
как исторически значимый
доменный объект.

Ошибка доставки
не удаляет Notification.

История попыток доставки
относится к Notification Engine
и будет определена
отдельной моделью.

---

# Разделение ответственности

## Matching Engine

Отвечает на вопрос:

> «Нужно ли уведомить
> эту Subscription
> об этом PowerOutage?»

Результатом является:

`Match`

---

## Application / Processing Flow

Отвечает за переход:

Match

↓

Notification

Правило создания Notification
после успешного Match
находится здесь.

---

## Notification

Отвечает за:

- необходимость уведомления;
- Subscription;
- PowerOutage;
- текст;
- состояние;
- историю;
- уникальность
  Subscription + PowerOutage.

---

## Notification Engine

Отвечает на вопрос:

> «Как обработать
> и доставить этот Notification?»

---

## Delivery Adapter

Отвечает на вопрос:

> «Как выполнить доставку
> через конкретный канал?»

---

# Расширяемость

Архитектура Notification Engine
предусматривает
подключение новых
способов доставки.

---

## Добавление нового канала

Для подключения
нового способа доставки
необходимо:

1. Реализовать новый Adapter.

2. Зарегистрировать
   новый канал.

3. Подключить Adapter
   к Notification Engine.

После этого
Notification Engine
получает возможность
использовать новый
способ доставки.

---

## Принципы расширяемости

Добавление нового канала
не требует изменения:

- Matching Engine;
- Parser;
- Domain Model;
- Notification;
- существующих Adapter.

---

# Архитектурные ограничения

Notification Engine
не должен:

- создавать Notification;
- создавать Match;
- выполнять Matching;
- получать outage data;
- обращаться к внешним
  outage sources;
- изменять Subscription;
- изменять PowerOutage;
- принимать решение
  о необходимости уведомления.

Notification Engine
может изменять
только состояние Notification
в рамках определённого
процесса обработки.

---

# Текущий scope

В текущем состоянии
архитектуры определены:

- Notification;
- Notification Engine;
- Notification lifecycle;
- Notification status;
- Adapter boundary;
- Email channel;
- будущий Telegram channel;
- Retry responsibility;
- Delivery Attempt responsibility.

Не определены
и не фиксируются:

- конкретная Retry Policy;
- Delivery Attempt model;
- storage Delivery Attempt;
- scheduler retry implementation;
- backoff algorithm;
- dead-letter mechanism;
- конкретные adapter implementations.

Эти вопросы относятся
к последующим TASK.

---

# Связанные документы

- [02-DOMAIN_MODEL](02-DOMAIN_MODEL.md)
- [05-MATCHING_ENGINE](05-MATCHING_ENGINE.md)
- [03-DATABASE](03-DATABASE.md)
- [ADR-003 — Outage Processing Pipeline](adr/ADR-003-Outage-Processing-Pipeline.md)
- [ADR-006 — Matching Engine](adr/ADR-006-Matching-Engine.md)
- [ADR-007 — Replaceable Infrastructure](adr/ADR-007-Replaceable-Infrastructure.md)

---

# См. также

## Документы

- [05-MATCHING_ENGINE](05-MATCHING_ENGINE.md)
- [07-SECURITY](07-SECURITY.md)

## ADR

- [ADR-003 — Outage Processing Pipeline](adr/ADR-003-Outage-Processing-Pipeline.md)
- [ADR-007 — Replaceable Infrastructure](adr/ADR-007-Replaceable-Infrastructure.md)

## Диаграммы

- [Notification Pipeline](diagrams/detailed/09-notification-pipeline.puml)

---

| ⬅ Предыдущий | 🏠 README | ➡ Следующий |
|-------------|-----------|-------------|
| [05-MATCHING_ENGINE](05-MATCHING_ENGINE.md) | [README](README.md) | [07-SECURITY](07-SECURITY.md) |