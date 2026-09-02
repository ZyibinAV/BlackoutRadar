# Parser

> Подсистема получения и нормализации информации об отключениях электроэнергии.

---

# Назначение

Документ описывает архитектуру
подсистемы парсинга,
жизненный цикл получения данных
из внешних источников
и процесс преобразования их
в единый внутренний формат.

Parser отвечает на вопрос:

> **«Как данные попадают в систему?»**

---

# Навигация

| Раздел | Ссылка |
|---------|--------|
| ⬅ Предыдущий | [03-DATABASE](03-DATABASE.md) |
| 🏠 Документация | [README](README.md) |
| ➡ Следующий | [05-MATCHING_ENGINE](05-MATCHING_ENGINE.md) |

---

# Связанные ADR

- [ADR-003 — Outage Processing Pipeline](adr/ADR-003-Outage-Processing-Pipeline.md)
- [ADR-004 — OutageProvider Architecture](adr/ADR-004-OutageProvider-Architecture.md)
- [ADR-007 — Replaceable Infrastructure](adr/ADR-007-Replaceable-Infrastructure.md)

---

# Связанные диаграммы

- [Parser Pipeline](diagrams/detailed/07-parser-pipeline.puml)

---

# Parser Subsystem

---

# Назначение

Подсистема Parser
отвечает
за получение информации
о плановых отключениях
из внешних источников.

Подсистема не содержит
Business Domain Logic.

Основная задача:

получить информацию
из внешнего источника
и преобразовать ее
во внутреннее представление,
понятное последующим
этапам системы.

Подробнее:

ADR-003 — Outage Processing Pipeline

ADR-004 — OutageProvider Architecture

---

# Основные принципы

Подсистема Parser
не зависит
от конкретного способа
получения данных.

Она не:

- создает PowerOutage;
- выполняет дедупликацию;
- ищет совпадения;
- создает Notification;
- отправляет уведомления;
- принимает Matching decisions.

Результатом работы Parser
является коллекция ParsedOutage
(`List<ParsedOutage>`).

---

# Общая архитектура

Подсистема состоит
из следующих компонентов:

- Source;
- Scheduler;
- Provider Registry;
- ProviderContext;
- OutageProvider;
- Adapter Layer;
- ParsedOutage.

Каждый компонент
имеет единственную ответственность.

Scheduler и Provider Registry
находятся
перед OutageProvider
и не смешиваются
с дальнейшим
Outage Processing Pipeline.

---

# Общая схема

```text
Source (активные)
    ↓
Scheduler — определяет момент запуска активного Source
    ↓
Provider Registry — находит Provider по providerType
    ↓
ProviderContext (sourceId + String configuration)
    ↓
OutageProvider — получает внешние данные
    ↓
Provider Technology
    ↓
Adapter
    ↓
ParsedOutage (sourceId)
    ↓
Outage Processing Pipeline
```

Не смешивать Scheduler
с дальнейшим
Outage Processing Pipeline.

Outage Processing Pipeline
начинается
после получения `ParsedOutage`.

---

# Source

Source является
Domain Entity,
хранящей конфигурацию
внешнего источника.

Source содержит:

- `id` — UUID;
- `name` — уникальное имя;
- `sourceType`;
- `providerType` — ключ для Provider Registry;
- `configuration` — `String` (nullable);
- `schedule` — строковое cron-выражение;
- `isActive` — признак активности.

`schedule` хранится
как `String`
и интерпретируется
только Scheduler / Infrastructure.

`Source`
не получает методов
`shouldRunNow()`,
`nextRunAt()`
и аналогичной логики
планирования.

`nextRunAt`
не добавляется
в Domain и Database.

Scheduler работает
только с активными Source.

Контрактом
получения активных Source
является специализированный запрос
через `SourcePort`
`findAllActive()`.

Реализация
этого запроса
выполнена в TASK 21
(`SourcePort.findAllActive()`
и `SourcePersistenceAdapter`).

Для одного Source
одновременно
не допускается
более одного выполняющегося запуска.

---

# Scheduler

Scheduler
отвечает
за запуск
процесса получения данных
из внешних источников.

Scheduler является
точкой входа
перед OutageProvider
и не является частью
Outage Processing Pipeline.

---

