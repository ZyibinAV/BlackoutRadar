# Database

> Физическая модель хранения данных BlackoutRadar.

---

# Назначение

Документ является
основной спецификацией
структуры PostgreSQL.

Database реализует
уже принятые
архитектурные решения
и не определяет
предметную область.

---

# Используемая СУБД

PostgreSQL 18.

---

# Основные правила

## 1. Primary Key

Все основные сущности
используют UUID.

Автоинкрементные
идентификаторы
не используются.

---

## 2. Нормализация

Структура соответствует
третьей нормальной форме.

Дублирование данных
допускается только
при наличии
обоснованной причины.

---

## 3. Ограничения

База данных должна
обеспечивать на своем уровне:

- PRIMARY KEY;
- FOREIGN KEY;
- UNIQUE;
- CHECK;
- NOT NULL там, где
  соответствующее поле
  является обязательным
  по Database Model.

Nullable-поля явно
фиксируются в описании
соответствующей таблицы.

---

## 4. История

Исторически значимые
данные не удаляются
без необходимости.

---

## 5. Индексы

Индексы являются
частью архитектуры.

Они проектируются
до реализации запросов
к production-данным.

---

# Именование

## Таблицы

Используются имена
в единственном числе:

- user
- subscription
- address
- power_outage

## Столбцы

Используется snake_case:

- created_at
- updated_at
- house_number
- canonical_name

## Foreign Key

Используется имя:

entity_id

Например:

- user_id
- address_id
- source_id

---

# Типы данных

| Назначение | PostgreSQL |
|---|---|
| идентификатор | UUID |
| короткий текст | VARCHAR |
| длинный текст | TEXT |
| дата | DATE |
| дата и время | TIMESTAMP WITH TIME ZONE |
| логическое значение | BOOLEAN |
| структурированная конфигурация | JSONB |
| перечисление | VARCHAR |

---

# Работа со временем

Все временные значения,
представляющие конкретный
момент времени,
хранятся как:

TIMESTAMP WITH TIME ZONE

В Java используется
java.time.

Для точных моментов времени
используется:

Instant

Не используются:

- java.util.Date;
- java.sql.Timestamp.

---

# Миграции

Изменение структуры БД
выполняется только
через Liquibase.

Ручное изменение
структуры БД
не допускается.

---

# Identity

## Таблица user

### Назначение

Хранит учетные записи
пользователей.

### Поля

| Поле | Тип | NULL | Ограничения |
|---|---|---|---|
| id | UUID | NO | PK |
| email | VARCHAR | NO | UNIQUE |
| password_hash | VARCHAR | YES | |
| role | VARCHAR | NO | |
| is_active | BOOLEAN | NO | DEFAULT TRUE |
| nickname | VARCHAR | YES | |
| about | TEXT | YES | |
| avatar_key | VARCHAR | YES | |
| created_at | TIMESTAMP WITH TIME ZONE | NO | |
| updated_at | TIMESTAMP WITH TIME ZONE | NO | |

### Основные ограничения

- email уникален;
- email обязателен;
- role обязателен;
- is_active обязателен.

password_hash может отсутствовать
для учетной записи,
созданной через OAuth2.

Пароль в открытом виде
не хранится.

### Связи

user

↓

subscription (1:N)

user

↓

refresh_token (1:N)

---

## Таблица refresh_token

### Назначение

Хранит Refresh Token
в защищенном представлении.

Исходное значение токена
в базе данных не хранится.

### Поля

| Поле | Тип | NULL | Ограничения |
|---|---|---|---|
| id | UUID | NO | PK |
| user_id | UUID | NO | FK |
| token_hash | VARCHAR | NO | UNIQUE |
| expires_at | TIMESTAMP WITH TIME ZONE | NO | |
| revoked_at | TIMESTAMP WITH TIME ZONE | YES | |
| created_at | TIMESTAMP WITH TIME ZONE | NO | |
| updated_at | TIMESTAMP WITH TIME ZONE | NO | |

### Индексы

- PK(id);
- UNIQUE(token_hash);
- INDEX(user_id);
- INDEX(expires_at).

### ON DELETE

user → refresh_token:

CASCADE

---

# Address Catalog

Address Catalog хранит
канонические адреса.

Все остальные подсистемы
используют канонические
Address.

---

## Таблица region

### Поля

| Поле | Тип | NULL | Ограничения |
|---|---|---|---|
| id | UUID | NO | PK |
| name | VARCHAR | NO | UNIQUE |
| created_at | TIMESTAMP WITH TIME ZONE | NO | |
| updated_at | TIMESTAMP WITH TIME ZONE | NO | |

### Индексы

- PK(id);
- UNIQUE(name).

### Связи

region

↓

regional_district (1:N)

region

↓

city (1:N)

### ON DELETE

region → regional_district:

