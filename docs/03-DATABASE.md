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

password_hash используется
для локальной аутентификации.

Поле:

- не содержит raw password;
- содержит только cryptographic hash;
- не является частью Domain User.

email имеет UNIQUE constraint.

UNIQUE(email) является
физической защитой от
конкурентной регистрации.

### Связи

user

↓

subscription (1:N)

user

↓

refresh_token (1:N)

---

## Таблица refresh_token

`refresh_token` является Security/Persistence table.

Таблица не представляет Domain Entity.

### Назначение

Хранит Refresh Token
в защищенном представлении.

Исходное значение токена
в базе данных не хранится.

Дополнительно таблица управляет:

- expiration;
- revocation;
- Rotation Family.

### Поля

| Поле | Тип | NULL | Ограничения |
|---|---|---|---|
| id | UUID | NO | PK |
| user_id | UUID | NO | FK |
| family_id | UUID | NO | |
| token_hash | VARCHAR | NO | UNIQUE |
| expires_at | TIMESTAMP WITH TIME ZONE | NO | |
| revoked_at | TIMESTAMP WITH TIME ZONE | YES | |
| created_at | TIMESTAMP WITH TIME ZONE | NO | |
| updated_at | TIMESTAMP WITH TIME ZONE | NO | |

### Индексы

- PK(id);
- UNIQUE(token_hash);
- INDEX(user_id);
- INDEX(expires_at);
- INDEX(family_id).

### ON DELETE

user → refresh_token:

CASCADE

### Security Rules

Raw Refresh Token не хранится в Database.

Database хранит только `token_hash`.

`family_id` является Security/Persistence state и не входит в Domain Model.

### Rotation

При успешной rotation:

1. существующий Refresh Token получает `revoked_at`;
2. создается новый Refresh Token;
3. новый Refresh Token получает тот же `family_id`;
4. новый `token_hash` сохраняется отдельно.

### Replay Detection

При использовании revoked Refresh Token:

1. определяется соответствующий `family_id`;
2. Security рассматривает ситуацию как reuse;
3. действующие Refresh Tokens соответствующей Family отзываются.

### Schema Change

TASK 30 добавляет `family_id` в существующую `refresh_token` table
через Liquibase changeset `013-refresh-token-family.sql`.

Изменение Database Schema выполняется только через Liquibase.

Не создается Domain Entity для `refresh_token`.

---

# NotificationChannel

## Таблица notification_channel

### Назначение

Хранит пользовательские
каналы уведомлений.

Один User может иметь
несколько NotificationChannel,
в том числе несколько каналов
одного типа.

`NotificationChannel`
представляет пользовательскую
настройку канала и не является
частью Notification.

### Поля

| Поле | Тип | NULL | Ограничения |
|---|---|---|---|
| id | UUID | NO | PK |
| user_id | UUID | NO | FK |
| type | VARCHAR | NO | |
| destination | VARCHAR | NO | |
| enabled | BOOLEAN | NO | |
| created_at | TIMESTAMP WITH TIME ZONE | NO | |
| updated_at | TIMESTAMP WITH TIME ZONE | NO | |

### Ограничения

UNIQUE(
user_id,
type,
destination
)

Один пользователь не может
иметь два одинаковых канала
с одинаковым типом и
одинаковым destination.

`type` является расширяемым
идентификатором и не ограничивается
перечислением конкретных каналов
на уровне Database Model.

### Индексы

- PK(id);
- UNIQUE(user_id, type, destination);
- INDEX(user_id).

### Связи

user

↓

notification_channel (1:N)

notification_channel

↓

user (N:1)

### ON DELETE

user → notification_channel:

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

Для каждой пары
Subscription + PowerOutage
хранится не более одного Notification.

Уникальность физически обеспечивается
ограничением:

UNIQUE(subscription_id, power_outage_id).

Application / Processing Flow
использует эту уникальность
для идемпотентного создания Notification.

### Atomic get-or-create

Канонический `Notification`
возвращается атомарной операцией:

```text
INSERT ...
ON CONFLICT (subscription_id, power_outage_id)
DO NOTHING
```

Если строка создана —
возвращается созданный `Notification`.

Если строка не создана
из-за конфликта —
возвращается уже существующий
`Notification` отдельным SELECT.

Исключение нарушения уникальности
не используется как штатная ветка.

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

## Concurrent Notification Finalization

Финализация `Notification` использует короткую блокировку строки `notification` в PostgreSQL.

Блокировка применяется только при принятии итогового решения:

```text
fenced NotificationDelivery update
        ↓
lock notification row
        ↓
read current NotificationDelivery states
        ↓
calculate Notification status
        ↓
update notification
        ↓
commit
```

Fenced completion `NotificationDelivery`
и финализация `Notification`
выполняются в одной короткой транзакции.
Внешняя доставка выполняется
вне этой транзакции.

Блокировка строки `notification` не используется во время:

* Delivery Adapter;
* внешнего сетевого вызова;
* Retry Processing;
* создания DeliveryAttempt;
* Recovery;
* других длительных операций.

