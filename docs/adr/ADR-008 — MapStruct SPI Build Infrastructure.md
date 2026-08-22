# ADR-008 — MapStruct SPI Build Infrastructure

> Архитектурное решение о выделении custom MapStruct SPI в отдельный Maven module.

---

## Статус

**Accepted**

---

## Дата

2026-08-16

---

## Версия

1.0

---

## Навигация

| Раздел | Ссылка |
|---------|--------|
| ⬅ Предыдущий | [ADR-007](ADR-007-Replaceable-Infrastructure.md) |
| 🏠 Документация | [README](../README.md) |
| 📚 ADR Index | [README](README.md) |

---

## Влияние

Данное решение оказывает влияние на:

- Persistence Layer;
- MapStruct mapping infrastructure;
- Maven build configuration;
- модульную структуру проекта;
- annotation processing.

---

## Связанные документы

- [01-ARCHITECTURE](../01-ARCHITECTURE.md)
- [02-DOMAIN_MODEL](../02-DOMAIN_MODEL.md)
- [03-DATABASE](../03-DATABASE.md)
- [ADR-001 — Domain First Architecture](ADR-001-Domain-First-Architecture.md)
- [ADR-007 — Replaceable Infrastructure](ADR-007-Replaceable-Infrastructure.md)

---

# ADR-008 — MapStruct SPI Build Infrastructure

**Статус:** Accepted

**Дата:** 2026-08-16

**Версия:** 1.0

---

# Назначение

Зафиксировать архитектурное решение
по размещению custom MapStruct SPI,
необходимого для преобразования
immutable Domain Model
с fluent accessors
в Persistence Model
с JavaBean accessors.

Решение определяет:

- границу custom MapStruct SPI;
- его Maven module;
- зависимости;
- место размещения SPI;
- способ подключения к annotation processor classpath;
- отсутствие зависимости Domain от SPI.

---

# Контекст

BlackoutRadar использует
MapStruct для преобразования
между Domain Model
и Persistence Model.

Persistence Entity
являются техническими
JPA representation
и отделены
от Domain Entity.

Mapping выполняется
в Infrastructure Layer.

Domain Model
при этом остается
immutable
и использует
fluent accessors:

- `id()`;
- `name()`;
- `region()`;
- `regionalDistrict()`;
- другие accessor methods
  аналогичного типа.

Persistence Entity
используют
обычные JavaBean accessors.

Стандартная MapStruct
`DefaultAccessorNamingStrategy`
не распознает
необходимые fluent accessors
Domain Model
как properties.

Для решения этой проблемы
используется custom:

`FluentAccessorNamingStrategy`

на основе
MapStruct SPI:

`AccessorNamingStrategy`.

---

# Проблема

Custom MapStruct SPI
загружается через
`ServiceLoader`
из classloader
annotation processor.

В single-module Maven configuration
SPI, скомпилированный
в основном application module,
не оказывается
на classpath
MapStruct annotation processor.

Были проверены
два сценария.

## Сценарий 1

SPI находится
в обычном application classpath.

Результат:

MapStruct processor
не видит
custom SPI.

Используется
`DefaultAccessorNamingStrategy`.

Fluent Domain accessors
не распознаются.

Generated mapping
становится некорректным.

## Сценарий 2

SPI предварительно
компилируется
в отдельный directory
и вручную добавляется
в processor classpath.

Результат:

custom SPI
корректно загружается.

MapStruct
генерирует
необходимый mapping.

Следовательно,
проблема связана
с classloader isolation
annotation processor,
а не с самим
MapStruct mapping model.

---

# Проверенный рабочий вариант

Для single-module проекта
была создана временная
двухфазная Maven configuration:

`compile-spi`

↓

`target/spi-classes`

↓

копирование
`META-INF/services`

↓

формирование
processor classpath

↓

MapStruct processor

Данный вариант
полностью работоспособен.

Однако он требует:

- отдельной компиляции SPI;
- дополнительной Maven lifecycle configuration;
- формирования собственного processor path;
- использования нескольких Maven plugins;
- ручной передачи SPI
  в annotation processor classpath.

Такая конфигурация
является техническим workaround
и не является
предпочтительной архитектурой.

---

# Рассмотренные варианты

## Вариант 1 — Изменить Domain Model

Изменить Domain accessors
на JavaBean style:

`getId()`

`getName()`

`getRegion()`

### Преимущества

- стандартный MapStruct;
- не требуется custom SPI.

### Недостатки

- Domain Model
  начинает подстраиваться
  под инфраструктурный инструмент;
- увеличивается coupling
  Domain Model
  к техническому соглашению;
- нарушается принцип
  Domain First;
- решение распространяется
  на всю Domain Model.

### Решение

**Отклонен.**

Domain Model
не должна изменяться
ради MapStruct.

---

## Вариант 2 — Использовать Lombok в Domain

Добавить Lombok
и JavaBean accessors
в Domain Model.

### Преимущества

- уменьшение boilerplate;
- потенциально простой MapStruct mapping.

### Недостатки

- Domain получает
  framework/tooling dependency;
- теряется явный контроль
  над Domain Model;
- нарушается принятый принцип
  чистого Domain;
- Lombok не решает
  factory-based construction
  immutable Domain.

### Решение

**Отклонен.**

Lombok разрешен
в Infrastructure/Persistence,
но не используется
в Domain.

---

## Вариант 3 — Полностью ручной mapping

Не использовать
custom MapStruct SPI
и реализовать
Domain ↔ Persistence mapping
вручную.

### Преимущества

- простая Maven configuration;
- полный контроль
  над mapping.

### Недостатки

- дублирование mapping logic;
- увеличение boilerplate;
- потеря преимуществ
  compile-time generated mapping;
- сложнее поддерживать
  nested mapping graph;
- противоречит принятому
  использованию MapStruct
  в Persistence Layer.

### Решение

**Отклонен.**

---

## Вариант 4 — Single-module Maven workaround

Использовать
двухфазную компиляцию SPI
внутри основного module.

### Преимущества

- не требуется
  дополнительный Maven module;
- решение уже доказало
  работоспособность;
- Domain остается чистым.

### Недостатки

- сложный `pom.xml`;
- нестандартная Maven lifecycle configuration;
- ручное формирование
  processor classpath;
- несколько дополнительных
  build plugin executions;
- build infrastructure
  смешивается
  с application module.

### Решение

**Отклонен как предпочтительная
долгосрочная архитектура.**

Допускается как временный
технический fallback,
если отдельный SPI module
еще не реализован.

---

## Вариант 5 — Отдельный Maven module

Создать отдельный module:

`blackoutradar-mapstruct-spi`

который содержит:

- `FluentAccessorNamingStrategy`;
- `META-INF/services/...`.

JAR этого module
подключается
исключительно
к annotation processor classpath
основного application module.

### Преимущества

- SPI физически находится
  на processor classpath;
- стандартная Maven
  dependency graph;
- основной `pom.xml`
  не содержит
  custom SPI bootstrap;
- четкое разделение
  application и build-time tooling;
- SPI можно
  независимо тестировать
  и собирать;
- Domain не получает
  зависимость от SPI.

### Недостатки

- появляется дополнительный
  Maven module;
- увеличивается
  модульная структура проекта;
- требуется дополнительная
  сборочная конфигурация.

### Решение

**Принят.**

---

# Решение

Custom MapStruct SPI
выделяется в отдельный
Maven module:

`blackoutradar-mapstruct-spi`

Module является
build-time infrastructure
и не является:

- Domain module;
- Application module;
- Persistence runtime component;
- Spring component.

---

# Граница module

Module содержит
только инфраструктуру,
необходимую для
MapStruct annotation processing.

Минимальное содержимое:

- `FluentAccessorNamingStrategy`;
- service registration
  `META-INF/services/org.mapstruct.ap.spi.AccessorNamingStrategy`.

Module не содержит:

- Domain Entity;
- Domain Ports;
- Persistence Entity;
- Repository;
- Persistence Adapter;
- Spring configuration;
- business logic;
- database logic.

---

# Package

Custom SPI размещается
в отдельном module
с package:

`com.zyibin.app.blackoutradar.mapstruct.spi`

Persistence package
не используется.

Причина:

custom SPI является
build-time tooling,
а не Persistence implementation.

---

# Зависимости SPI Module

SPI module зависит
от:

`org.mapstruct:mapstruct-processor`

поскольку
`FluentAccessorNamingStrategy`
расширяет
MapStruct processor SPI.

SPI module
не зависит от:

- Spring;
- Spring Boot;
- JPA;
- Hibernate;
- PostgreSQL;
- Domain;
- Persistence Entity;
- Application Layer.

---

# Основной Application Module

Основной module
продолжает использовать:

- `mapstruct`;
- `lombok`;
- `lombok-mapstruct-binding`.

MapStruct processor
используется
как annotation processor.

Custom SPI module
подключается
только через
annotation processor classpath.

Целевая схема:

`annotationProcessorPaths`

↓

- `mapstruct-processor`;
- `lombok`;
- `lombok-mapstruct-binding`;
- `blackoutradar-mapstruct-spi`.

Custom SPI
не является
runtime dependency
application module.

---

# Dependency Direction

Целевая dependency direction:

`blackoutradar-mapstruct-spi`

↓

`MapStruct Processor`

и:

`blackoutradar`

↓

annotation processor classpath

↓

`blackoutradar-mapstruct-spi`

Domain при этом
не имеет зависимости
на:

- `blackoutradar-mapstruct-spi`;
- MapStruct;
- Lombok;
- Spring;
- JPA.

---

# Build Architecture

Целевая Maven architecture:

`blackoutradar-mapstruct-spi`

↓

JAR

↓

annotation processor classpath

↓

MapStruct processor

↓

Mapper generation

↓

BlackoutRadar application module.

Основной application module
не должен выполнять:

- предварительную
  компиляцию SPI;
- копирование SPI
  в `target/spi-classes`;
- ручное формирование
  processor classpath;
- отдельный lifecycle
  для SPI compilation.

---

# Lombok Integration

`lombok-mapstruct-binding`
остается отдельной
частью annotation processing.

Ответственность компонентов:

`lombok-mapstruct-binding`

→ корректное взаимодействие
Lombok и MapStruct.

`blackoutradar-mapstruct-spi`

→ поддержка fluent accessors
принятой Domain Model
в MapStruct.

Эти зависимости
решают разные задачи
и не заменяют друг друга.

---

# Domain Independence

Domain Model
не изменяется
ради MapStruct.

Domain продолжает
использовать:

- immutable objects;
- private constructors;
- static factories;
- fluent accessors.

Domain не содержит:

- MapStruct annotations;
- Lombok annotations;
- Spring annotations;
- JPA annotations.

Custom SPI
адаптирует MapStruct
к существующей Domain Model.

---

# Persistence Mapping

Persistence Layer
продолжает использовать
MapStruct для mapping:

`Domain Model`

↓

`MapStruct`

↓

`Persistence Entity`

и:

`Persistence Entity`

↓

`MapStruct`

↓

`Domain Model`.

Custom SPI
не изменяет
границы Persistence.

Persistence Entity
остается отдельной
моделью хранения.

---

# Object Factory

MapStruct
может использовать
`@ObjectFactory`
для создания
immutable Domain objects
через существующие
Domain static factories.

Custom SPI
не является заменой
Domain factories.

Responsibilities разделены:

- AccessorNamingStrategy —
  распознавание Domain properties;
- MapStruct —
  generated mapping;
- ObjectFactory —
  создание Domain object;
- Domain factory —
  применение Domain construction rules.

---

# Consequences

## Положительные

### Domain остается чистым

Domain не адаптируется
под технический
mapping framework.

### Mapping остается generated

Основная mapping logic
генерируется MapStruct.

