# ADR-004 — OutageProvider Architecture

> Архитектурное решение об архитектуре получения информации
> об отключениях электроэнергии из внешних источников.

---

## Статус

**Accepted**

---

## Дата

2026-08-09

---

## Версия

1.2

---

## Навигация

| Раздел | Ссылка |
|---------|--------|
| ⬅ Предыдущий | [ADR-003](ADR-003-Outage-Processing-Pipeline.md) |
| 🏠 Документация | [README](../README.md) |
| 📚 ADR Index | [README](README.md) |
| ➡ Следующий | [ADR-005](ADR-005-PowerOutage-Event-Model.md) |

---

## Влияние

Данное решение оказывает влияние на:

- OutageProvider;
- ProviderContext;
- Parser;
- Provider Registry;
- Adapter Layer;
- Scheduler;
- Source;
- ParsedOutage;
- Outage Processing Pipeline;
- внешние scraping и document-processing technologies.

---

## Связанные документы

- [ADR-001 — Domain First Architecture](ADR-001-Domain-First-Architecture.md)
- [ADR-003 — Outage Processing Pipeline](ADR-003-Outage-Processing-Pipeline.md)
- [01-ARCHITECTURE](../01-ARCHITECTURE.md)
- [04-PARSER](../04-PARSER.md)
- [ADR-007 — Replaceable Infrastructure](ADR-007-Replaceable-Infrastructure.md)

---

# ADR-004 — OutageProvider Architecture

**Статус:** Accepted

**Дата:** 2026-08-09

**Версия:** 1.2

---

# Назначение

Зафиксировать архитектуру получения
информации об отключениях электроэнергии
из внешних источников.

Решение определяет:

- контракт OutageProvider;
- ProviderContext;
- Provider Registry;
- Adapter Layer;
- границу внешних библиотек;
- единый внутренний формат ParsedOutage;
- ответственность Provider;
- границу Scheduler и Source;
- возможность использования
  различных технологий получения данных;
- возможность замены конкретного Provider
  без изменения Business Domain Model.

---

# Контекст

Информация об отключениях
может поступать
из различных внешних источников.

Источники могут предоставлять данные
в различных форматах:

- HTML;
- REST API;
- JSON;
- XML;
- PDF;
- документы;
- динамические web applications;
- другие форматы.

Источники также могут
требовать различный способ получения:

- обычный HTTP request;
- crawling;
- JavaScript execution;
- browser automation;
- document processing;
- специализированный API client.

Нельзя связывать
Business Domain Model
с конкретным способом
получения данных.

---

# Проблема

Если каждая интеграция
будет напрямую использовать
свою внешнюю библиотеку
внутри бизнесовой модели,

то появится coupling:

Domain

↓

конкретная библиотека

↓

конкретный внешний источник.

Это приведет к:

- зависимости Domain от Infrastructure;
- сложности замены библиотек;
- сложности тестирования;
- распространению внешних моделей
  по внутренним слоям;
- невозможности использовать
  различные технологии
  для различных Provider.

---

# Архитектурные принципы

## 1. Provider abstraction

Все внешние источники
подключаются через
единый OutageProvider contract.

---

## 2. Provider isolation

Каждый Provider
является независимым компонентом.

Provider не знает
о других Provider.

---

## 3. Единая внутренняя модель

Любой Provider
возвращает:

`List<ParsedOutage>`

Внутренняя модель
не зависит
от способа получения
информации.

---

## 4. Внешние решения подключаются через Adapter

Использование
готовых open-source решений
выполняется
только через Adapter Layer.

Предметная область
не зависит
от реализации
стороннего проекта.

---

## 5. Provider не содержит бизнес-логики

Provider отвечает
только за получение
и преобразование
внешних данных
в установленный внутренний контракт.

Provider не выполняет:

- дедупликацию;
- сохранение в БД;
- поиск совпадений;
- Matching;
- создание Notification;
- отправку уведомлений;
- другие Business Domain decisions.

---

# Решение

## 1. Source и Scheduler

