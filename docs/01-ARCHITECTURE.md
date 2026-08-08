# Architecture

> Общая архитектура системы BlackoutRadar.

---

# Назначение

Документ описывает архитектуру системы, основные подсистемы, принципы их взаимодействия и границы ответственности.

Architecture отвечает на вопрос:

> **«Как устроена система?»**

---

# Навигация

| Раздел | Ссылка |
|---------|--------|
| ⬅ Предыдущий | [00.5-GLOSSARY](00.5-GLOSSARY.md) |
| 🏠 Документация | [README](README.md) |
| ➡ Следующий | [02-DOMAIN_MODEL](02-DOMAIN_MODEL.md) |

---

# Связанные ADR

- [ADR-001 — Domain First Architecture](adr/ADR-001-Domain-First-Architecture.md)
- [ADR-003 — Outage Processing Pipeline](adr/ADR-003-Outage-Processing-Pipeline.md)
- [ADR-007 — Replaceable Infrastructure](adr/ADR-007-Replaceable-Infrastructure.md)

---

# Связанные диаграммы

- [System Context](diagrams/overview/01-system-context.puml)
- [Container](diagrams/overview/02-container.puml)
- [Package Responsibility](diagrams/overview/03-package-responsibility.puml)

---

# Архитектура BlackoutRadar

---

# Назначение

Документ описывает общую архитектуру системы
и взаимодействие основных подсистем.

Документ не содержит
детального описания
отдельных компонентов.

Архитектурные решения
зафиксированы
в ADR.

---

# Архитектурный стиль

BlackoutRadar представляет собой
модульный монолит
со слоистой архитектурой,
построенной
по принципу Domain First.

Основная бизнес-логика
расположена
в Domain Layer.

Инфраструктура
рассматривается
как внешний слой.

Подробнее:

[ADR-001 — Domain First Architecture](adr/  ADR-001-Domain-First-Architecture.md)

---

# Общая схема системы

                     +-----------------------+
                     |  External Sources     |
                     +-----------+-----------+
                                 |
                                 v
                      +----------------------+
                      |   OutageProvider     |
                      +----------+-----------+
                                 |
                                 v
                      +----------------------+
                      |   ParsedOutage       |
                      +----------+-----------+
                                 |
                                 v
                      +----------------------+
                      | DuplicateResolver    |
                      +----------+-----------+
                                 |
                                 v
                      +----------------------+
                      |    PowerOutage       |
                      +----------+-----------+
                                 |
                                 v
                      +----------------------+
                      | CandidateFinder      |
                      +----------+-----------+
                                 |
                                 v
                      +----------------------+
                      |  Matching Engine     |
                      +----------+-----------+
                                 |
                                 v
                      +----------------------+
                      |    Notification      |
                      +----------+-----------+
                                 |
                                 v
                      +----------------------+
                      | Notification Engine  |
                      +----------------------+

---

# Основные подсистемы

## Domain

Ядро системы.

Содержит:

- предметную область;
- бизнес-правила;
- алгоритмы сопоставления.

---

## Address Subsystem

Отвечает за:

- нормализацию;
- хранение;
- поиск адресов.

Подробнее:

[ADR-002 — Canonical Address Model](adr/ADR-002-Canonical-Address-Model.md)

---

## Provider Subsystem

Отвечает
за получение информации
из внешних источников.

Подробнее:

[ADR-004 — OutageProvider Architecture](adr/ADR-004-OutageProvider-Architecture.md)

---

## Processing Pipeline

Обрабатывает информацию
об отключениях.

Подробнее:

[ADR-003 — Outage Processing Pipeline](adr/ADR-003-Outage-Processing-Pipeline.md)

---

## Matching Engine

Определяет,
какие подписки
соответствуют найденному отключению.

Подробнее:

[ADR-006 — Matching Engine](adr/ADR-006-Matching-Engine.md)

---

## Notification Engine

Отвечает
исключительно
за доставку уведомлений.

---

## Infrastructure

Обеспечивает работу системы.

Содержит:

- PostgreSQL;
- MinIO;
- Spring Security;
- Email;
- Telegram;
- Scheduler.

Подробнее:

[ADR-007 — Replaceable Infrastructure](adr/ADR-007-Replaceable-Infrastructure.md)

---

# Взаимодействие подсистем

External Sources

↓

Provider

↓

Pipeline

↓

Domain

↓

Notification

↓

Infrastructure

---

# Основные принципы

Архитектура проекта
строится
на следующих принципах.

- Domain First

- Pipeline Processing

- Replaceable Infrastructure

- Canonical Address Model

- Single Responsibility Principle

- Dependency Inversion Principle

Все принципы
подробно описаны
в ADR.

---

# Связанные документы

[00-VISION](00-VISION.md)

[00.5-GLOSSARY](00.5-GLOSSARY.md)

[ADR-001 — Domain First Architecture](adr/ADR-001-Domain-First-Architecture.md)

[ADR-002 — Canonical Address Model](adr/ADR-002-Canonical-Address-Model.md)

[ADR-003 — Outage Processing Pipeline](adr/ADR-003-Outage-Processing-Pipeline.md)

[ADR-004 — OutageProvider Architecture](adr/ADR-004-OutageProvider-Architecture.md)

[ADR-005 — PowerOutage Event Model](adr/ADR-005-PowerOutage-Event-Model.md)

[ADR-006 — Matching Engine](adr/ADR-006-Matching-Engine.md)

[ADR-007 — Replaceable Infrastructure](adr/ADR-007-Replaceable-Infrastructure.md)

[02-DOMAIN_MODEL](02-DOMAIN_MODEL.md)

[03-DATABASE](03-DATABASE.md)

---

# См. также

## Документы

- [00-VISION](00-VISION.md)
- [02-DOMAIN_MODEL](02-DOMAIN_MODEL.md)
- [03-DATABASE](03-DATABASE.md)

## ADR

- [ADR-001 — Domain First Architecture](adr/ADR-001-Domain-First-Architecture.md)
- [ADR-003 — Outage Processing Pipeline](adr/ADR-003-Outage-Processing-Pipeline.md)
- [ADR-007 — Replaceable Infrastructure](adr/ADR-007-Replaceable-Infrastructure.md)

## Диаграммы

- [System Context](diagrams/overview/01-system-context.puml)
- [Container](diagrams/overview/02-container.puml)
- [Package Responsibility](diagrams/overview/03-package-responsibility.puml)

---

| ⬅ Предыдущий | 🏠 README | ➡ Следующий |
|-------------|-----------|-------------|
| [00.5-GLOSSARY](00.5-GLOSSARY.md) | [README](README.md) | [02-DOMAIN_MODEL](02-DOMAIN_MODEL.md) |