Конкурентная обработка отдельных `NotificationDelivery` не сериализуется через строку `notification`.

PostgreSQL-блокировка используется только для сериализации итогового решения по `Notification`.


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

Все связанные `NotificationDelivery` успешно завершены.

Финализатор установил состояние `Notification` в `SENT`.

`SENT` означает успешное завершение всех предусмотренных доставок.

`SENT` не означает гарантированное прочтение или ознакомление пользователя.


#### FAILED

Обработка Notification
завершилась ошибкой.

Notification сохраняется
в истории.

Повторная доставка выполняется
на уровне связанных `NotificationDelivery`
через Retry Processing.

`Notification` не является
единицей Retry Processing.


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

Состояние Retry хранится
в `notification_delivery`:

- `status`;
- `next_attempt_at`;
- `processing_token`.

История фактических попыток
хранится в `delivery_attempt`.

Retry Decision принимается
на уровне `NotificationDelivery`
на основе Retry Policy.

Подробно таблицы описаны
в разделах ниже.

---

# Notification Delivery

## Таблица notification_delivery

### Назначение

Хранит текущее состояние доставки конкретного `Notification` через конкретный `NotificationChannel`.

Одна Notification может иметь несколько `NotificationDelivery`.

Каждая `NotificationDelivery` однозначно определяется связью:

```text
notification_id + notification_channel_id
```

### Поля

| Поле                    | Тип                      | NULL | Назначение                         |
| ----------------------- | ------------------------ | ---- | ---------------------------------- |
| id                      | UUID                     | NO   | PK                                 |
| notification_id         | UUID                     | NO   | FK → notification                  |
| notification_channel_id | UUID                     | NO   | FK → notification_channel          |
| status                  | VARCHAR                  | NO   | READY / PROCESSING / SENT / FAILED |
| next_attempt_at         | TIMESTAMP WITH TIME ZONE | YES  | время следующей попытки            |
| processing_token        | UUID                     | YES  | технический ownership token        |
| created_at              | TIMESTAMP WITH TIME ZONE | NO   | время создания                     |
| updated_at              | TIMESTAMP WITH TIME ZONE | NO   | время изменения                    |

### Ограничения

```text
PRIMARY KEY(id)

UNIQUE(
    notification_id,
    notification_channel_id
)
```

`processing_token` является техническим полем конкурентной обработки.

Он не является частью Domain Model.

### Индексы

```text
PK(id)

UNIQUE(notification_id, notification_channel_id)

INDEX(notification_id)

INDEX(status, next_attempt_at)
```

### Связи

```text
notification
    ↓
notification_delivery
    ↓
notification_channel
```

### ON DELETE

```text
notification → notification_delivery:

RESTRICT

notification_channel → notification_delivery:

RESTRICT
```

Связанные записи
не удаляются,
пока существует NotificationDelivery.

---

# Delivery Attempt

## Таблица delivery_attempt

### Назначение

Хранит исторические записи фактически выполненных попыток доставки.

### Поля

| Поле                     | Тип                      | NULL | Назначение                 |
| ------------------------ | ------------------------ | ---- | -------------------------- |
| id                       | UUID                     | NO   | PK                         |
| notification_delivery_id | UUID                     | NO   | FK                         |
| attempt_number           | INTEGER                  | NO   | номер попытки              |
| started_at               | TIMESTAMP WITH TIME ZONE | NO   | начало                     |
| completed_at             | TIMESTAMP WITH TIME ZONE | YES  | завершение                 |
| result                   | VARCHAR                  | YES  | результат попытки          |
| error_code               | VARCHAR                  | YES  | безопасный технический код |

### Ограничения

```text
PRIMARY KEY(id)

UNIQUE(
    notification_delivery_id,
    attempt_number
)
```

`attempt_number` нумеруется независимо для каждой `NotificationDelivery`.

### ON DELETE

```text
notification_delivery → delivery_attempt:

RESTRICT
```

Историческая `DeliveryAttempt`
сохраняется после recovery
и не удаляется вместе с доставкой.

### Индексы

Основной поиск истории выполняется по:

```text
notification_delivery_id
```

с сортировкой по:

```text
attempt_number
```

### Безопасность

`delivery_attempt` не хранит:

* destination;
* полный текст Notification;
* SMTP credentials;
* access tokens;
* refresh tokens;
* полную строку исключения.

`error_code` должен быть безопасным техническим идентификатором.

---

# Retry Processing

Для поиска готовых Retry используется:

```text
status = READY
AND (
    next_attempt_at IS NULL
    OR next_attempt_at <= current time
)
```

Для обнаружения зависшей доставки используется:

```text
status = PROCESSING
```

с проверкой незавершённой `DeliveryAttempt`.
PROCESSING
AND
EXISTS incomplete attempt started before threshold
AND
NOT EXISTS incomplete attempt started at or after threshold

Recovery не удаляет и не изменяет историческую `DeliveryAttempt`.

Изменения схемы выполняются только через Liquibase.
