# Domain Model

> Доменная модель BlackoutRadar.

---

# Назначение

Документ описывает
основные сущности предметной области,
их ответственность,
связи и бизнес-инварианты.

Domain Model не зависит
от Spring,
JPA,
PostgreSQL,
MinIO,
SMTP,
Telegram
и других инфраструктурных технологий.

---

# Identity

## User

### Назначение

Представляет
зарегистрированного пользователя системы.

User является владельцем
подписок и связанных
с ними уведомлений.

### Ответственность

User отвечает за:

- учетную запись;
- персональные данные;
- настройки профиля;
- роль;
- состояние учетной записи;
- список принадлежащих подписок.

### Не отвечает

User не отвечает за:

- поиск отключений;
- обработку событий;
- сопоставление;
- доставку уведомлений;
- работу Parser.

### Основные связи

User

↓

Subscription

↓

Notification

User

↓

RefreshToken

### Основные инварианты

У пользователя:

- уникальный email;
- одна учетная запись;
- может быть несколько Subscription;
- может быть несколько RefreshToken;
- может быть один аватар.

---

## RefreshToken

### Назначение

Представляет возможность
обновления JWT
без повторной аутентификации.

### Ответственность

RefreshToken отвечает
за жизненный цикл
обновления аутентификации.

### Основные инварианты

Каждый RefreshToken:

- принадлежит одному User;
- имеет срок действия;
- может быть отозван;
- не используется
  после истечения срока;
- хранится в persistence
  в виде защищенного
  представления токена,
  а не исходного значения.

---

# Address Catalog

Address Catalog отвечает
за нормализацию,
хранение и идентификацию
канонических адресов.

Все операции
сопоставления выполняются
только с каноническими
адресными объектами.

---

## Region

### Назначение

Представляет
административный регион.

### Ответственность

Region отвечает за:

- идентификацию региона;
- принадлежность
  RegionalDistrict;
- принадлежность City.

### Основные связи

Region

↓

RegionalDistrict (0..N)

Region

↓

City (0..N)

### Инварианты

Каждый Region:

- имеет идентификатор;
- имеет название.

---

## RegionalDistrict

### Назначение

Представляет
административно-муниципальную
единицу внутри Region.

### Поддерживаемые типы

- MUNICIPAL_DISTRICT
- MUNICIPAL_OKRUG
- URBAN_OKRUG
- INTRACITY_TERRITORY
- FEDERAL_TERRITORY

### Ответственность

RegionalDistrict отвечает за:

- идентификацию административной
  единицы;
- принадлежность Region;
- принадлежность City.

### Основные связи

RegionalDistrict

↓

Region

RegionalDistrict

↓

City (0..N)

### Инварианты

Каждый RegionalDistrict:

- принадлежит одному Region;
- имеет тип;
- имеет название.

---

## City

### Назначение

Представляет
населенный пункт.

### Ответственность

City отвечает за:

- идентификацию населенного пункта;
- принадлежность Region;
- опциональную принадлежность
  RegionalDistrict;
- принадлежность Street;
- принадлежность CityDistrict.

### Основные связи

City

↓

Region

City

↓

RegionalDistrict (0..1)

City

↓

CityDistrict (0..N)

City

↓

Street (0..N)

### Инварианты

Каждый City:

- принадлежит одному Region;
- может непосредственно
  принадлежать Region;
- может принадлежать
  RegionalDistrict;
- если RegionalDistrict
  указан, он принадлежит
  тому же Region.

---

## CityDistrict

### Назначение

Представляет
внутригородской район
или иной локальный
районный контекст.

### Ответственность

CityDistrict отвечает за:

- идентификацию района;
- принадлежность City;
- уточнение адреса.

### Основные связи

CityDistrict

↓

City

CityDistrict

↑

Address (0..N)

### Инварианты

Каждый CityDistrict:

- принадлежит одному City;
- имеет уникальное имя
  внутри City;