### Maven build становится проще

Основной module
не содержит
специальный bootstrap
для компиляции SPI.

### Classloader boundary становится явной

SPI находится
непосредственно
на processor classpath.

### Инфраструктура заменяема

Custom SPI
является техническим
компонентом
и может быть заменен
без изменения
Domain Model.

### Ответственности разделены

Application module
не отвечает
за компиляцию
собственного
annotation processor SPI.

---

# Отрицательные последствия

### Дополнительный Maven module

Появляется:

`blackoutradar-mapstruct-spi`

### Дополнительный artifact

SPI должен собираться
до его использования
основным module.

### Усложнение структуры

Проект становится
минимально multi-module:

`blackoutradar`

+

`blackoutradar-mapstruct-spi`.

---

# Архитектурные ограничения

Решение не означает,
что каждый технический
инструмент должен
получать отдельный module.

Новый module создается
только потому,
что MapStruct annotation processor
имеет отдельный
processor classloader,
и custom SPI физически
должен находиться
на его classpath.

Не создавать
дополнительные modules
для Lombok,
MapStruct или других
обычных dependencies
без отдельного
архитектурного решения.

---

# Rejected Technical Direction

Не использовать
в дальнейшем
single-module workaround
с:

- `target/spi-classes`;
- `compile-spi`;
- ручным `-processorpath`;
- дополнительным
  копированием service files;
- отдельным
  dependency plugin
  только для SPI bootstrap.

Эта схема была
необходима для POC,
но после принятия
ADR-008 заменяется
отдельным module.

---

# Связь с Domain First

Решение сохраняет
основное направление
зависимостей:

`Infrastructure`

↓

`Application`

↓

`Domain`.

Custom MapStruct SPI
не является частью
Domain Model.

Он находится
в техническом
build-time слое
и используется
только для генерации
Infrastructure mapping code.

---

# Связь с Replaceable Infrastructure

ADR-007 устанавливает,
что инфраструктурные
компоненты являются
заменяемыми
и не должны
проникать в Domain.

Custom MapStruct SPI
является частью
этой технической
границы.

Замена:

- MapStruct;
- custom SPI;
- mapping implementation

не должна
требовать изменения
Domain business rules.

---

# Реализационные последствия

После принятия ADR-008
необходимо:

1. создать Maven module
   `blackoutradar-mapstruct-spi`;
2. перенести
   `FluentAccessorNamingStrategy`;
3. перенести
   service registration;
4. добавить module
   в Maven reactor;
5. подключить SPI JAR
   к annotation processor
   classpath основного module;
6. удалить single-module
   SPI workaround;
7. выполнить clean build;
8. подтвердить generated
   MapStruct implementations;
9. подтвердить полный
   test suite.

Эти действия выполняются
отдельным TASK.

ADR-008 не является
детальной постановкой
этого TASK.

---

# Проверка принятого решения

Решение считается
корректно реализованным,
если:

- `blackoutradar-mapstruct-spi`
  собирается отдельно;
- SPI JAR содержит
  `META-INF/services`;
- основной module
  видит SPI через
  annotation processor classpath;
- MapStruct использует
  `FluentAccessorNamingStrategy`;
- fluent Domain accessors
  корректно распознаются;
- generated Mapper implementations
  корректны;
- Domain Model
  не изменяется;
- runtime dependency
  основного application module
  на SPI отсутствует;
- single-module SPI workaround
  удален;
- `mvn clean test`
  проходит с нуля.

---

# Итоговое решение

**Принято:**

Custom MapStruct SPI
выделяется в отдельный
Maven module:

`blackoutradar-mapstruct-spi`.

Module является
build-time infrastructure.

Он содержит
только custom MapStruct SPI
и service registration.

SPI JAR подключается
исключительно
к annotation processor classpath
основного BlackoutRadar module.

Domain Model
остается чистой
и независимой
от MapStruct,
Lombok и Spring.

Текущий single-module
SPI workaround
после реализации
нового решения
удаляется.