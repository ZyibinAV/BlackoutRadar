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

1.1

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
- Parser;
- Provider Registry;
- Adapter Layer;
- Scheduler;
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

**Версия:** 1.1

---

# Назначение

Зафиксировать архитектуру получения
информации об отключениях электроэнергии
из внешних источников.

Решение определяет:

- контракт OutageProvider;
- Provider Registry;
- Adapter Layer;
- границу внешних библиотек;
- единый внутренний формат ParsedOutage;
- ответственность Provider;
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

`ParsedOutage`

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

## 1. OutageProvider

OutageProvider является
абстракцией получения
информации об отключениях
из конкретного внешнего источника.

Каждый Provider:

- реализует единый контракт;
- работает независимо;
- не зависит от других Provider;
- не взаимодействует
  с Domain Model напрямую;
- не взаимодействует
  с Persistence напрямую;
- возвращает ParsedOutage.

---

## 2. Provider Registry

Provider Registry
является единой точкой
регистрации и предоставления
доступных Provider.

Registry отвечает за:

- регистрацию Provider;
- предоставление списка Provider;
- поиск Provider
  по типу источника;
- проверку доступности Provider.

Registry не отвечает за:

- parsing;
- нормализацию;
- persistence;
- deduplication;
- Matching;
- Notification.

---

## 3. Adapter Layer

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

ParsedOutage.

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

ParsedOutage

↓

Outage Processing Pipeline.

ParsedOutage
не содержит
моделей внешних библиотек.

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

# Provider Failure Isolation

Ошибка обработки
одного Provider
не должна останавливать
обработку остальных Provider.

Provider должен
обрабатываться независимо.

Временные ошибки
могут быть повторно
обработаны Scheduler.

---

# Расширяемость

Добавление нового Provider
не должно требовать
изменения:

- Domain Model;
- Matching Engine;
- Notification Engine;
- существующих Provider;
- общего ParsedOutage contract.

Для нового Provider
должны быть реализованы:

1. OutageProvider;
2. соответствующий Adapter
   при необходимости;
3. регистрация Provider
   в Provider Registry.

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
- постепенно переходить
  от одного внешнего решения
  к другому;
- использовать browser automation
  только там, где она необходима;
- использовать document processors
  только для соответствующих
  document-based источников;
- тестировать внутреннюю модель
  независимо от внешнего scraping stack.

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
  требует отдельного runtime.

Данные последствия
признаны допустимыми.

---

# Влияние на реализацию

Настоящее решение оказывает влияние
на:

- OutageProvider;
- Provider Registry;
- Adapter Layer;
- Scheduler;
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
- ParsedOutage detailed contract;
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

Версия 1.1 уточняет
существующее архитектурное решение
и фиксирует предпочтительные
технологии для Infrastructure.

Изменение общей
OutageProvider architecture
допускается только путем
создания нового ADR.