RESTRICT

region → city:

RESTRICT

---

## Таблица regional_district

### Назначение

Хранит административно-
муниципальные единицы
внутри Region.

### Поля

| Поле | Тип | NULL | Ограничения |
|---|---|---|---|
| id | UUID | NO | PK |
| region_id | UUID | NO | FK |
| type | VARCHAR | NO | |
| name | VARCHAR | NO | |
| created_at | TIMESTAMP WITH TIME ZONE | NO | |
| updated_at | TIMESTAMP WITH TIME ZONE | NO | |

### Типы

- MUNICIPAL_DISTRICT
- MUNICIPAL_OKRUG
- URBAN_OKRUG
- INTRACITY_TERRITORY
- FEDERAL_TERRITORY

### Ограничения

UNIQUE(region_id, type, name)

### Индексы

- PK(id);
- UNIQUE(region_id, type, name);
- INDEX(region_id).

### Связи

regional_district

↓

region (N:1)

regional_district

↓

city (1:N)

### ON DELETE

region → regional_district:

RESTRICT

---

## Таблица city

### Назначение

Хранит населенные пункты.

City всегда принадлежит
Region.

RegionalDistrict является
необязательным.

### Поля

| Поле | Тип | NULL | Ограничения |
|---|---|---|---|
| id | UUID | NO | PK |
| region_id | UUID | NO | FK |
| regional_district_id | UUID | YES | FK |
| name | VARCHAR | NO | |
| created_at | TIMESTAMP WITH TIME ZONE | NO | |
| updated_at | TIMESTAMP WITH TIME ZONE | NO | |

### Целостность

City может принадлежать
RegionalDistrict только
внутри того же Region.

Это обеспечивается
составным FOREIGN KEY.

### Уникальность

Для City внутри RegionalDistrict:

UNIQUE(regional_district_id, name)

WHERE regional_district_id IS NOT NULL

Для City непосредственно
в Region:

UNIQUE(region_id, name)

WHERE regional_district_id IS NULL

### Индексы

- PK(id);
- INDEX(region_id);
- INDEX(regional_district_id);
- partial UNIQUE для каждого
  из двух вариантов принадлежности.

### Связи

city

↓

region (N:1)

city

↓

regional_district (0..1)

city

↓

city_district (1:N)

city

↓

street (1:N)

### ON DELETE

region → city:

RESTRICT

regional_district → city:

RESTRICT

---

## Таблица city_district

### Назначение

Хранит внутригородские
районы или иной локальный
районный контекст.

### Поля

| Поле | Тип | NULL | Ограничения |
|---|---|---|---|
| id | UUID | NO | PK |
| city_id | UUID | NO | FK |
| name | VARCHAR | NO | |
| created_at | TIMESTAMP WITH TIME ZONE | NO | |
| updated_at | TIMESTAMP WITH TIME ZONE | NO | |

### Ограничения

UNIQUE(city_id, name)

### Индексы

- PK(id);
- UNIQUE(city_id, name);
- INDEX(city_id).

### Связи

city_district

↓

city (N:1)

city_district

↓

address (1:N)

### ON DELETE

city → city_district:

RESTRICT

---

## Таблица street

### Назначение

Хранит канонический
справочник улиц.

Street принадлежит City,
а не CityDistrict.

Одна Street может
проходить через несколько
CityDistrict.

### Поля

| Поле | Тип | NULL | Ограничения |
|---|---|---|---|
| id | UUID | NO | PK |
| city_id | UUID | NO | FK |
| type | VARCHAR | NO | |
| canonical_name | VARCHAR | NO | |
| created_at | TIMESTAMP WITH TIME ZONE | NO | |
| updated_at | TIMESTAMP WITH TIME ZONE | NO | |

### Ограничения

UNIQUE(city_id, type, canonical_name)

### Индексы

- PK(id);
- UNIQUE(city_id, type, canonical_name);
- INDEX(city_id);
- INDEX(canonical_name).

### Связи

street

↓

city (N:1)

street

↓

address (1:N)

### ON DELETE

city → street:

RESTRICT

---

## Таблица address

### Назначение

Хранит канонические адреса.

Address является
основной единицей
Matching Engine.

### Поля

| Поле | Тип | NULL | Ограничения |
|---|---|---|---|
| id | UUID | NO | PK |
| street_id | UUID | NO | FK |
| city_district_id | UUID | YES | FK |
| house_number | VARCHAR | NO | |
| house_addition | VARCHAR | YES | |
| canonical_house | VARCHAR | NO | |
| created_at | TIMESTAMP WITH TIME ZONE | NO | |
| updated_at | TIMESTAMP WITH TIME ZONE | NO | |

Persistence-specific
`city_id` используется
для обеспечения адресной
целостности через
composite FOREIGN KEY.

