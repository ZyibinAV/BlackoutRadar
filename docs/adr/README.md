# Architecture Decision Records (ADR)

Архитектурные решения проекта BlackoutRadar.

---

## Что такое ADR?

Architecture Decision Record (ADR) — документ, фиксирующий важное архитектурное решение, его причины и последствия.

Каждый ADR отвечает на три вопроса:

- Почему возникла проблема?
- Какое решение принято?
- Какие последствия имеет это решение?

---

## Статусы

| Статус | Значение |
|---------|----------|
| Accepted | Решение принято и используется |
| Superseded | Решение заменено новым ADR |
| Deprecated | Решение больше не рекомендуется |

---

## Список решений

| ADR                                                                             | Решение                     |
|---------------------------------------------------------------------------------|-----------------------------|
| [ADR-001 — Domain First Architecture](ADR-001-Domain-First-Architecture.md)     | Domain First Architecture   |
| [ADR-002 — Canonical Address Model](ADR-002-Canonical-Address-Model.md)         | Canonical Address Model     |
| [ADR-003 — Outage Processing Pipeline](ADR-003-Outage-Processing-Pipeline.md)   | Outage Processing Pipeline  |
| [ADR-004 — OutageProvider Architecture](ADR-004-OutageProvider-Architecture.md) | OutageProvider Architecture |
| [ADR-005 — PowerOutage Event Model](ADR-005-PowerOutage-Event-Model.md)         | PowerOutage Event Model     |
| [ADR-006 — Matching Engine](ADR-006-Matching-Engine.md)                         | Matching Engine             |
| [ADR-007 — Replaceable Infrastructure](ADR-007-Replaceable-Infrastructure.md)   | Replaceable Infrastructure  |

---

## Связанные документы

- [Архитектура](../01-ARCHITECTURE.md)
- [Предметная область](../02-DOMAIN_MODEL.md)
- [База данных](../03-DATABASE.md)

---

## Диаграммы

- [System Context](../diagrams/overview/01-system-context.puml)
- [Container](../diagrams/overview/02-container.puml)