Source является
конфигурационной Domain Entity
внешнего источника.

Source содержит:

- `id` — UUID identity;
- `name` — уникальное имя;
- `sourceType` — классификация источника;
- `providerType` — ключ для выбора Provider;
- `configuration` — `String` (nullable), provider-specific параметры;
- `schedule` — строковое cron-выражение;
- `isActive` — признак активности.

`schedule` хранится как `String`
и интерпретируется
только Scheduler / Infrastructure.

Domain `Source`
не получает методов
`shouldRunNow()`,
`nextRunAt()`
и аналогичной логики
планирования.

`nextRunAt`
не добавляется
в Domain и Database.

Scheduler работает
только с активными Source
(`isActive == true`).

Контрактом получения
активных Source является
специализированный запрос
через `SourcePort`
`findAllActive()`.

Реализация
этого запроса
выполнена в TASK 21
(`SourcePort.findAllActive()`,
`SourceJpaRepository.findByIsActiveTrue`,
`SourcePersistenceAdapter`).

Для одного Source
одновременно
не допускается
более одного выполняющегося запуска.

Ошибка обработки
одного Source
не должна останавливать
обработку остальных Source.

---

## 2. ProviderContext

ProviderContext является
Application-level
временным контрактом
для вызова конкретного Provider.

ProviderContext содержит:

- `sourceId` — UUID идентификатор Source;
- `configuration` — `String` (nullable), строка конфигурации конкретного Source.

Тип `configuration`
в ProviderContext —
`String`,
а не `JsonNode`.

ProviderContext
не содержит Domain `Source`.

Provider получает
только минимально необходимый
контекст конкретного Source.

Provider не получает
прямой доступ
к полному `Source`
или к его `schedule` / `isActive`.

---

## 3. OutageProvider

OutageProvider является
абстракцией получения
информации об отключениях
из конкретного внешнего источника.

Контракт (реализован в TASK 21):

```text
OutageProvider
    String providerType()
    List<ParsedOutage> fetch(ProviderContext context)
```

Каждый Provider:

- сам объявляет свой `providerType`
  через `providerType()`;
- реализует `fetch(ProviderContext)`;
- работает независимо;
- не зависит от других Provider;
- не взаимодействует
  с Domain Model напрямую;
- не взаимодействует
  с Persistence напрямую;
- возвращает `List<ParsedOutage>`
  (пустой список — отсутствие данных,
  а не ошибка);
- не занимается persistence, deduplication,
  Matching, Notification
  и другими Business Domain decisions.

Контракт
реализован в TASK 21
(`application.provider.OutageProvider`).

---

## 4. Provider Registry

Provider Registry
является единой точкой
предоставления
зарегистрированных Provider.

Минимальный контракт (реализован в TASK 21):

```text
ProviderRegistry.find(String providerType)
    → Optional<OutageProvider>
```

Registry отвечает за:

- централизованное предоставление
  зарегистрированных Provider;
- использование `providerType` как ключа;
- построение неизменяемого индекса
  `providerType → Provider`
  при старте приложения.

Registry не отвечает за:

- parsing;
- нормализацию;
- запуск Provider;
- внешний health check;
- persistence;
- deduplication;
- Matching;
- Notification.

Не добавлять
обязательный публичный
`list()` и `register()`
в контракт Registry
как часть обязательного API.
Внутренняя регистрация
выполняется
через механизм
dependency injection /
коллекцию бинов `List<OutageProvider>`
при построении индекса.

Контракт
реализован в TASK 21
(`application.provider.ProviderRegistry`).

---

## 5. Регистрация и duplicate providerType

При построении индекса
`providerType → Provider`
Registry проверяет
уникальность `providerType`.

Duplicate `providerType`
(два Provider с одинаковым `providerType`)
является ошибкой
конфигурации приложения
и приводит к отказу старта
(например, `IllegalStateException`).

---

## 6. Adapter Layer

Adapter Layer
изолирует внутреннюю модель
от внешних библиотек,
сервисов и форматов данных.