`city_id` не является частью
Domain Model, канонической
идентичности Address
или бизнес-логики.

### Целостность

Address должен ссылаться
на Street и CityDistrict,
относящиеся к одному City.

Используются:

- `street_id → street(id)`;
- `city_district_id → city_district(id)`;
- `(city_id, street_id) → street(city_id, id)`;
- `(city_id, city_district_id) → city_district(city_id, id)`.

Для поддержки composite
FOREIGN KEY используются
соответствующие UNIQUE indexes.

### Индексы

- PK(id);
- INDEX(street_id);
- INDEX(city_district_id);
- INDEX(canonical_house);
- UNIQUE(street_id, canonical_house)
  WHERE city_district_id IS NULL;
- UNIQUE(street_id, city_district_id, canonical_house)
  WHERE city_district_id IS NOT NULL;
- supporting UNIQUE indexes
  для composite foreign keys.

### Связи

address

↓

street (N:1)

address

↓

city_district (0..1)

address

↓

subscription (1:N)

address

↓

power_outage_address (1:N)

### ON DELETE

street → address:

RESTRICT

city_district → address:

RESTRICT

---

# TransformerStation

## Таблица transformer_station

### Назначение

Хранит TransformerStation,
связанную с Subscription
или PowerOutageAddress.

### Поля

| Поле | Тип | NULL | Ограничения |
|---|---|---|---|
| id | UUID | NO | PK |
| name | VARCHAR | NO | UNIQUE |
| created_at | TIMESTAMP WITH TIME ZONE | NO | |
| updated_at | TIMESTAMP WITH TIME ZONE | NO | |

### Индексы

- PK(id);
- UNIQUE(name).

### Связи

transformer_station

↓

subscription_transformer_station (1:N)

transformer_station

↓

power_outage_address (1:N)

---

# Subscription

## Таблица subscription

### Назначение

Хранит подписки пользователей
на мониторинг Address.

### Поля

| Поле | Тип | NULL | Ограничения |
|---|---|---|---|
| id | UUID | NO | PK |
| user_id | UUID | NO | FK |
| address_id | UUID | NO | FK |
| monitoring_start | TIMESTAMP WITH TIME ZONE | NO | |
| monitoring_end | TIMESTAMP WITH TIME ZONE | NO | |
| is_active | BOOLEAN | NO | DEFAULT TRUE |
| service_access_until | TIMESTAMP WITH TIME ZONE | NO | |
| created_at | TIMESTAMP WITH TIME ZONE | NO | |
| updated_at | TIMESTAMP WITH TIME ZONE | NO | |

### Ограничения

CHECK(monitoring_start < monitoring_end)

### Индексы

- PK(id);
- INDEX(user_id, is_active);
- INDEX(address_id, is_active);
- INDEX(monitoring_start, monitoring_end).

### Связи

subscription

↓

user (N:1)

subscription

↓

address (N:1)

subscription

↓

subscription_transformer_station (1:N)

subscription

↓

notification (1:N)

### ON DELETE

user → subscription:

RESTRICT

address → subscription:

RESTRICT

---

## Таблица subscription_transformer_station

### Назначение

Реализует M:N
между Subscription
и TransformerStation.

### Поля

| Поле | Тип | NULL | Ограничения |
|---|---|---|---|
| id | UUID | NO | PK |
| subscription_id | UUID | NO | FK |
| transformer_station_id | UUID | NO | FK |
| created_at | TIMESTAMP WITH TIME ZONE | NO | |
| updated_at | TIMESTAMP WITH TIME ZONE | NO | |

### Ограничения

UNIQUE(
subscription_id,
transformer_station_id
)

### Индексы

- PK(id);
- UNIQUE(subscription_id, transformer_station_id);
- INDEX(subscription_id);
- INDEX(transformer_station_id).

### ON DELETE

subscription → subscription_transformer_station:

CASCADE

transformer_station →
subscription_transformer_station:

RESTRICT

---

# Source

## Таблица source

### Назначение

Хранит конфигурацию
внешнего источника.

`configuration` является
необязательной.

Отсутствие configuration
представляется SQL `NULL`.

SQL `NULL` является единственным
persistence representation
отсутствующей configuration.

Пустой JSON object `{}` не используется
как замена отсутствующей configuration.

JSONB `null` не используется
как альтернативное representation
отсутствующей configuration.

Persistence Layer не подставляет
provider-specific default configuration.

### Поля

| Поле | Тип | NULL | Ограничения |
|---|---|---|---|
| id | UUID | NO | PK |
| name | VARCHAR | NO | UNIQUE |
| source_type | VARCHAR | NO | |
| provider_type | VARCHAR | NO | |
| configuration | JSONB | YES | |
| schedule | VARCHAR | NO | |
| is_active | BOOLEAN | NO | DEFAULT TRUE |
| created_at | TIMESTAMP WITH TIME ZONE | NO | |
| updated_at | TIMESTAMP WITH TIME ZONE | NO | |