## Назначение

Организовать
периодический запуск
всех активных Source
на основе их `schedule` (cron, `String`).

---

## Ответственность

Scheduler отвечает за:

- получение активных Source
  через специализированный запрос `SourcePort`
  и планирование запуска
  по cron-выражению `schedule`;
- запрет параллельных запусков
  одного и того же Source;
- передачу управления
  Provider Registry
  для получения Provider;
- формирование `ProviderContext`
  и вызов `OutageProvider.fetch`;
- передачу полученных
  `List<ParsedOutage>`
  в Outage Processing Pipeline.

---

## Не отвечает

Scheduler
не отвечает за:

- парсинг данных;
- дедупликацию;
- поиск совпадений;
- Matching;
- создание PowerOutage;
- создание Notification;
- отправку уведомлений;
- интерпретацию `configuration`.

Cron интерпретируется
только Scheduler / Infrastructure.

---

## Основные принципы

Scheduler:

- работает только
  с активными Source;
- не зависит
  от конкретных Provider
  (использует `providerType` как ключ);
- не содержит
  бизнес-логики дедупликации;
- изолирует ошибки Source
  друг от друга.

## Жизненный цикл расписаний

Расписания
формируются однократно
при старте приложения
из текущего списка
активных Source
через `SourcePort.findAllActive()`.

Последующие изменения
`Source` в БД
(создание, изменение `schedule` / `isActive` / `configuration` / `providerType`,
удаление)
не обновляют
уже созданные расписания
автоматически.

Новая конфигурация
применяется только
после повторного планирования /
перезапуска приложения.

Динамическое rescheduling
в рамках TASK 21
не реализовано
и требует отдельного
архитектурного решения.

---

## Общий алгоритм (реализован в TASK 21)

Запуск Scheduler по расписанию инфраструктуры

↓

Получение активных Source
через `SourcePort.findAllActive()`

↓

Для каждого активного Source
(без параллельных запусков одного Source):

  Поиск Provider
  через `ProviderRegistry.find(providerType)`

  ↓

  Формирование `ProviderContext`
  (`sourceId` + `String configuration`)

  ↓

  `OutageProvider.fetch(context)`
  → `List<ParsedOutage>`

  ↓

  Передача каждого `ParsedOutage`
  в `OutageProcessingService` /
  Outage Processing Pipeline

Ошибка одного Source
(отсутствующий Provider,
исключение `fetch`,
временная недоступность)
не останавливает
обработку остальных Source.

---

# OutageProvider

OutageProvider
является
единой точкой входа
для получения информации
из внешних источников.

---

## Контракт (реализован в TASK 21)

```text
OutageProvider
    String providerType()
    List<ParsedOutage> fetch(ProviderContext context)
```

Provider сам объявляет
свой `providerType`.

`fetch` получает
только `ProviderContext`
(`sourceId` + `String configuration`)
и возвращает
`List<ParsedOutage>`.

Контракт
реализован в TASK 21
(`application.provider.OutageProvider`).

---

## Ответственность

OutageProvider отвечает за:

- получение данных
  для конкретного Source;
- чтение внешних документов;
- взаимодействие
  с внешними сервисами;
- использование
  необходимой Infrastructure technology;
- передачу полученных данных
  соответствующему Adapter;
- преобразование результата
  в `List<ParsedOutage>` через Adapter boundary.

---

## Не отвечает

OutageProvider
не отвечает за:

- сохранение данных;
- дедупликацию;
- поиск совпадений;
- Matching;
- создание Notification;
- отправку уведомлений;
- persistence;
- Business Domain decisions.

---

## Основные принципы

Каждый Provider:

- работает независимо;
- сам объявляет `providerType`;
- не знает
  о других Provider;
- получает только `ProviderContext`;
- не взаимодействует
  с базой данных напрямую;
- не передает модели
  внешних библиотек
  во внутренние слои;
- возвращает `List<ParsedOutage>`.

---

## Жизненный цикл

Инициализация Provider

↓

Вызов `fetch(ProviderContext)`

↓

Получение внешних данных
через Provider Technology

↓

Преобразование через Adapter
в `List<ParsedOutage>`

↓

Возврат результата
вызывающему Scheduler

↓

Завершение обработки Source

