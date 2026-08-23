# ADR-010 — Canonical Address Resolution and Concurrent Creation

## Status

Accepted

## Date

2026-08-23

## Context

BlackoutRadar использует Canonical Address Model.

Address Catalog отвечает за:

- хранение канонических адресных объектов;
- нормализацию адресных данных;
- идентификацию канонических объектов;
- предоставление канонических объектов остальным подсистемам.

Application Layer координирует Address resolution через Domain Ports.

Текущая модель содержит отдельные Domain Ports для:

- Region;
- RegionalDistrict;
- City;
- CityDistrict;
- Street;
- Address.

Существующие Ports уже предоставляют:

- find operations;
- save operations.

Однако последовательность:

find
→ отсутствует
→ save
→ конфликт уникальности
→ повторный find

не является корректной архитектурной моделью конкурентного canonical resolution.

Использование DataIntegrityViolationException как штатного механизма разрешения конкурентного создания имеет следующие недостатки:

- database constraint violation используется как control flow;
- exception может перевести текущую transaction в rollback-only состояние;
- Application Layer начинает зависеть от persistence failure semantics;
- для корректного продолжения могут потребоваться дополнительные transaction boundaries;
- concurrency logic становится частью AddressService;
- поведение зависит от конкретной persistence implementation.

При этом физическая Database Model уже содержит необходимые UNIQUE constraints и partial UNIQUE indexes, соответствующие canonical identity Address Catalog.

## Decision

Для canonical Address resolution вводится специализированная операция resolveCanonical.

resolveCanonical означает:

"вернуть существующий canonical object либо атомарно создать его, если объект с такой canonical identity отсутствует".

Операция является capability Address Catalog и не является generic CRUD operation.

Существующая операция save сохраняется без изменения своей семантики.

save остается обычной persistence operation.

resolveCanonical используется только для canonical identity resolution.

## Domain Port Contract

Domain Ports выражают canonical identity, а не database representation.

Domain Port не должен содержать:

- UUID foreign key identifiers вместо Domain objects без необходимости;
- JPA Entity;
- Spring Data Repository;
- SQL;
- PostgreSQL-specific concepts;
- ON CONFLICT;
- persistence exceptions.

### Region

Region определяется canonical name.

Контракт:

Region resolveCanonical(String canonicalName);

### RegionalDistrict

RegionalDistrict определяется:

Region
+
RegionalDistrictType
+
CanonicalName

Контракт:

RegionalDistrict resolveCanonical(
Region region,
RegionalDistrictType type,
String canonicalName
);

### City

City имеет две различные canonical identity forms.

City непосредственно внутри Region:

Region
+
CanonicalName

City внутри RegionalDistrict:

RegionalDistrict
+
CanonicalName

Для устранения ambiguity используются два отдельных метода.

Контракты:

City resolveCanonicalInRegion(
Region region,
String canonicalName
);

City resolveCanonicalInRegionalDistrict(
RegionalDistrict regionalDistrict,
String canonicalName
);

Не используется единый метод с nullable RegionalDistrict.

### CityDistrict

CityDistrict определяется:

City
+
CanonicalName

Контракт:

CityDistrict resolveCanonical(
City city,
String canonicalName
);

### Street

Street определяется:

City
+
StreetType
+
CanonicalName

Контракт:

Street resolveCanonical(
City city,
StreetType type,
String canonicalName
);

### Address

Address определяется:

Street
+
optional CityDistrict
+
CanonicalHouse

Для устранения ambiguity используются два отдельных метода.

Контракты:

Address resolveCanonical(
Street street,
House house
);

Address resolveCanonical(
Street street,
CityDistrict cityDistrict,
House house
);

## Persistence Responsibility

Persistence Adapter реализует resolveCanonical атомарно.

Persistence Layer отвечает за:

- попытку создания canonical row;
- использование database uniqueness;
- корректное разрешение concurrent creation;
- получение canonical row после INSERT;
- mapping Persistence Entity в Domain Entity.

Application Layer не обрабатывает unique constraint violation как штатный concurrency mechanism.

Domain Layer не знает о механизме database concurrency.

## PostgreSQL Implementation

Для PostgreSQL используется:

INSERT ... ON CONFLICT DO NOTHING

После INSERT выполняется canonical lookup.

Общая схема:

resolveCanonical
→ atomic INSERT IF ABSENT
→ canonical SELECT
→ Persistence Entity
→ Domain Entity

Если запись уже существовала, INSERT не изменяет существующую canonical row.