- является необязательным
  для City;
- не является родителем Street.

---

## Street

### Назначение

Представляет
улицу внутри City.

Street является частью
канонического адресного каталога.

### Ответственность

Street отвечает за:

- идентификацию улицы;
- хранение StreetType;
- хранение канонического
  названия;
- использование
  в каноническом Address.

### Не отвечает

Street не отвечает за:

- хранение домов;
- принадлежность
  CityDistrict;
- поиск совпадений;
- отправку уведомлений.

### Основные связи

Street

↓

City

Street

↓

Address (1:N)

### Инварианты

Каждая Street:

- принадлежит одному City;
- имеет StreetType;
- имеет CanonicalName;
- уникальна внутри City
  по StreetType + CanonicalName.

Одна Street может иметь
Address, относящиеся
к разным CityDistrict.

---

## StreetType

### Назначение

Представляет тип
элемента улично-дорожной сети.

### Основные значения

- STREET
- PROSPECT
- BOULEVARD
- LANE
- PASSAGE
- SQUARE
- EMBANKMENT
- HIGHWAY
- ROAD
- TRACT
- ALLEY
- DEAD_END
- DESCENT
- MAGISTRAL
- UNKNOWN

### Инварианты

StreetType является
частью идентичности Street.

UNKNOWN используется,
если тип невозможно
определить при нормализации.

UNKNOWN не является
универсальным совпадением
для других типов.

---

## House

### Назначение

House представляет
структурированную
идентификацию дома.

### Состав

House содержит:

- houseNumber;
- houseAddition;
- canonicalHouse.

### Инварианты

houseNumber является
обязательной частью.

houseAddition является
необязательным.

canonicalHouse является
нормализованным значением
для идентификации и поиска.

---

## Address

### Назначение

Представляет
канонический адрес.

Address является
основной единицей
сопоставления.

### Ответственность

Address отвечает за:

- связь Street
  с конкретным домом;
- хранение House;
- опциональный CityDistrict;
- связь с Subscription;
- связь с PowerOutage.

### Основные связи

Address

↓

Street

Address

↓

CityDistrict (0..1)

Address

↓

Subscription (0..N)

Address

↓

PowerOutageAddress (0..N)

### Инварианты

Каждый Address:

- принадлежит одной Street;
- содержит один House;
- может содержать один
  CityDistrict;
- CityDistrict принадлежит
  тому же City, что и Street;
- существует в единственном
  каноническом экземпляре.

---

# TransformerStation

## Назначение

Представляет
трансформаторную подстанцию.

Используется как дополнительный
критерий Matching Engine.

### Ответственность

TransformerStation отвечает за:

- идентификацию подстанции;
- хранение ее названия;
- участие в Matching.

### Основные связи

TransformerStation

↓

Subscription (M:N)

TransformerStation

↓

PowerOutageAddress (0..N)

### Инварианты

TransformerStation:

- может использоваться
  несколькими Subscription;
- может использоваться
  несколькими PowerOutageAddress;
- может отсутствовать
  в данных источника.

---

# Subscription

## Назначение

Представляет подписку пользователя
на мониторинг отключений
конкретного Address.

### Ответственность

Subscription отвечает за:

- User;
- Address;
- период мониторинга;
- активность;
- срок Service Access;
- выбранные TransformerStation.

### Основные поля

Subscription содержит:

- monitoringStart;
- monitoringEnd;
- isActive;
- serviceAccessUntil.

### Основные связи

Subscription

↓

User

Subscription

↓

Address

Subscription

↓

TransformerStation (0..N)

Subscription

↓

Notification (0..N)

### Инварианты

Каждая Subscription:

- принадлежит одному User;
- относится к одному Address;
- имеет monitoringStart;
- имеет monitoringEnd;
- `monitoringStart < monitoringEnd`;
- имеет состояние активности;
- имеет serviceAccessUntil;
- может содержать несколько
  TransformerStation;
