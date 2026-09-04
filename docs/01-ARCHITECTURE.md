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

Infrastructure
рассматривается
как внешний слой.

Application Layer
координирует
прикладные сценарии
и взаимодействие
между Domain
и Infrastructure.

Подробнее:

[ADR-001 — Domain First Architecture](adr/ADR-001-Domain-First-Architecture.md)

---

# Общая схема системы

> Scheduler и Provider Registry
> находятся перед OutageProvider
> и не смешиваются
> с дальнейшим Outage Processing Pipeline.

                     +-----------------------+
                     |  External Sources     |
                     +-----------+-----------+
                                 |
                                 v
                      +----------------------+
                      |      Source          |
                      | (активные через     |
                      |  SourcePort)         |
                      +----------+-----------+
                                 |
                                 v
                      +----------------------+
                      |     Scheduler        |
                      | (cron String,        |
                      |  только активные)    |
                      +----------+-----------+
                                 |
                                 v
                      +----------------------+
                      | Provider Registry    |
                      | find(providerType)   |
                      +----------+-----------+
                                 |
                                 v
                      +----------------------+
                      | ProviderContext      |
                      | sourceId +           |
                      | String configuration |
                      +----------+-----------+
                                 |
                                 v
                      +----------------------+
                      |   OutageProvider     |
                      | providerType()       |
                      | fetch(context)       |
                      +----------+-----------+
                                 |
                                 v
                      +----------------------+
                       |   ParsedOutage       |
                       | (sourceId)           |
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
                      |       Match          |
                      +----------+-----------+
                                 |
                                 v
                      +----------------------+
                      | Application /        |
                      | Processing Flow      |
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
                      +----------+-----------+
                                 |
                                 v
                      +----------------------+
                      | Delivery Adapter     |
                      +----------------------+

---

# Основные подсистемы

## Domain

Ядро системы.

Содержит:

- предметную область;
- бизнес-правила;
- алгоритмы сопоставления.

Domain Layer
не зависит
от инфраструктурных
технологий.

---

## Application Layer

Application Layer
координирует
прикладные сценарии
и последовательность
взаимодействия
между Domain
и Infrastructure.

Application Layer:

- вызывает Domain Services;
- координирует
  последовательность
  операций;
- использует Domain Ports;
- передает результаты
  между подсистемами;
- реализует прикладные
  use cases.

Application Layer
не содержит
инфраструктурной реализации.

---

## Application / Processing Flow

Application / Processing Flow
является частью
Application Layer.

В Outage Processing Pipeline
он отвечает,
в частности,
за переход:

Match

↓

Notification

После успешного Match
Application / Processing Flow
создает Notification.

Notification
не имеет технической
зависимости от Match.

Match остается
временным результатом
Matching Engine.

Application / Processing Flow
передает готовый Notification
в Notification Engine.

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
из внешних источников
через последовательность:

```text
Source (активные через SourcePort)
    ↓
Scheduler — определяет момент запуска активного Source
    ↓
Provider Registry — находит Provider по providerType
    ↓
ProviderContext (sourceId + String configuration)
    ↓
OutageProvider — получает внешние данные
    ↓
ParsedOutage (sourceId) — возвращает внутренний результат
```

Scheduler работает
только с активными Source
и использует `schedule` как `String` cron.

`ProviderContext.configuration`
имеет тип `String`, а не `JsonNode`.

Контракт
`ProviderRegistry.find(providerType) → Optional<OutageProvider>`
и `ParsedOutage.sourceId`
реализованы в TASK 21.

Жизненный цикл расписаний:
расписания формируются однократно
при старте приложения
через `SourcePort.findAllActive()`;
изменения `Source` в БД
применяются только
после повторного планирования /
перезапуска.

Подробнее:

[ADR-004 — OutageProvider Architecture](adr/ADR-004-OutageProvider-Architecture.md)
[04-PARSER](04-PARSER.md)

---

### Matching Integration

Application-level Matching boundary,
созданная как временная часть TASK 16,
удалена после реализации TASK 24.