---

# ProviderContext

ProviderContext является
Application-level
временным контрактом
для вызова Provider.

ProviderContext содержит:

- `sourceId` — UUID идентификатор Source;
- `configuration` — `String` (nullable),
  строковая конфигурация конкретного Source.

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
к `schedule`, `isActive`
или другим полям `Source`.

Реализация
`ProviderContext`
выполнена в TASK 21
(`application.provider.ProviderContext`
как `record` с `UUID sourceId` и `String configuration`).

---

# Provider Technology

Конкретный Provider
может использовать
различные технологии
получения и обработки
внешних данных.

Выбор технологии
является локальным решением
конкретного Provider.

Разные Provider
могут использовать
различные технологии.

---

## Предпочтительный default — Scrapy

[Scrapy](https://github.com/scrapy/scrapy)

Scrapy является
предпочтительным default-инструментом
для HTTP/HTML crawling
и extraction.

Он может применяться,
когда источник доступен
через:

- HTTP;
- HTML;
- обычные web requests;
- structured web responses.

Scrapy используется
только внутри
Infrastructure / Adapter boundary.

Domain Model
не зависит от Scrapy.

---

## Специализированный fallback — Botasaurus

[Botasaurus](https://github.com/omkarcloud/botasaurus)

Botasaurus может применяться
для Provider, которым требуется:

- browser automation;
- JavaScript execution;
- dynamic web application;
- browser-oriented extraction;
- anti-bot capabilities.

Botasaurus не является
глобальным Parser dependency.

Он используется
только соответствующим
Provider.

---

## Document Processing — AnyDoc

[AnyDoc](https://github.com/firecrawl/anydoc)

AnyDoc может использоваться
для Provider, которые получают
данные в document-based формате.

Например:

- PDF;
- DOC/DOCX;
- XLS/XLSX;
- PPT/PPTX;
- другие поддерживаемые
  документы.

AnyDoc не является
web crawling framework
и не используется
как основной scraper.

---

# Выбор технологии

При выборе Provider Technology
применяется следующий
приоритет:

1. Использовать простой
   HTTP/API client,
   если внешний источник
   предоставляет пригодный API.

2. Использовать Scrapy
   для стандартного
   HTTP/HTML crawling.

3. Использовать browser-oriented
   technology, включая Botasaurus,
   если источник требует
   JavaScript/browser automation
   или соответствующих
   anti-bot capabilities.

4. Использовать document processor,
   включая AnyDoc,
   если исходные данные
   представлены документами.

Конкретная комбинация технологий
может использоваться
в рамках одного Provider,
если это необходимо.

---

# Browser Automation

Browser automation
не является default-подходом.

Если необходимые данные
могут быть получены
через HTTP/API,
предпочтительно не использовать
browser runtime.

Browser automation
применяется только при наличии
реальной технической необходимости.

---

# Provider Registry

Provider Registry
является централизованной точкой
предоставления
зарегистрированных Provider.

Registry используется
Scheduler для получения
Provider по `providerType`.

---

## Назначение

Централизованно
предоставлять
зарегистрированные Provider
по ключу `providerType`.

---

## Контракт (реализован в TASK 21)

```text
ProviderRegistry.find(String providerType)
    → Optional<OutageProvider>
```

`find`
возвращает `Optional`
и использует `providerType` как ключ.

Контракт
реализован в TASK 21
(`application.provider.ProviderRegistry`).

---

## Ответственность

Provider Registry отвечает за:

- централизованное предоставление
  зарегистрированных Provider;
- использование `providerType` как ключа;
- построение неизменяемого индекса
  `providerType → Provider`
  при старте приложения.

---

## Не отвечает

Provider Registry
не отвечает за:

- выполнение парсинга;
- запуск Provider;
- внешний health check;
- сохранение данных;
- дедупликацию;
- обработку ошибок Provider;
- persistence, Matching или Notification;
- Business Domain Logic.

Не добавлять
обязательный публичный
`list()` и `register()`
в контракт Registry
как часть обязательного API.

Внутренняя регистрация
выполняется
через DI-коллекцию
`List<OutageProvider>`
при построении индекса.

---

## Основные принципы

- Каждый Provider
  регистрируется один раз
  через Spring-бобы;
- имеет уникальный `providerType`,
  объявляемый самим Provider;
- может быть заменен
  без изменения Registry;
- duplicate `providerType`
  является ошибкой конфигурации
  (отказ старта приложения).

---

## Отсутствующий Provider

Отсутствующий Provider
(не найден по `providerType` Source)
является ошибкой
конкретного Source.

Такая ошибка
не останавливает
обработку остальных Source.

Scheduler логирует
ошибку конкретного Source
и продолжает
обработку остальных.

---

## Жизненный цикл (целевой)

Сбор `List<OutageProvider>` из контекста

↓

Построение неизменяемого индекса
`providerType → Provider`
с проверкой duplicate

↓

Использование через `find(providerType)`
по запросу Scheduler

↓

Отказ старта
при duplicate `providerType`

---

# Adapter Layer

Adapter Layer
обеспечивает интеграцию
между внешними решениями
и внутренней моделью системы.

Подробнее:

[ADR-004 — OutageProvider Architecture](adr/ADR-004-OutageProvider-Architecture.md)

---

## Назначение

Изолировать
внутреннюю модель
от моделей данных:

- внешних библиотек;
- внешних сервисов;
- web scraping tools;
- browser automation tools;
- document processors;
- внешних форматов.

---

## Ответственность

Adapter отвечает за:

- преобразование
  внешних моделей;
- преобразование результатов
  Provider Technology;
- нормализацию данных
  в рамках внешнего формата;
- создание ParsedOutage с `sourceId`.

---

## Не отвечает

Adapter
не отвечает за:

- persistence;
- дедупликацию;
- поиск совпадений;
- Matching;
- создание Notification;
- Delivery.

---

## Основные принципы

Каждый Adapter:

- работает только
  с одним внешним форматом
  или согласованным набором
  форматов одного Provider;
- не зависит
  от других Adapter;
- не передает внешние модели
  во внутренние слои;
- возвращает ParsedOutage (`sourceId`).

---

## Использование внешних решений

При подключении
готового open-source проекта
или внешнего сервиса

внутренняя модель
не взаимодействует
с его моделями напрямую.

Все преобразования
выполняются
через Adapter Layer.

---

## Причины проектирования

Использование Adapter Layer
позволяет:

- заменить внешний проект
  без изменения Domain Model;
- использовать
  несколько различных библиотек;
- использовать
  различные технологии
  для различных Provider;
- минимизировать
  связанность компонентов.

---

# ParsedOutage

ParsedOutage
является Application-level
временным контрактом
между Parser Subsystem
и Outage Processing Pipeline.

ParsedOutage
не является
сущностью базы данных
и не является Domain Entity.

---

## Модель (реализована в TASK 21)

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

ParsedOutage содержит
`sourceId` (UUID),
а не Domain `Source`.

Переход
`ParsedOutage.source` → `ParsedOutage.sourceId`
реализован в TASK 21.

Не описывать `Source`
как часть `ParsedOutage`.

`configuration` (`String`)
не является частью
`ParsedOutage`;
она передаётся
только через `ProviderContext`.

---

## Назначение

Представляет результат
обработки одного события
отключения,
полученного
из внешнего источника,
с привязкой к `sourceId`.

---

## Ответственность

ParsedOutage отвечает за:

- передачу данных
  между Parser и Pipeline;
- временное хранение
  результатов парсинга
  с `sourceId`;
- представление данных
  в формате,
  независимом
  от внешнего Provider.

---

## Не отвечает

ParsedOutage
не отвечает за:

- хранение истории;
- Business Domain Logic;
- дедупликацию;
- Matching;
- сохранение в базе данных;
- выбор технологии
  получения внешних данных;
- хранение `configuration`.

---

# Canonical Address Boundary

Parser получает
и структурирует
адресные данные,
но не определяет
canonical Address identity.

Canonical Address resolution
является отдельной
ответственностью AddressService.

Граница:

```text
ParsedOutage (sourceId)
    ↓
OutageProcessingService
    ↓
ParsedOutageProcessor → AddressService
    ↓
canonical Address
    ↓
DuplicateResolver
```

Parser Adapter
не должен:

- выполнять canonical Address lookup;
- взаимодействовать
  с Address Persistence;
- создавать canonical Address;
- принимать решения
  о существовании
  canonical Address.

Подробная реализация
AddressService
определяется TASK 13
и ADR-010.

Существующий `OutageProcessingService`
(реализован в TASK 20)
координирует только
`ParsedOutageProcessor` → `DuplicateResolver`
и не изменяется в TASK 21.

---

# Ошибки обработки

Ошибка обработки
одного Source
не должна останавливать
обработку остальных.

В том числе:

- отсутствующий Provider;
- исключение `fetch`;
- временная недоступность источника.

---

# Основные принципы

## 1. Изоляция ошибок

Каждый Source
обрабатывается
независимо.

Отсутствующий Provider
является ошибкой
конкретного Source,
а не всего Scheduler цикла.

---

## 2. Частичный успех

Если обработка
части источников
завершилась успешно,

результат
должен быть передан
на соответствующий
этап Pipeline
(`OutageProcessingService`).

Остальные Source
продолжают обработку.

---

## 3. Повторная обработка

Временные ошибки
могут быть
повторно обработаны
при следующем запуске
Scheduler по cron.

---

## 4. Логирование

Все ошибки
конкретного Source
подлежат логированию.

Логирование
не заменяет
обработку ошибок
и не останавливает
обработку остальных Source.

---

## 5. Отказоустойчивость

Недоступность
одного Provider
не должна влиять
на работу остальных Provider.

---

## 6. Запрет параллельных запусков Source

Для одного Source
одновременно
не допускается
более одного выполняющегося запуска.

---

# Расширение подсистемы

Архитектура Parser
предусматривает
простое подключение
новых источников данных.

---

## Добавление нового Source

Для подключения
нового источника
необходимо:

1. Реализовать `OutageProvider`
   с `providerType()` / `fetch(ProviderContext)`.

2. При необходимости
   создать Adapter
   для создания `ParsedOutage` с `sourceId`.

3. Выбрать необходимую
   Provider Technology.

4. Зарегистрировать Provider
   как Spring-боб
   (Registry построит индекс автоматически,
   duplicate `providerType` — ошибка).

5. Создать `Source`
   с соответствующими
   `providerType`, `String configuration`,
   `String schedule` (cron), `isActive`.

После этого
новый источник
становится доступным
Scheduler
без изменения
Domain / Pipeline.

---

## Принципы расширяемости

Добавление нового Provider:

- не требует
  изменения Domain Layer;
- не требует
  изменения Matching Engine;
- не требует
  изменения Notification Engine;
- не требует
  изменения существующих Provider;
- не требует
  использования той же
  scraping technology,
  что и другие Provider;
- не требует
  изменения `OutageProcessingService`
  из TASK 20.

---

# Runtime Boundary

Scraping и document-processing
technologies являются
Infrastructure concerns.

Они не должны
становиться зависимостями:

- Domain Model;
- Domain Ports;
- Application business logic
  (кроме `ProviderContext` / `OutageProvider` как Application контрактов);
- ParsedOutage contract
  (кроме `sourceId`).

Конкретный способ запуска
внешнего Python/Rust/другого
инструмента не фиксируется
данным документом.

Он определяется
при реализации конкретного
Provider.

---

# Связанные документы

- [ADR-003 — Outage Processing Pipeline](adr/ADR-003-Outage-Processing-Pipeline.md)
- [ADR-004 — OutageProvider Architecture](adr/ADR-004-OutageProvider-Architecture.md)
- [ADR-007 — Replaceable Infrastructure](adr/ADR-007-Replaceable-Infrastructure.md)

- [01-ARCHITECTURE](01-ARCHITECTURE.md)
- [02-DOMAIN_MODEL](02-DOMAIN_MODEL.md)
- [03-DATABASE](03-DATABASE.md)
- [05-MATCHING_ENGINE](05-MATCHING_ENGINE.md)

---

# Диаграммы

- [Parser Pipeline](diagrams/detailed/07-parser-pipeline.puml)

---

| ⬅ Предыдущий | 🏠 README | ➡ Следующий |
|-------------|-----------|-------------|
| [03-DATABASE](03-DATABASE.md) | [README](README.md) | [05-MATCHING_ENGINE](05-MATCHING_ENGINE.md) |
