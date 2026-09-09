# ADR-011 — Каналы уведомлений и расширяемая доставка

**Статус:** Accepted

**Дата:** 2026-09-08

**Версия:** 1.0

---

# Назначение

Зафиксировать архитектурное решение для поддержки нескольких каналов уведомлений в BlackoutRadar.

Решение должно обеспечивать:

* поддержку нескольких каналов уведомлений;
* возможность добавления новых каналов;
* отсутствие зависимости Domain Model от конкретных каналов;
* отсутствие изменений Matching Engine при добавлении нового канала;
* отсутствие изменений Parser при добавлении нового канала;
* отсутствие изменений уже существующих Delivery Adapter при добавлении нового канала;
* разделение пользовательских настроек каналов и `Notification`;
* совместимость с существующим lifecycle `Notification`;
* основу для последующей реализации `Delivery` и Retry.

---

# Контекст

Текущая архитектура определяет поток:

```text
Match
  ↓
Application / Processing Flow
  ↓
Notification
  ↓
Notification Engine
  ↓
Delivery Adapter
  ↓
External Delivery Provider
```

`Notification` представляет необходимость уведомления пользователя.

`Notification` не знает о конкретном канале доставки.

`Notification Engine` отвечает за обработку и доставку Notification.

`Delivery Adapter` выполняет техническую доставку через конкретный канал.

Такое разделение соответствует принципу Replaceable Infrastructure: Email, Telegram и другие способы доставки являются инфраструктурными компонентами и могут заменяться без изменения бизнес-логики.

---

# Проблема

Для реализации Notification Engine необходимо определить:

1. где хранятся пользовательские каналы;
2. как один пользователь может иметь несколько каналов;
3. как определяется тип канала;
4. как Notification Engine получает доступные каналы;
5. как выбирается конкретный Delivery Adapter;
6. как добавлять новые каналы без изменения Domain Model;
7. как отделить настройки канала от состояния конкретной Notification.

Текущая `Notification` содержит:

```text
id
subscription
powerOutage
message
status
```

и не содержит channel-specific state.

Это решение сохраняется.

---

# Решение

## 1. Канал уведомления является отдельной моделью

Вводится отдельная модель `NotificationChannel`, представляющая пользовательский способ получения уведомлений.

Концептуальная структура:

```text
NotificationChannel
-------------------
id
user
type
destination
enabled
```

Назначение полей:

* `id` — идентификатор канала;
* `user` — пользователь, которому принадлежит канал;
* `type` — расширяемый идентификатор типа канала;
* `destination` — адрес назначения;
* `enabled` — разрешена ли доставка через данный канал.

---

# 2. NotificationChannel не является частью Notification

`Notification` не получает:

```text
channel
channelType
destination
deliveryStatus
retryCount
```

`Notification` продолжает представлять сам факт необходимости уведомления.

Таким образом сохраняется существующее правило:

```text
Subscription + PowerOutage
        ↓
    Notification
```

и существующий lifecycle:

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

---

# 3. Тип канала является расширяемым идентификатором

Тип канала не фиксируется в Domain Model перечислением:

```text
EMAIL
TELEGRAM
SMS
...
```

Вместо этого используется расширяемый идентификатор.

Например:

```text
email
telegram
sms
push
webhook
```

Конкретный набор поддерживаемых каналов определяется Application / Infrastructure.

Это позволяет добавлять новый канал без изменения Domain Model.

---

# 4. Один пользователь может иметь несколько каналов

Модель поддерживает несколько каналов одного пользователя:

```text
User
 ├── email → personal@example.com
 ├── email → work@example.com
 └── telegram → 123456789
```

Количество каналов не ограничивается двумя или каким-либо заранее заданным набором.

---

# 5. Допускается несколько каналов одного типа

Один пользователь может иметь несколько каналов одного типа.

Например:

```text
User
 ├── email → personal@example.com
 ├── email → work@example.com
 └── telegram → 123456789
```

Запрещается только точный дубликат:

```text
user + type + destination
```

Физическое ограничение:

```text
UNIQUE(user_id, type, destination)
```

---

# 6. Каналы относятся к пользователю

В первой реализации `NotificationChannel` является пользовательской настройкой.

Связь:

```text
User
  │
  │ 1:N
  ▼
NotificationChannel
```

Subscription не получает собственный список технических каналов в рамках TASK 25A.

Notification Engine использует каналы пользователя при обработке Notification.

Если в будущем потребуется различать каналы на уровне отдельных Subscription, это будет отдельным архитектурным решением.

---

# 7. NotificationChannel и Delivery имеют разные назначения

`NotificationChannel` отвечает на вопрос:

> Через какой способ и куда пользователь разрешил получать уведомления?