- не содержит одну и ту же
  TransformerStation более одного раза.

Истечение Service Access
не изменяет автоматически
isActive.

---

# Source

## Назначение

Представляет внешний источник,
из которого система получает
информацию об отключениях.

### Ответственность

Source отвечает за:

- идентификацию источника;
- тип источника;
- Provider;
- параметры конфигурации;
- расписание;
- состояние активности.

### Не отвечает

Source не отвечает за:

- загрузку данных;
- парсинг;
- дедупликацию;
- Matching;
- Notification.

---

# PowerOutage

## Назначение

Представляет одно
событие отключения
электроэнергии.

### Ответственность

PowerOutage отвечает за:

- временной интервал;
- причину;
- источник;
- жизненный цикл;
- набор затронутых адресов.

### Основные связи

PowerOutage

↓

Source

PowerOutage

↓

PowerOutageAddress (1..N)

PowerOutage

↓

Notification (0..N)

### Инварианты

Каждый PowerOutage:

- имеет один Source;
- имеет начало и конец;
- имеет причину;
- имеет состояние;
- может содержать
  множество Address;
- не удаляется после завершения.

PowerOutage создается
только после DuplicateResolver.

---

# PowerOutageAddress

## Назначение

Представляет связь
между PowerOutage
и Address.

### Ответственность

PowerOutageAddress отвечает за:

- связь события с Address;
- хранение TransformerStation,
  если она известна
  для данного Address.

### Основные связи

PowerOutageAddress

↓

PowerOutage

PowerOutageAddress

↓

Address

PowerOutageAddress

↓

TransformerStation (0..1)

### Инварианты

Каждая запись:

- относится к одному PowerOutage;
- содержит один Address;
- может содержать
  одну TransformerStation;
- не существует самостоятельно;
- пара PowerOutage + Address
  уникальна.

---

# Candidate

## Назначение

Представляет потенциальную
Subscription, которая может
соответствовать PowerOutage.

Candidate является
временным результатом
CandidateFinder.

Candidate не является
сущностью базы данных.

---

# Match

## Назначение

Представляет положительный
результат сопоставления
PowerOutage и Subscription.

Match является
временным результатом
Matching Engine.

Match не сохраняется
в базе данных.

### Ответственность

Match отвечает за:

- представление подтвержденного
  соответствия Subscription
  и PowerOutage;
- передачу результата
  в последующий
  Application / Processing Flow.

### Не отвечает

Match не отвечает за:

- создание Notification;
- отправку уведомлений;
- хранение истории;
- поиск совпадений.

---

# Notification

## Назначение

Представляет факт необходимости
уведомить пользователя
о найденном совпадении.

Notification является
самостоятельным доменным объектом.

Notification не имеет
технической зависимости
от Match.

Правило:

> Notification создается только
> после успешного Match.

является правилом
Application / Processing Flow.

Таким образом:

Match

↓

Application / Processing Flow

↓

Notification

Match не является
полем, зависимостью
или частью состояния
Notification.

### Ответственность

Notification отвечает за:

- Subscription;
- PowerOutage;
- текст уведомления;
- состояние уведомления;
- предотвращение
  повторного уведомления;
- сохранение истории.

### Не отвечает

Notification не отвечает за:

- поиск отключений;
- Matching;
- получение внешних данных;
- создание Match;
- конкретный канал доставки;
- Retry-механику;
- историю попыток доставки;
- планирование повторной обработки.

### Основные связи

Notification

↓

Subscription

Notification

↓

PowerOutage

### Инварианты

Каждый Notification:

- относится к одной Subscription;
- относится к одному PowerOutage;
- через Subscription
  относится к одному User;
- имеет состояние;
- сохраняется в истории.

Для пары:

Subscription + PowerOutage

создается не более одного
Notification.

### Notification Status

Notification имеет одно
из следующих состояний:

- PENDING;
- PROCESSING;
- SENT;
- FAILED.

### Значение состояний

#### PENDING

Notification создан
и ожидает обработки
Notification Engine.

Это исходное состояние
нового Notification.

#### PROCESSING

Notification Engine
принял Notification
в обработку.

PROCESSING означает
состояние обработки
Notification Engine,
а не состояние
конкретного канала
или Delivery Adapter.

PROCESSING не содержит
информации о:

- канале доставки;
- Adapter;
- попытке доставки;
- номере попытки;
- количестве повторов;
- времени следующей попытки;
- технической ошибке доставки.

#### SENT

Notification Engine
завершил обработку
Notification успешно.

SENT означает,
что операция доставки
завершилась успешно.

SENT не означает
гарантированное прочтение
или фактическое ознакомление
пользователя с сообщением.

#### FAILED

Обработка Notification
завершилась ошибкой.

Notification при этом
не удаляется
и сохраняется в истории.

FAILED не определяет
окончательную судьбу
Notification.

Notification Engine
может принять решение
о повторной обработке
в соответствии
с собственной Retry Policy.

### Lifecycle

Основной lifecycle:

PENDING

↓

PROCESSING

↓

SENT

или

PROCESSING

↓

FAILED

Notification Engine
может повторно принять
FAILED Notification
в обработку.

Такое повторное принятие
не создает новый Notification
и не изменяет инвариант
уникальности:

Subscription + PowerOutage.

Повторная обработка
и переход FAILED → PROCESSING
как механизм Retry
являются ответственностью
Notification Engine,
а не отдельным бизнес-правилом
Notification Domain.

### Ошибка доставки

Ошибка доставки
не удаляет Notification.

Notification остается
доступным Notification Engine
для возможной повторной обработки.

Retry Policy,
планирование повторных попыток
и история попыток доставки
не являются частью
текущей модели Notification.

---

# Общие правила взаимодействия сущностей

## 1. Единственная ответственность

Каждая сущность
имеет одну основную
предметную ответственность.

---

## 2. Независимость от инфраструктуры

Domain Model не зависит
от:

- Spring;
- JPA;
- Hibernate;
- PostgreSQL;
- MinIO;
- SMTP;
- Telegram.

---

## 3. Канонические данные

Поиск и сопоставление
выполняются через
канонические объекты.

---

## 4. История

Исторически значимые
объекты не удаляются
без необходимости.

---

## 5. Работа со временем

Во всем проекте
для работы со временем
используется пакет java.time.

Для точных моментов времени
используется Instant.

Доменные модели
не используют:

- java.util.Date;
- java.sql.Timestamp.

---

# Связанные документы

- [00-VISION](00-VISION.md)
- [00.5-GLOSSARY](00.5-GLOSSARY.md)
- [ADR-001 — Domain First Architecture](adr/ADR-001-Domain-First-Architecture.md)
- [ADR-002 — Canonical Address Model](adr/ADR-002-Canonical-Address-Model.md)
- [ADR-003 — Outage Processing Pipeline](adr/ADR-003-Outage-Processing-Pipeline.md)
- [ADR-004 — OutageProvider Architecture](adr/ADR-004-OutageProvider-Architecture.md)
- [ADR-005 — PowerOutage Event Model](adr/ADR-005-PowerOutage-Event-Model.md)
- [ADR-006 — Matching Engine](adr/ADR-006-Matching-Engine.md)
- [ADR-007 — Replaceable Infrastructure](adr/ADR-007-Replaceable-Infrastructure.md)
- [01-ARCHITECTURE](01-ARCHITECTURE.md)
- [03-DATABASE](03-DATABASE.md)

---

| ⬅ Предыдущий | 🏠 README | ➡ Следующий |
|-------------|-----------|-------------|
| [00.5-GLOSSARY](00.5-GLOSSARY.md) | [README](README.md) | [03-DATABASE](03-DATABASE.md) |