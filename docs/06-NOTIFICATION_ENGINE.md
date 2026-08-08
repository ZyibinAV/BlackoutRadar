# Notification Engine

> Подсистема доставки уведомлений пользователям.

---

# Назначение

Документ описывает архитектуру подсистемы уведомлений, поддерживаемые каналы доставки и жизненный цикл уведомления.

Notification Engine отвечает на вопрос:

> **«Как уведомление доставляется пользователю?»**

---

# Навигация

| Раздел | Ссылка |
|---------|--------|
| ⬅ Предыдущий | [05-MATCHING_ENGINE](05-MATCHING_ENGINE.md) |
| 🏠 Документация | [README](README.md) |
| ➡ Следующий | [07-SECURITY](07-SECURITY.md) |

---

# Связанные ADR

- [ADR-003 — Outage Processing Pipeline](adr/ADR-003-Outage-Processing-Pipeline.md)
- [ADR-007 — Replaceable Infrastructure](adr/ADR-007-Replaceable-Infrastructure.md)

---

# Связанные диаграммы

- [Notification Pipeline](diagrams/detailed/09-notification-pipeline.puml)

---

# Notification Engine

---

# Назначение

Notification Engine
отвечает
за доставку уведомлений
пользователям.

Подсистема получает
готовые Notification
из Outage Processing Pipeline
и обеспечивает
их доставку
по выбранному пользователем каналу.

Подробнее:

[ADR-003 — Outage Processing Pipeline](adr/ADR-003-Outage-Processing-Pipeline.md)

[ADR-006 — Matching Engine](adr/ADR-006-Matching-Engine.md)

---

# Место в архитектуре

Notification Engine
является
заключительным этапом
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

Notification

↓

Notification Engine

---

# Основные принципы

Notification Engine:

- не ищет совпадения;

- не получает данные
  из внешних источников;

- не принимает
  бизнес-решения;

- не изменяет
  предметную область.

Его задача —

доставить Notification.

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

Архитектура
допускает
добавление
новых каналов.

---

## Основные принципы

Добавление
нового канала

не требует
изменения
Matching Engine
или Parser.

Новый канал
подключается
как новый Adapter.

---

# Процесс доставки

После создания Notification

Notification Engine

выполняет
следующие этапы.

---

## Этап 1

Получение Notification.

---

## Этап 2

Определение
канала доставки.

---

## Этап 3

Подготовка сообщения.

---

## Этап 4

Передача сообщения
соответствующему Adapter.

---

## Этап 5

Получение результата доставки.

---

## Этап 6

Обновление
статуса Notification.

---

# Обработка ошибок

Ошибки доставки
не должны
останавливать
работу системы.

---

## Основные принципы

### Независимость доставки

Ошибка доставки
одному пользователю

не влияет
на остальных.

---

### Повторная отправка

Временные ошибки

могут быть
повторно обработаны.

---

### История

Информация
о попытках доставки

сохраняется
для анализа.

---

### Логирование

Все ошибки доставки

подлежат логированию.

---

### Разделение ответственности

Notification Engine

не принимает решение

о необходимости уведомления.

Он только
выполняет доставку.

---

# Расширяемость

Архитектура Notification Engine
предусматривает
подключение
новых способов доставки.

---

## Добавление нового канала

Для подключения
нового способа доставки
необходимо:

1. Реализовать Adapter.

2. Зарегистрировать
   новый канал.

После этого

Notification Engine

получает возможность
использовать
новый способ доставки.

---

## Принципы расширяемости

Добавление нового канала

не требует изменения:

- Matching Engine;

- Parser;

- Domain Layer;

- существующих Adapter.

---

# Связанные документы

- [ADR-003 — Outage Processing Pipeline](adr/ADR-003-Outage-Processing-Pipeline.md)

- [ADR-006 — Matching Engine](adr/ADR-006-Matching-Engine.md)

- [ADR-007 — Replaceable Infrastructure](adr/ADR-007-Replaceable-Infrastructure.md)

- [02-DOMAIN_MODEL](02-DOMAIN_MODEL.md)

- [05-MATCHING_ENGINE](05-MATCHING_ENGINE.md)

- [ADR-007 — Replaceable Infrastructure](ADR-007-Replaceable-Infrastructure.md)

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