Будущий `Delivery` отвечает на вопрос:

> Что произошло при доставке конкретной Notification через конкретный канал?

Концептуально:

```text
User
  ↓
NotificationChannel
  ↓
определяет доступный способ доставки

Notification
  ↓
Notification Engine
  ↓
Delivery
  ↓
конкретная доставка
```

`Delivery` не входит в TASK 25A.

---

# 8. Многоканальная доставка

Одна Notification может быть предназначена для нескольких каналов:

```text
Notification
   ├── Email
   └── Telegram
```

При этом одна Notification остаётся одной Notification.

Не создаются отдельные Notification для каждого канала.

Это сохраняет правило:

```text
Subscription + PowerOutage
        ↓
не более одного Notification
```

---

# 9. Будущий Delivery

Для независимого отслеживания результата доставки предполагается отдельная модель `Delivery`.

Концептуально:

```text
Notification
    │
    ├── Delivery → Email
    │
    └── Delivery → Telegram
```

Именно `Delivery` в дальнейшем должен позволить различать результаты:

```text
Email    → SENT
Telegram → FAILED
```

`Notification.status` не используется для хранения отдельного состояния каждого канала.

Точная модель `Delivery`, её lifecycle и история попыток определяются последующими TASK.

---

# 10. Notification Engine

Notification Engine:

1. получает Notification;
2. определяет доступные пользовательские каналы;
3. выбирает включённые каналы;
4. определяет соответствующий способ доставки;
5. передаёт данные через общий Delivery Port;
6. обрабатывает результат;
7. управляет lifecycle Notification;
8. в будущем управляет Retry.

Notification Engine не должен содержать жёсткую зависимость от конкретных адаптеров.

Не допускается архитектура:

```text
if email
    ...
else if telegram
    ...
else if sms
    ...
```

как основной механизм расширения.

---

# 11. Delivery Port

Между Notification Engine и конкретными Delivery Adapter вводится Application-level порт.

```text
Notification Engine
        ↓
    Delivery Port
        ↓
  Delivery Adapter
```

Application определяет контракт.

Infrastructure предоставляет реализации.

Например:

```text
Delivery Port
      ↓
 ┌────┴─────────────┐
 ↓                  ↓
Email Adapter    Telegram Adapter
```

В будущем:

```text
SMS Adapter
Push Adapter
Webhook Adapter
...
```

могут подключаться через тот же архитектурный механизм.

---

# 12. Реестр каналов

Для сопоставления типа канала с соответствующим обработчиком используется реестр.

Концептуально:

```text
channel type
     ↓
channel registry
     ↓
Delivery Adapter
```

Например:

```text
email
  ↓
Email Adapter

telegram
  ↓
Telegram Adapter
```

Notification Engine не должен знать конкретный список всех возможных каналов в виде разрастающейся условной логики.

Добавление нового канала должно выполняться добавлением новой реализации и её регистрации.

---

# 13. Добавление нового канала

Добавление нового канала должно быть локальным изменением.

Например, для нового `sms`:

```text
1. добавить тип канала;
2. реализовать SMS Delivery Adapter;
3. зарегистрировать Adapter;
4. реализовать техническую отправку;
5. добавить необходимые настройки;
6. добавить тесты.
```

При этом не изменяются:

```text
Notification
Subscription
Matching Engine
Parser
Email Adapter
Telegram Adapter
```

Если новый канал требует новых бизнес-правил, которых нет в существующем контракте, создаётся отдельный ADR.

---

# 14. Persistence

Настройки каналов хранятся отдельно от пользователя.

Концептуальная таблица:

```text
notification_channel
--------------------
id UUID PK
user_id UUID FK NOT NULL
type VARCHAR NOT NULL
destination VARCHAR NOT NULL
enabled BOOLEAN NOT NULL
created_at TIMESTAMP WITH TIME ZONE NOT NULL
updated_at TIMESTAMP WITH TIME ZONE NOT NULL
```

Ограничения:

```text
PRIMARY KEY(id)

FOREIGN KEY(user_id)
    REFERENCES user(id)

UNIQUE(user_id, type, destination)
```

Индекс:

```text
INDEX(user_id)
```

Все изменения Database Schema выполняются только через Liquibase.

Persistence Entity отделена от Domain Model.

---

# 15. Изменение пользовательского канала

Изменение:

```text
destination
enabled
```

изменяет пользовательскую настройку.

Оно не должно изменять уже созданную Notification.

Вопрос фиксации конкретного назначения для отдельной доставки относится к будущему `Delivery` и определяется при его реализации.

---

# 16. Ответственность компонентов

## User

Владеет пользовательскими настройками и Subscription.