Текущая граница Matching:

CandidateFinder
→ Application `Candidate`
→ Subscription
→ Domain MatchingEngine
→ Match.

OutageProcessingService
не зависит от:
- MatchingBoundary;
- NoOpMatchingBoundary;
- PowerOutageMatchingService.

Matching Engine является фактической
Domain-level реализацией Matching.
## Processing Pipeline

Обрабатывает информацию
об отключениях.

Основные этапы
(начинается с уже полученного `ParsedOutage`):

- DuplicateResolver;
- PowerOutage;
- CandidateFinder;
- Matching Engine;
- Application / Processing Flow;
- Notification;
- Notification Engine.

Получение данных
`Source` → `Scheduler` → `Provider Registry` → `ProviderContext` → `OutageProvider` → `ParsedOutage`
относится к Provider Subsystem
и находится перед Pipeline.

Подробнее:

[ADR-003 — Outage Processing Pipeline](adr/ADR-003-Outage-Processing-Pipeline.md)

---

## Matching Engine

Определяет,
какие подписки
соответствуют
найденному отключению.

Результатом
Matching Engine
является Match.

Matching Engine:

- выполняет сопоставление;
- работает
  с каноническими данными;
- формирует Match.

Matching Engine
не отвечает за:

- создание Notification;
- выбор канала доставки;
- доставку;
- Retry.

CandidateFinder является Application-level
механизмом предварительного поиска.

CandidateFinder возвращает временные
Application-level `Candidate`.

`Candidate` не передается в Domain Matching Engine.

Application / Processing Flow
извлекает `Subscription` из `Candidate`
и передает в Matching Engine:

CandidateFinder
→ Candidate
→ Subscription
→ Matching Engine
→ Match.

Matching Engine принимает:
- PowerOutage;
- List<Subscription>.

Matching Engine не зависит от Application `Candidate`.

OutageProcessingService
координирует переход:

ParsedOutage
→ ParsedOutageProcessor
→ DuplicateResolver
→ PowerOutage
→ CandidateFinder
→ Matching Engine
→ Match.

При `DuplicateResolver.Decision.IGNORE`
CandidateFinder и Matching Engine
не вызываются.

Полученный `Match` возвращается
в Application / Processing Flow
для следующего этапа Notification.

Подробнее:

[ADR-006 — Matching Engine](adr/ADR-006-Matching-Engine.md)

---

## Notification

Notification
является доменным объектом,
представляющим необходимость
уведомить пользователя
о найденном совпадении.

Notification:

- относится
  к Subscription;
- относится
  к PowerOutage;
- имеет собственный lifecycle;
- не зависит технически
  от Match;
- не определяет
  канал доставки.

Основные состояния:

- PENDING;
- PROCESSING;
- SENT;
- FAILED.

Подробнее:

[02-DOMAIN_MODEL](02-DOMAIN_MODEL.md)

---

## Notification Engine

Отвечает
исключительно
за обработку
и доставку Notification.

Notification Engine:

- принимает готовый Notification;
- принимает Notification
  в обработку;
- определяет канал;
- подготавливает сообщение;
- передает сообщение Adapter;
- получает результат;
- обновляет состояние Notification;
- принимает решение
  о повторной обработке.

Notification Engine
не отвечает за:

- получение outage data;
- Matching;
- создание Match;
- создание Notification.

---

## Delivery Adapter

Delivery Adapter
обеспечивает взаимодействие
с конкретным каналом доставки.

Примеры:

- Email Adapter;
- Telegram Adapter.

Adapter:

- выполняет конкретную
  операцию доставки;
- возвращает результат
  Notification Engine.

Adapter не содержит
бизнес-решения
о необходимости Notification.

---

## Infrastructure

Обеспечивает работу системы.

Содержит:

- PostgreSQL;
- MinIO;
- Spring Security;
- Email;
- Telegram;
- Scheduler;
- Delivery Adapters.

Подробнее:

[ADR-007 — Replaceable Infrastructure](adr/ADR-007-Replaceable-Infrastructure.md)

---

## Persistence

Persistence является частью Infrastructure Layer.

Persistence отвечает за взаимодействие
между предметной областью
и системой хранения данных.

Domain Layer не зависит
от JPA,
Hibernate,
Spring Data JPA
или PostgreSQL.

Взаимодействие
с хранилищем выполняется
через порты,
определенные
внутренними слоями системы.

Infrastructure реализует
эти порты
через Persistence Adapters.

Для PostgreSQL
используются:

- Spring Data JPA;
- Hibernate;
- PostgreSQL JDBC Driver.

Persistence Entity
отделены
от Domain Entity.

Mapping между
Domain Model
и Persistence Model
выполняется
через отдельный Mapper.

Для преобразования моделей
используется MapStruct.

Изменение структуры
базы данных
выполняется
только через Liquibase.

---

# Взаимодействие подсистем

## Outage Processing

> Scheduler и Provider Subsystem
> находятся перед входом `ParsedOutage`
> в Processing Pipeline
> и не смешиваются с самим Pipeline.
> Pipeline начинается с `ParsedOutage`.


Source (активные через SourcePort)
    ↓
Scheduler (только активные, cron String, без nextRunAt)
    ↓
Provider Registry — find(providerType)
    ↓
ProviderContext (sourceId + String configuration)
    ↓
OutageProvider — fetch → List<ParsedOutage> (sourceId)
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

Match

↓

Application / Processing Flow

↓

Notification

↓

Notification Engine

↓

Delivery Adapter

---

## Domain / Application / Infrastructure

Infrastructure

↓

Application Layer

↓

Domain Layer

Application Layer
координирует
прикладные сценарии.

Domain Layer
содержит
предметные правила.

Infrastructure
реализует
внешние зависимости
и технические операции.

---

# Основные принципы

Архитектура проекта
строится
на следующих принципах.

- Domain First
- Application Orchestration
- Pipeline Processing
- Replaceable Infrastructure
- Canonical Address Model
- Single Responsibility Principle
- Dependency Inversion Principle

Все принципы
подробно описаны
в ADR.

---

# Domain First

Domain Model
является центром
архитектуры.

Domain Layer
не зависит
от:

- Spring;
- JPA;
- Hibernate;
- PostgreSQL;
- Liquibase;
- SMTP;
- Telegram;
- других инфраструктурных
  технологий.

---

# Application Orchestration

Application Layer
координирует
прикладные сценарии.

Application Layer
не является
частью Domain Model.

В частности,
правило последовательности:

Match

↓

Notification

реализуется
в Application /
Processing Flow.

Notification
не получает
техническую зависимость
от Match.

---

# Pipeline Processing

Событие отключения
проходит
последовательность
определенных этапов.

Каждый этап
имеет собственную
ответственность.

Изменение одного этапа
не должно требовать
изменения остальных.

Подробнее:

[ADR-003 — Outage Processing Pipeline](adr/ADR-003-Outage-Processing-Pipeline.md)

---

# Replaceable Infrastructure

Инфраструктурные
компоненты могут быть
заменены без изменения
Domain Model.

Допускается замена:

- PostgreSQL;
- Persistence implementation;
- Email;
- Telegram;
- Delivery Adapter;
- Scheduler;
- Provider;
- внешних библиотек.

Подробнее:

[ADR-007 — Replaceable Infrastructure](adr/ADR-007-Replaceable-Infrastructure.md)

---

# Notification Lifecycle

Notification имеет
собственный lifecycle:

PENDING

↓

PROCESSING

↓

SENT

или

PROCESSING

↓

FAILED

`PROCESSING` означает,
что Notification Engine
принял Notification
в обработку.

Retry является
ответственностью
Notification Engine.

Повторная обработка
FAILED Notification
не создает новый
Notification.

Конкретная Retry Policy
и Delivery Attempt
не являются частью
текущей Domain Model.

---