### Ограничения

- name уникален;
- configuration не содержит
  секреты;
- source_type обязателен;
- provider_type обязателен;
- schedule обязателен.

### Индексы

- PK(id);
- UNIQUE(name);
- INDEX(is_active);
- INDEX(source_type).

### Связи

source

↓

power_outage (1:N)

### ON DELETE

source → power_outage:

RESTRICT

---

# PowerOutage

## Таблица power_outage

### Поля

| Поле | Тип | NULL | Ограничения |
|---|---|---|---|
| id | UUID | NO | PK |
| source_id | UUID | NO | FK |
| start_time | TIMESTAMP WITH TIME ZONE | NO | |
| end_time | TIMESTAMP WITH TIME ZONE | NO | |
| reason | TEXT | NO | |
| status | VARCHAR | NO | |
| created_at | TIMESTAMP WITH TIME ZONE | NO | |
| updated_at | TIMESTAMP WITH TIME ZONE | NO | |

### Ограничения

CHECK(start_time < end_time)

### Индексы

- PK(id);
- INDEX(source_id);
- INDEX(start_time);
- INDEX(end_time);
- INDEX(status);
- INDEX(start_time, end_time).

### Связи

power_outage

↓

source (N:1)

power_outage

↓

power_outage_address (1:N)

power_outage

↓

notification (1:N)

### ON DELETE

source → power_outage:

RESTRICT

---

# PowerOutageAddress

## Таблица power_outage_address

### Назначение

Связывает PowerOutage
с Address.

Дополнительно хранит
TransformerStation,
если она известна.

### Поля

| Поле | Тип | NULL | Ограничения |
|---|---|---|---|
| id | UUID | NO | PK |
| power_outage_id | UUID | NO | FK |
| address_id | UUID | NO | FK |
| transformer_station_id | UUID | YES | FK |
| created_at | TIMESTAMP WITH TIME ZONE | NO | |
| updated_at | TIMESTAMP WITH TIME ZONE | NO | |

### Ограничения

UNIQUE(
power_outage_id,
address_id
)

### Индексы

- PK(id);
- UNIQUE(power_outage_id, address_id);
- INDEX(power_outage_id);
- INDEX(address_id);
- INDEX(transformer_station_id).

### Связи

power_outage_address

↓

power_outage (N:1)

power_outage_address

↓

address (N:1)

power_outage_address

↓

transformer_station (0..1)

### ON DELETE

power_outage → power_outage_address:

CASCADE

address → power_outage_address:

RESTRICT

transformer_station →
power_outage_address:

RESTRICT

---

# Notification

## Таблица notification

### Назначение

Хранит историю уведомлений,
созданных после успешного Match
в Application / Processing Flow.

Notification не хранит
техническую зависимость
от Match.

### Поля

| Поле | Тип | NULL | Ограничения |
|---|---|---|---|
| id | UUID | NO | PK |
| subscription_id | UUID | NO | FK |
| power_outage_id | UUID | NO | FK |
| message | TEXT | NO | |
| status | VARCHAR | NO | |
| created_at | TIMESTAMP WITH TIME ZONE | NO | |
| updated_at | TIMESTAMP WITH TIME ZONE | NO | |

### Ограничения

UNIQUE(
subscription_id,
power_outage_id
)

### Notification Status

Поле `status` хранит
текущее состояние Notification.

Допустимые значения:

- PENDING;
- PROCESSING;
- SENT;
- FAILED.

### Семантика Status

#### PENDING

Notification создан
и ожидает обработки
Notification Engine.

#### PROCESSING

Notification Engine
принял Notification
в обработку.

PROCESSING не содержит
информацию о конкретном
канале, Adapter или
delivery attempt.

#### SENT

Notification Engine
успешно завершил
обработку Notification.

SENT означает успешное
завершение операции доставки.

SENT не означает
гарантированное прочтение
или ознакомление пользователя.

#### FAILED

Обработка Notification
завершилась ошибкой.

Notification сохраняется
и может быть повторно
обработан Notification Engine.

### Индексы

- PK(id);
- UNIQUE(subscription_id, power_outage_id);
- INDEX(subscription_id);
- INDEX(power_outage_id);
- INDEX(status).

### Связи

notification

↓

subscription (N:1)

notification

↓

power_outage (N:1)

User определяется
через Subscription.

user_id в Notification
не хранится.

### ON DELETE

subscription → notification:

RESTRICT

power_outage → notification:

RESTRICT

### Retry

Notification не удаляется
при ошибке доставки.

Механизм Retry
не хранится в текущей
модели базы данных.

Retry Policy,
планирование повторных попыток
и история попыток доставки
относятся к Notification Engine.