Не выполняет техническую доставку.

## NotificationChannel

Хранит пользовательскую настройку канала.

Не выполняет доставку.

## Subscription

Определяет интерес пользователя к отключениям.

Не знает о SMTP, Telegram API и других технологиях доставки.

## Notification

Представляет необходимость уведомления.

Не знает:

* конкретный канал;
* Email;
* Telegram;
* SMTP;
* Delivery Adapter;
* Retry;
* количество попыток.

## Notification Engine

Отвечает за:

* обработку Notification;
* определение каналов;
* выбор включённых каналов;
* вызов Delivery Port;
* обработку результата;
* lifecycle Notification;
* будущий Retry.

## Delivery Port

Определяет Application-контракт доставки.

## Delivery Adapter

Выполняет техническую доставку через конкретный канал.

Не принимает бизнес-решение о необходимости Notification.

---

# 17. Архитектурная расширяемость

Главное правило:

> Добавление нового канала не должно требовать изменения Domain Model.

Целевая структура:

```text
                         ┌── Email Adapter
                         │
Notification Engine ─────┼── Telegram Adapter
                         │
                         ├── SMS Adapter
                         │
                         ├── Push Adapter
                         │
                         └── Future Adapter
```

Через общий контракт:

```text
Notification Engine
        ↓
   Delivery Port
        ↓
   Channel Registry
        ↓
Delivery Adapter
```

Архитектура не ограничивает количество каналов заранее.

---

# 18. Рассмотренные варианты

## Вариант 1 — канал внутри Notification

```text
Notification
    └── channel
```

**Отклонён.**

Причины:

* канал является способом доставки;
* Notification представляет необходимость уведомления;
* одна Notification может доставляться через несколько каналов;
* состояние отдельных каналов нельзя корректно выразить одним полем Notification;
* это смешивает предметную сущность и технический способ доставки.

---

## Вариант 2 — Email как единственный канал

```text
Notification Engine
        ↓
Email Adapter
```

**Отклонён.**

Такой подход фиксирует один канал и не создаёт необходимой основы для расширения.

---

## Вариант 3 — тип канала как enum в Domain

```text
enum ChannelType {
    EMAIL,
    TELEGRAM
}
```

**Отклонён.**

Добавление нового канала потребовало бы изменения Domain Model.

Это противоречит принципу Replaceable Infrastructure.

---

## Вариант 4 — отдельная NotificationChannel + общий Delivery Port

```text
User
 ↓
NotificationChannel

Notification
 ↓
Notification Engine
 ↓
Delivery Port
 ↓
Channel Registry
 ↓
Delivery Adapter
```

**Принят.**

Вариант обеспечивает:

* несколько каналов;
* несколько каналов одного типа;
* независимость Notification от канала;
* независимость Domain от конкретных технологий;
* расширение без изменения существующих компонентов;
* основу для последующей реализации Delivery и Retry.

---

# 19. Ограничения данного ADR

ADR не определяет:

* Retry Policy;
* Delivery Attempt;
* количество попыток;
* backoff;
* временные и постоянные ошибки;
* расписание повторной доставки;
* SMTP;
* конкретный Email Provider;
* Telegram API;
* конкретный Telegram Provider;
* REST API управления каналами;
* авторизацию управления каналами;
* каналы непосредственно на уровне Subscription;
* окончательную модель Delivery.

Эти вопросы определяются отдельными TASK или ADR.

---

# 20. Влияние

Решение затрагивает:

* Application Layer;
* Notification Engine;
* пользовательские настройки;
* Persistence;
* Database Schema;
* Delivery Infrastructure.

Не изменяет архитектурные обязанности:

* Matching Engine;
* Parser;
* PowerOutage;
* Address Model;
* существующий Notification lifecycle.

---

# 21. Совместимость с существующими архитектурными решениями

ADR-011 сохраняет следующие существующие правила:

```text
Infrastructure
      ↓
Application
      ↓
Domain
```

Domain не зависит от инфраструктурных технологий.

Новый канал должен быть заменяемым инфраструктурным компонентом.

Добавление нового канала не должно изменять:

* Domain Model;
* Matching Engine;
* Parser;
* существующие Delivery Adapter.

Это соответствует принципам Domain First и Replaceable Infrastructure.

---

# 22. Связанные документы

* ADR-001 — Domain First Architecture
* ADR-003 — Outage Processing Pipeline
* ADR-006 — Matching Engine
* ADR-007 — Replaceable Infrastructure
* 01-ARCHITECTURE
* 02-DOMAIN_MODEL
* 03-DATABASE
* 06-NOTIFICATION_ENGINE
* TASK_PLAN
* TASK_LOG

---

# Статус

**Accepted**
