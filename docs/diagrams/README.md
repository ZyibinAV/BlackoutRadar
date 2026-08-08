# Architecture Diagrams

> Архитектурные диаграммы проекта BlackoutRadar.

---

# Назначение

Документ содержит обзор всех архитектурных диаграмм проекта.

Диаграммы являются графическим дополнением к архитектурной документации и помогают понять устройство системы на различных уровнях абстракции.

Architecture Diagrams отвечает на вопрос:

> **«Какие диаграммы используются для описания архитектуры системы?»**

---

# Навигация

| Раздел | Ссылка |
|---------|--------|
| 🏠 Документация | [README](../README.md) |
| 📚 ADR | [Architecture Decision Records](../adr/README.md) |

---

# Связанные документы

- [01-ARCHITECTURE](../01-ARCHITECTURE.md)
- [02-DOMAIN_MODEL](../02-DOMAIN_MODEL.md)
- [03-DATABASE](../03-DATABASE.md)
- [04-PARSER](../04-PARSER.md)
- [05-MATCHING_ENGINE](../05-MATCHING_ENGINE.md)
- [06-NOTIFICATION_ENGINE](../06-NOTIFICATION_ENGINE.md)
- [07-SECURITY](../07-SECURITY.md)

---

# Диаграммы

## Overview

Диаграммы верхнего уровня предназначены для быстрого понимания архитектуры системы.

| Диаграмма | Назначение |
|------------|------------|
| [01 — System Context](overview/01-system-context.puml) | Внешние границы системы и взаимодействие с внешними сервисами |
| [02 — Container](overview/02-container.puml) | Основные подсистемы приложения и их взаимодействие |
| [03 — Package Responsibility](overview/03-package-responsibility.puml) | Ответственность основных пакетов проекта |
| [04 — Domain Overview](overview/04-domain-overview.puml) | Упрощенная модель предметной области |

---

## Detailed

Детальные диаграммы используются при разработке системы.

| Диаграмма | Назначение |
|------------|------------|
| [05 — Domain Model](detailed/05-domain-model.puml) | Полная модель предметной области |
| [06 — Database ER](detailed/06-database-er.puml) | Физическая модель базы данных |
| [07 — Parser Pipeline](detailed/07-parser-pipeline.puml) | Последовательность обработки данных при парсинге |
| [08 — Matching Pipeline](detailed/08-matching-pipeline.puml) | Алгоритм сопоставления отключений с подписками |
| [09 — Notification Pipeline](detailed/09-notification-pipeline.puml) | Процесс формирования и доставки уведомлений |
| [10 — Security Flow](detailed/10-security-flow.puml) | Основные сценарии аутентификации и авторизации |

---

# Рекомендуемый порядок просмотра

Для первого знакомства с системой рекомендуется следующий порядок изучения диаграмм.

1. System Context
2. Container
3. Package Responsibility
4. Domain Overview
5. Domain Model
6. Database ER
7. Parser Pipeline
8. Matching Pipeline
9. Notification Pipeline
10. Security Flow

---

# См. также

## Документы

- [README](../README.md)
- [01-ARCHITECTURE](../01-ARCHITECTURE.md)
- [02-DOMAIN_MODEL](../02-DOMAIN_MODEL.md)
- [03-DATABASE](../03-DATABASE.md)

## ADR

- [Architecture Decision Records](../adr/README.md)

---

| 🏠 Документация | 📚 ADR |
|----------------|---------|
| [README](../README.md) | [ADR Index](../adr/README.md) |