# Архитектурные границы

## Parser

Отвечает
за получение
и нормализацию
внешних данных.

Не выполняет:

- дедупликацию;
- Matching;
- создание Notification;
- доставку.

---

## DuplicateResolver

Отвечает
за дедупликацию
и определение
необходимости создания
или обновления
PowerOutage.

---

## CandidateFinder

Выполняет
предварительный
поиск потенциальных
Subscription.

Не принимает
окончательное решение
о совпадении.

---

## Matching Engine

Принимает
окончательное решение
о соответствии
PowerOutage
и Subscription.

Результат:

Match.

---

## Application / Processing Flow

Координирует
переход:

Match

↓

Notification.

Передает
готовый Notification
в Notification Engine.

---

## Notification

Представляет
доменную необходимость
уведомления.

Не знает
о конкретном
канале доставки.

---

## Notification Engine

Отвечает
за обработку
и доставку Notification.

---

## Delivery Adapter

Выполняет
техническую доставку
через конкретный канал.

---

# Dependency Direction

Зависимости
должны направляться
к внутренним слоям.

Infrastructure

↓

Application

↓

Domain

Domain
не зависит
от Application
или Infrastructure.

Application
может использовать
Domain Model
и Domain Ports.

Infrastructure
реализует
необходимые порты.

---

# Persistence Direction

Domain

↓

Domain Ports

↓

Persistence Adapters

↓

Spring Data JPA

↓

Hibernate

↓

PostgreSQL

Persistence Entity
не является
Domain Entity.

Mapping выполняется
через Mapper.

---

# Notification Direction

Application / Processing Flow

↓

Notification

↓

Notification Engine

↓

Delivery Adapter

↓

External Delivery Provider

Notification Domain
не знает
о:

- Email;
- Telegram;
- SMTP;
- конкретном Adapter;
- Retry implementation.

---

# Расширяемость

Архитектура должна позволять
добавлять:

- новые Provider;
- новые каналы уведомлений;
- новые алгоритмы Matching;
- новые Persistence implementations.

Добавление нового
канала доставки
не должно изменять:

- Domain Model;
- Matching Engine;
- Parser;
- существующие Adapter.

---

# Тестируемость

Каждый слой
должен тестироваться
изолированно.

## Domain

Unit tests
без инфраструктуры.

## Application

Tests
с mock/fake Domain Ports.

## Infrastructure

Integration tests
с реальными
инфраструктурными компонентами.

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
- [00.5-GLOSSARY](00.5-GLOSSARY.md)
- [02-DOMAIN_MODEL](02-DOMAIN_MODEL.md)
- [03-DATABASE](03-DATABASE.md)
- [04-PARSER](04-PARSER.md)
- [05-MATCHING_ENGINE](05-MATCHING_ENGINE.md)
- [06-NOTIFICATION_ENGINE](06-NOTIFICATION_ENGINE.md)

## ADR

- [ADR-001 — Domain First Architecture](adr/ADR-001-Domain-First-Architecture.md)
- [ADR-003 — Outage Processing Pipeline](adr/ADR-003-Outage-Processing-Pipeline.md)
- [ADR-007 — Replaceable Infrastructure](adr/ADR-007-Replaceable-Infrastructure.md)

## Диаграммы

- [System Context](diagrams/overview/01-system-context.puml)
- [Container](diagrams/overview/02-container.puml)
- [Package Responsibility](diagrams/overview/03-package-responsibility.puml)
- [Parser Pipeline](diagrams/detailed/07-parser-pipeline.puml)
- [Matching Pipeline](diagrams/detailed/08-matching-pipeline.puml)
- [Notification Pipeline](diagrams/detailed/09-notification-pipeline.puml)

---

| ⬅ Предыдущий | 🏠 README | ➡ Следующий |
|-------------|-----------|-------------|
| [00.5-GLOSSARY](00.5-GLOSSARY.md) | [README](README.md) | [02-DOMAIN_MODEL](02-DOMAIN_MODEL.md) |