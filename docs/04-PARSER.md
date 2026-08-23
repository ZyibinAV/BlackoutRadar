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
является коллекция ParsedOutage.

---

# Общая архитектура

Подсистема состоит
из следующих компонентов:

- Provider Registry;
- OutageProvider;
- Adapter Layer;
- ParsedOutage;
- Scheduler.

Каждый компонент
имеет единственную ответственность.

---

# Общая схема

External Source

↓

OutageProvider

↓

Provider Technology

↓

Adapter

↓

ParsedOutage

↓

Outage Processing Pipeline

---

# OutageProvider

OutageProvider
является
единой точкой входа
для получения информации
из внешних источников.

Все реализации Provider
используют единый контракт.

---

## Ответственность

OutageProvider отвечает за:

- получение данных;
- чтение внешних документов;
- взаимодействие
  с внешними сервисами;
- использование
  необходимой Infrastructure technology;
- передачу полученных данных
  соответствующему Adapter;
- преобразование результата
  в ParsedOutage через Adapter boundary.

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
- Business Domain decisions.

---

## Основные принципы

Каждый Provider:

- работает независимо;
- не знает
  о других Provider;
- не взаимодействует
  с базой данных напрямую;
- не передает модели
  внешних библиотек
  во внутренние слои;
- возвращает ParsedOutage.

---

## Жизненный цикл

Инициализация

↓

Получение данных

↓

Преобразование

↓

Возврат ParsedOutage

↓

Завершение работы

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
отвечает
за регистрацию
и предоставление
доступных OutageProvider.

Registry является
единой точкой,
через которую
Scheduler получает Provider
для выполнения обработки.

---

## Назначение

Обеспечить централизованное
управление
всеми зарегистрированными
Provider.

---

## Ответственность

Provider Registry отвечает за:

- регистрацию Provider;
- предоставление списка Provider;
- поиск Provider
  по типу источника;
- проверку доступности Provider.

---

## Не отвечает

Provider Registry
не отвечает за:

- выполнение парсинга;
- сохранение данных;
- обработку ошибок Provider;
- Business Domain Logic.

---

## Основные принципы

Каждый Provider:

- регистрируется один раз;
- имеет уникальный тип;
- может быть заменен
  без изменения Registry.

---

## Жизненный цикл

Регистрация

↓

Использование

↓

Удаление
(при необходимости)

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
- создание ParsedOutage.

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
- возвращает ParsedOutage.

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
является внутренним контрактом
между Parser Subsystem
и Outage Processing Pipeline.

ParsedOutage
не является
сущностью базы данных.

---

## Назначение

Представляет результат
обработки одного события
отключения,
полученного
из внешнего источника.

---

## Ответственность

ParsedOutage отвечает за:

- передачу данных
  между подсистемами;
- временное хранение
  результатов парсинга;
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
  получения внешних данных.

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

ParsedOutage

↓

AddressService

↓

canonical Address

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
определяется TASK 13.

---

# Scheduler

Scheduler
отвечает
за запуск
процесса получения данных
из внешних источников.

Scheduler является
точкой входа
в Outage Processing Pipeline.

---

## Назначение

Организовать
периодический запуск
всех активных Source.

---

## Ответственность

Scheduler отвечает за:

- запуск обработки
  по расписанию;
- выбор активных Source;
- передачу управления
  соответствующему OutageProvider.

---

## Не отвечает

Scheduler
не отвечает за:

- парсинг данных;
- дедупликацию;
- поиск совпадений;
- Matching;
- отправку уведомлений.

---

## Основные принципы

Scheduler:

- работает только
  с активными Source;
- не зависит
  от конкретных Provider;
- не содержит
  бизнес-логики.

---

## Общий алгоритм

Запуск Scheduler

↓

Получение списка
активных Source

↓

Определение
необходимого Provider

↓

Получение ParsedOutage

↓

Передача
в Outage Processing Pipeline

---

# Ошибки обработки

Ошибка обработки
одного Source
не должна останавливать
обработку остальных.

---

# Основные принципы

## 1. Изоляция ошибок

Каждый Source
обрабатывается
независимо.

---

## 2. Частичный успех

Если обработка
части источников
завершилась успешно,

результат
должен быть передан
на соответствующий
этап Pipeline.

---

## 3. Повторная обработка

Временные ошибки
могут быть
повторно обработаны
при следующем запуске
Scheduler.

---

## 4. Логирование

Все ошибки
подлежат логированию.

Логирование
не заменяет
обработку ошибок.

---

## 5. Отказоустойчивость

Недоступность
одного Provider
не должна влиять
на работу остальных Provider.

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

1. Реализовать OutageProvider.

2. При необходимости
   создать Adapter.

3. Выбрать необходимую
   Provider Technology.

4. Зарегистрировать Provider
   в Provider Registry.

После этого
новый источник
становится доступным
Scheduler.

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
  что и другие Provider.

---

# Runtime Boundary

Scraping и document-processing
technologies являются
Infrastructure concerns.

Они не должны
становиться зависимостями:

- Domain Model;
- Domain Ports;
- Application business logic;
- ParsedOutage contract.

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