Если другая transaction одновременно создает ту же canonical row, PostgreSQL UNIQUE enforcement и ON CONFLICT DO NOTHING разрешают race без передачи unique violation в Application Layer.

## Conflict Targets

Каждая resolveCanonical implementation должна использовать canonical identity соответствующего объекта.

### Region

UNIQUE:

name

### RegionalDistrict

UNIQUE:

region_id
+
type
+
name

### City without RegionalDistrict

Partial UNIQUE:

region_id
+
name

при:

regional_district_id IS NULL

### City with RegionalDistrict

Partial UNIQUE:

regional_district_id
+
name

при:

regional_district_id IS NOT NULL

### CityDistrict

UNIQUE:

city_id
+
name

### Street

UNIQUE:

city_id
+
type
+
canonical_name

### Address without CityDistrict

Partial UNIQUE:

street_id
+
canonical_house

при:

city_district_id IS NULL

### Address with CityDistrict

Partial UNIQUE:

street_id
+
city_district_id
+
canonical_house

при:

city_district_id IS NOT NULL

## Address city_id

Persistence-specific city_id не является частью Domain Model.

city_id:

- не является частью canonical Address identity;
- не используется в business logic;
- используется для composite foreign keys;
- обеспечивает целостность связей Address со Street и CityDistrict.

resolveCanonical для Address не должен включать city_id в Domain contract.

## Transaction Boundary

AddressService сохраняет единую transaction boundary для canonical Address resolution.

Общая схема:

AddressService transaction
→ Region resolution
→ RegionalDistrict resolution
→ City resolution
→ CityDistrict resolution
→ Street resolution
→ Address resolution

Каждая persistence operation выполняет atomic insert-if-absent внутри существующей transaction.

Дополнительные transaction boundaries для разрешения unique conflicts не вводятся.

REQUIRES_NEW не используется.

NESTED transactions и savepoints для canonical resolution не используются.

## Exception Handling

DataIntegrityViolationException не используется как штатный механизм разрешения конкурентного создания canonical Address objects.

Expected concurrency behavior должен быть реализован через database-native atomic insertion.

Unexpected persistence failures по-прежнему обрабатываются как ошибки persistence layer согласно общей error handling policy проекта.

## Persistence Context

После native INSERT Persistence Layer получает canonical object через отдельный lookup.

Native INSERT не используется как источник managed Persistence Entity.

Persistence Layer не должен возвращать объект, созданный вручную на основании только результата INSERT, если canonical row может быть создана другой transaction.

Canonical SELECT является источником истины для возвращаемого Domain object.

## Existing save Operation

Семантика существующего save не изменяется.

save продолжает использоваться для обычных persistence operations и существующих persistence tests.

resolveCanonical является отдельной capability и не заменяет generic persistence operation save.

## Application Responsibility

AddressService отвечает за:

- normalization;
- порядок разрешения Address Catalog;
- выбор City resolution path;
- выбор Address resolution path;
- проверку Domain invariants;
- orchestration.

AddressService не отвечает за:

- database unique constraints;
- PostgreSQL conflict handling;
- persistence exceptions как concurrency control;
- JPA Entity management;
- SQL execution.

## Domain Boundary

Domain остается framework-free.

Domain Model не зависит от:

- Spring;
- Spring Boot;
- Spring Data;
- JPA;
- Hibernate;
- PostgreSQL;
- Liquibase;
- MapStruct;
- Lombok.

resolveCanonical является Domain Port capability.

Database-specific implementation остается в Infrastructure.

## Testing Requirements

Canonical resolution должна проверяться на трех уровнях.

### Unit Tests

AddressService tests должны проверять:

- использование resolveCanonical;
- корректный порядок resolution;
- корректный выбор City resolution path;
- корректный выбор Address resolution path;
- отсутствие find/save orchestration для canonical creation.

### Persistence Integration Tests

Для каждого canonical entity должны проверяться:

- первая resolution создает объект;
- повторная resolution возвращает тот же объект;
- разные canonical identities создают разные объекты;
- database uniqueness сохраняется;
- mapping Persistence → Domain корректен.

### Concurrency Integration Tests

Необходимо проверить concurrent resolution одинаковой canonical identity.

Несколько независимых transactions должны одновременно выполнять одну canonical resolution.

Ожидаемый результат:

- все callers получают один canonical object identity;
- database содержит одну canonical row;
- unique constraints не нарушаются;
- Application Layer не получает ожидаемую unique constraint exception;
- persistence context isolation сохраняется.

Для concurrency tests каждый worker должен использовать собственную transaction.

## Consequences

### Positive