Adapter отвечает за:

- преобразование внешних моделей;
- нормализацию данных
  в пределах внешнего формата;
- создание ParsedOutage;
- преобразование результата
  конкретной внешней технологии
  во внутренний контракт.

Adapter не отвечает за:

- persistence;
- deduplication;
- Matching;
- Notification;
- бизнесовые решения.

---

# Scraping and Document Processing Technologies

Архитектура не фиксирует
единственный технологический стек
для всех Provider.

Конкретный Provider
может использовать
различную Infrastructure technology
в зависимости от особенностей
внешнего источника.

При выборе технологии
предпочтение отдается
наименьшей необходимой
технической сложности.

---

## Scrapy

[Scrapy](https://github.com/scrapy/scrapy)

Scrapy является
предпочтительным default-инструментом
для Provider, которым требуется:

- HTTP crawling;
- HTML extraction;
- structured data extraction;
- обработка большого количества
  web resources;
- обычное web crawling
  без обязательного browser automation.

Scrapy не является
частью Domain Model,
Application Model
или ParsedOutage contract.

Scrapy используется
только через соответствующий
Infrastructure / Adapter boundary.

---

## Botasaurus

[Botasaurus](https://github.com/omkarcloud/botasaurus)

Botasaurus является
специализированным fallback-инструментом
для Provider, которым требуется:

- browser automation;
- JavaScript execution;
- dynamic web applications;
- browser-oriented extraction;
- anti-bot capabilities.

Botasaurus не является
глобальной зависимостью
BlackoutRadar.

Он может использоваться
только тем Provider,
которому такие возможности
действительно необходимы.

---

## AnyDoc

[AnyDoc](https://github.com/firecrawl/anydoc)

AnyDoc является
вспомогательным
document-processing инструментом.

Он может использоваться
Provider, которые получают
данные в document-based формате,
например:

- PDF;
- DOC/DOCX;
- XLS/XLSX;
- PPT/PPTX;
- другие поддерживаемые
  документные форматы.

AnyDoc не рассматривается
как основной web crawling framework.

Использование AnyDoc
ограничивается соответствующим
Infrastructure / Adapter boundary.

---

# Provider-local Technology Choice

Конкретный Provider
может использовать:

- Scrapy;
- Botasaurus;
- AnyDoc;
- прямой HTTP client;
- API client;
- другую подходящую
  Infrastructure technology.

Выбор технологии
является локальным решением
конкретного Provider.

Различные Provider
могут использовать
различные технологии.

Например:

Provider A

↓

Scrapy

Provider B

↓

Botasaurus

Provider C

↓

REST API client

Provider D

↓

Scrapy

↓

Document processing

↓

AnyDoc

Все варианты
должны завершаться
одним внутренним контрактом:

Provider

↓

Adapter

↓

ParsedOutage (`List<ParsedOutage>`).

---

# Browser Automation

Browser automation
не является default-способом
получения данных.

Если внешний источник
предоставляет доступные
HTTP/API endpoints,
предпочтительно использовать
прямое получение данных
без запуска browser runtime.

Browser automation
применяется только тогда,
когда без него
невозможно или существенно
сложнее получить необходимые данные.

---

# Java Runtime Boundary

Scraping и document-processing
технологии не являются
частью Domain Model.

Они также не должны
становиться обязательными
runtime dependencies
Business Domain или Application Core.

Конкретный способ интеграции
внешних Python/Rust/других
инструментов с Java runtime
данным ADR не фиксируется.

Способ интеграции должен
определяться отдельно
при проектировании
конкретного Provider.

---

# Domain Boundary

Business Domain Model
не знает:

- Scrapy;
- Botasaurus;
- AnyDoc;
- browser automation;
- HTTP client;
- HTML parser;
- PDF parser;
- конкретные модели
  внешних библиотек.

Все внешние зависимости
изолируются
Infrastructure / Adapter Layer.

---

# ParsedOutage Boundary

Любая внешняя технология
должна завершать
работу на границе
Provider / Adapter.

Внутренняя последовательность:

External Source

↓

Provider Technology

↓

Provider Adapter

↓

ParsedOutage (`sourceId` + данные)

↓

Outage Processing Pipeline.

ParsedOutage:

- является Application-level
  временным контрактом Parser;
- не является сущностью базы данных;
- не содержит
  моделей внешних библиотек;
- содержит `sourceId` (UUID),
  а не Domain `Source`.

Модель (реализована в TASK 21):

```text
ParsedOutage(
    UUID sourceId,
    Instant startTime,
    Instant endTime,
    String reason,
    String externalReference,
    List<AddressInput> addresses
)
```

Переход `ParsedOutage.source`
→ `ParsedOutage.sourceId`
реализован в TASK 21.

Не описывать `Source`
как часть `ParsedOutage`.

---

# Address Boundary

OutageProvider
и его внешние технологии
не являются
частью Canonical Address Model.

Полученные адресные данные
передаются во внутренний
Parser / Processing contract.

Canonical Address resolution
выполняется отдельно
через AddressService.

Scrapy, Botasaurus,
AnyDoc или другая
внешняя технология
не может использоваться
как источник
canonical Address identity.

---

# Scheduler Boundary

Scheduler является
Infrastructure
компонентом,
отвечающим
за периодический запуск
активных Source.

Последовательность (целевая, TASK 21):

```text
Source (активные через SourcePort)
    ↓
Scheduler — определяет момент запуска активного Source
    ↓
Provider Registry — находит Provider по providerType
    ↓
ProviderContext (sourceId + String configuration)
    ↓
OutageProvider.fetch(context) — получает внешние данные
    ↓
List<ParsedOutage> (с sourceId) — возвращает внутренний результат
    ↓
Outage Processing Pipeline (DuplicateResolver → PowerOutage …)
```

Scheduler:

- определяет момент запуска
  на основе `Source.schedule` (cron, `String`);
- работает только с активными Source;
- получает активных Source
  через специализированный запрос `SourcePort`;
- для одного Source
  не допускает параллельных запусков;
- передаёт управление
  Provider Registry → Provider.

Scheduler не смешивается
с дальнейшим Outage Processing Pipeline.

Outage Processing Pipeline
начинается
после получения `ParsedOutage`.

## Жизненный цикл расписаний

Расписания
формируются однократно
при старте приложения
из текущего списка
активных Source
через `SourcePort.findAllActive()`.

Последующие изменения
`Source` в БД
не обновляют
уже созданные расписания
автоматически;
новая конфигурация
применяется только
после повторного планирования /
перезапуска приложения.

Динамическое rescheduling
в TASK 21 не реализовано
и требует отдельного
архитектурного решения.

---

# Provider Failure Isolation

Ошибка обработки
одного Source / Provider
не должна останавливать
обработку остальных Source.

В том числе:

- исключение в `OutageProvider.fetch`
  для одного Source;
- отсутствующий Provider
  по `providerType` (ошибка конфигурации
  конкретного Source).

Отсутствующий Provider
должен быть ошибкой
конкретного Source,
а не остановкой
всей итерации Scheduler.

Provider должен
обрабатываться независимо.

Для одного Source
одновременно
не допускается
более одного выполняющегося запуска
(защита от перекрытия cron-интервалов).

Временные ошибки
могут быть повторно
обработаны Scheduler
при следующем запуске
по cron-расписанию.

---

# Расширяемость

Добавление нового Provider
не должно требовать
изменения:

- Domain Model;
- Matching Engine;
- Notification Engine;
- существующих Provider;
- общего ParsedOutage contract (кроме `sourceId` перехода).

Для нового Provider
должны быть реализованы:

1. OutageProvider с `providerType()` / `fetch(ProviderContext)`;
2. соответствующий Adapter
   при необходимости;
3. регистрация Provider
   в Provider Registry
   (автоматически через Spring-бобы,
   duplicate `providerType` — ошибка).

---

# Последствия

## Положительные

Архитектура позволяет:

- использовать разные технологии
  для разных источников;
- выбрать оптимальный инструмент
  для конкретного Provider;
- заменить scraper/parser
  без изменения Domain Model;
- передавать Provider
  только минимальный `ProviderContext`
  (`sourceId` + `String configuration`);
- централизованно находить Provider
  по `providerType` через `find`;
- изолировать ошибки одного Source
  от остальных;
- тестировать внутреннюю модель
  независимо от внешнего scraping stack;
- сохранить `Source.schedule`
  как простой `String` cron
  без доменной логики планирования.

---

## Отрицательные

Решение приводит к:

- увеличению количества
  возможных Infrastructure components;
- необходимости поддерживать
  Adapter boundaries;
- необходимости тестировать
  интеграцию каждого Provider
  с выбранной технологией;
- потенциальному усложнению deployment,
  если конкретная технология
  требует отдельного runtime;
- необходимости реализовать
  в TASK 21 новые контракты
  `ProviderContext`, `OutageProvider.fetch`,
  `ProviderRegistry.find` и переход
  `ParsedOutage.source` → `sourceId`.

Данные последствия
признаны допустимыми.

---

# Влияние на реализацию

Настоящее решение оказывает влияние
на:

- OutageProvider;
- ProviderContext;
- Provider Registry;
- Adapter Layer;
- Scheduler;
- Source / SourcePort;
- ParsedOutage;
- Parser;
- Outage Processing Pipeline.

Конкретные scraping/document-processing
dependencies добавляются
только в Infrastructure,
связанную с соответствующим Provider.

Они не добавляются
в Domain Model.

---

# Не входит в данное решение

Настоящий ADR
не определяет:

- конкретный внешний Provider;
- конкретный scraper implementation;
- deployment topology;
- Python/Rust runtime integration;
- container architecture
  для scraper;
- AddressService implementation;
- Address normalization algorithms;
- Matching algorithms.

Эти вопросы определяются
соответствующими TASK
и архитектурными решениями.

---

# Совместимость с Replaceable Infrastructure

Решение полностью соответствует
ADR-007.

Scraping technology
является заменяемой
Infrastructure.

Допускается замена:

Scrapy

↓

другая technology

без изменения
Domain Model.

Аналогично:

Botasaurus

↓

другая technology

или:

AnyDoc

↓

другой document processor.

Замена внешнего инструмента
не должна менять
внутренний Business Domain contract.

---

# Связанные документы

- [ADR-001 — Domain First Architecture](ADR-001-Domain-First-Architecture.md)
- [ADR-003 — Outage Processing Pipeline](ADR-003-Outage-Processing-Pipeline.md)
- [ADR-005 — PowerOutage Event Model](ADR-005-PowerOutage-Event-Model.md)
- [ADR-007 — Replaceable Infrastructure](ADR-007-Replaceable-Infrastructure.md)
- [01-ARCHITECTURE](../01-ARCHITECTURE.md)
- [04-PARSER](../04-PARSER.md)

---

# Статус

**Accepted**

Версия 1.2 уточняет
целевой контракт TASK 21:

- Source.schedule как `String` cron,
  Scheduler только для активных Source
  через специализированный `SourcePort`,
  без `nextRunAt` / `shouldRunNow` в Domain,
  с защитой от параллельных запусков
  одного Source и изоляцией ошибок Source;
- Application-level `ProviderContext`
  (`sourceId` + `String configuration`,
  без `Source` и без `JsonNode`);
- OutageProvider с `providerType()` /
  `fetch(ProviderContext)` → `List<ParsedOutage>`;
- ParsedOutage с `sourceId` (UUID) вместо `Source`
  как целевой контракт (переход в TASK 21);
- ProviderRegistry с минимальным
  `find(providerType) → Optional<OutageProvider>`,
  неизменяемым индексом
  и duplicate `providerType` как ошибкой конфигурации;
- уточнённый Scheduler → Registry → Provider flow
  перед Outage Processing Pipeline.

Изменение общей
OutageProvider architecture
допускается только путем
создания нового ADR.