- canonical identity выражена непосредственно в Domain Port;
- Application Layer не содержит database concurrency control;
- существующий save API не меняет семантику;
- unique constraint violation больше не используется как штатный control flow;
- concurrency semantics обеспечиваются database atomicity;
- Domain остается независимым от PostgreSQL;
- Persistence Adapter остается replaceable;
- canonical identity соответствует ADR-002;
- City и Address ambiguity устранена явными API operations;
- implementation хорошо соответствует существующей PostgreSQL Database Model.

### Negative

- каждый Address Port получает дополнительную operation;
- Persistence repositories требуют database-specific insert operations;
- native SQL появляется в Persistence Layer;
- для concurrent correctness необходимы integration tests;
- PostgreSQL-specific implementation требует отдельной адаптации при смене database technology.

### Neutral

resolveCanonical является специализированной capability Address Catalog.

Не следует распространять этот API на другие Domain Ports без отдельного архитектурного обоснования.

## Alternatives Considered

### Find → Save → Catch → Re-read

Отклонено.

Причины:

- exception используется как expected control flow;
- unique violation может привести transaction в rollback-only state;
- Application Layer зависит от persistence failure semantics;
- concurrency behavior сложнее;
- возможна необходимость дополнительных transaction boundaries.

### Изменение semantics save

Отклонено.

Причины:

- save уже является частью существующего Domain Port API;
- save используется существующими persistence tests;
- generic save не должен неожиданно превращаться в canonical resolution operation;
- разделение обычной persistence operation и canonical resolution более явно выражает намерение.

### Generic getOrCreate

Отклонено.

Причины:

- термин не отражает canonical identity;
- создается generic CRUD-like abstraction;
- появляется риск распространения getOrCreate на несвязанные Domain Ports;
- resolveCanonical точнее описывает ответственность Address Catalog.

### REQUIRES_NEW

Отклонено.

Причины:

- не требуется при использовании database-native atomic insert;
- усложняет transaction semantics;
- создаёт дополнительные physical transactions;
- увеличивает нагрузку на connection pool.

### NESTED / Savepoints

Отклонено.

Причины:

- unique conflict не должен использоваться как expected exception flow;
- savepoint не требуется при ON CONFLICT DO NOTHING.

### ON CONFLICT DO UPDATE

Отклонено.

Причины:

- существующая canonical row не должна обновляться только ради получения её identity;
- операция является resolution, а не update;
- DO NOTHING лучше соответствует semantics "insert if absent".

### Application-level synchronization

Отклонено.

Не используются:

- synchronized;
- JVM locks;
- process-local mutex;
- distributed locks.

Причина:

canonical uniqueness является database invariant и должна оставаться корректной при нескольких application instances.

## Relationship with Existing ADRs

### ADR-001 — Domain First Architecture

Решение сохраняет Domain First и Dependency Inversion.

Domain Port находится во внутреннем слое.

Persistence implementation остается Infrastructure responsibility.

### ADR-002 — Canonical Address Model

resolveCanonical использует canonical identity, определенную ADR-002.

Новые методы не изменяют canonical identity.

### ADR-007 — Replaceable Infrastructure

Database-specific implementation остается за Persistence Adapter.

Domain contract не содержит PostgreSQL-specific details.

### ADR-008 — MapStruct SPI Build Infrastructure

resolveCanonical не изменяет MapStruct architecture.

MapStruct продолжает использоваться для Domain ↔ Persistence mapping.

## Relationship with TASK 13

ADR-010 является архитектурным решением, уточняющим implementation scope TASK 13.

TASK 13 должен реализовать:

- Address normalization;
- canonical Address resolution;
- resolveCanonical Domain Port operations;
- atomic persistence resolution;
- соответствующие tests.

TASK 13 не должен:

- изменять canonical identity;
- изменять Database Schema без отдельного решения;
- добавлять PostgreSQL concepts в Domain;
- изменять semantics существующего save;
- добавлять generic getOrCreate API;
- реализовывать concurrency через JVM locks;
- использовать expected DataIntegrityViolationException как control flow.

## Decision Summary

BlackoutRadar использует специализированный resolveCanonical contract для canonical Address Catalog operations.

Application Layer orchestrates canonical resolution.

Domain Ports выражают canonical identity.

Persistence Adapters обеспечивают atomic insert-if-absent.

PostgreSQL UNIQUE constraints и ON CONFLICT DO NOTHING обеспечивают concurrent correctness.

После atomic insert выполняется canonical lookup.

Существующий save остается обычной persistence operation.

Domain не зависит от PostgreSQL или других infrastructure technologies.