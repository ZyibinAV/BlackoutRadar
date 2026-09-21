# ADR-009 вЂ” RefreshToken Security Boundary

**Status:** Accepted

**Date:** 2026-08-18

---

# Context

Р’ РїРµСЂРІРѕРЅР°С‡Р°Р»СЊРЅРѕР№ Domain Model
RefreshToken Р±С‹Р» РѕРїСЂРµРґРµР»РµРЅ
РєР°Рє Domain Entity
РІ СЂР°РјРєР°С… TASK 6 вЂ” Identity and Subscription Domain.

RefreshToken СЃРѕРґРµСЂР¶Р°Р»:

- UUID identity;
- User;
- expiresAt;
- revokedAt.

РўР°РєР¶Рµ Р±С‹Р»Рё РѕРїСЂРµРґРµР»РµРЅС‹
РѕРїРµСЂР°С†РёРё lifecycle:

- expiration;
- revocation;
- usability.

РџСЂРё РїРѕРґРіРѕС‚РѕРІРєРµ TASK 10 вЂ”
Identity and Subscription Persistence вЂ”
Р±С‹Р»Рѕ РїСЂРѕРІРµРґРµРЅРѕ РїРѕРІС‚РѕСЂРЅРѕРµ Р°СЂС…РёС‚РµРєС‚СѓСЂРЅРѕРµ
РёСЃСЃР»РµРґРѕРІР°РЅРёРµ РѕС‚РІРµС‚СЃС‚РІРµРЅРЅРѕСЃС‚Рё RefreshToken.

---

# Problem

РќРµРѕР±С…РѕРґРёРјРѕ РѕРїСЂРµРґРµР»РёС‚СЊ,
СЏРІР»СЏРµС‚СЃСЏ Р»Рё RefreshToken
С‡Р°СЃС‚СЊСЋ Business Domain Model
РёР»Рё Security/Application concern.

РћСЃРЅРѕРІРЅРѕР№ РєСЂРёС‚РµСЂРёР№:

> РРјРµРµС‚ Р»Рё RefreshToken СЃР°РјРѕСЃС‚РѕСЏС‚РµР»СЊРЅРѕРµ
> Р±РёР·РЅРµСЃРѕРІРѕРµ Р·РЅР°С‡РµРЅРёРµ РІ РїСЂРµРґРјРµС‚РЅРѕР№ РѕР±Р»Р°СЃС‚Рё
> BlackoutRadar РЅРµР·Р°РІРёСЃРёРјРѕ РѕС‚ authentication?

---

# Analysis

RefreshToken РёСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ
РґР»СЏ РїСЂРѕРґРѕР»Р¶РµРЅРёСЏ authentication session
Рё РїРѕР»СѓС‡РµРЅРёСЏ РЅРѕРІРѕРіРѕ Access Token.

Р•РіРѕ lifecycle РІРєР»СЋС‡Р°РµС‚:

- issuance;
- expiration;
- revocation;
- rotation;
- validation.

Р­С‚Рё РѕРїРµСЂР°С†РёРё РѕС‚РЅРѕСЃСЏС‚СЃСЏ
Рє authentication/security.

RefreshToken РЅРµ СѓС‡Р°СЃС‚РІСѓРµС‚
РЅРµРїРѕСЃСЂРµРґСЃС‚РІРµРЅРЅРѕ РІ Р±РёР·РЅРµСЃ-РїСЂРѕС†РµСЃСЃР°С…:

- Address;
- Subscription;
- TransformerStation;
- PowerOutage;
- Matching;
- Notification;
- Outage Processing.

РќРµС‚ Р±РёР·РЅРµСЃРѕРІРѕРіРѕ РїСЂР°РІРёР»Р°,
РІ РєРѕС‚РѕСЂРѕРј RefreshToken СЏРІР»СЏРµС‚СЃСЏ
С‡Р°СЃС‚СЊСЋ РїСЂРµРґРјРµС‚РЅРѕР№ Р»РѕРіРёРєРё BlackoutRadar.

---

# Decision

`RefreshToken` **РЅРµ СЏРІР»СЏРµС‚СЃСЏ
С‡Р°СЃС‚СЊСЋ Business Domain Model**.

RefreshToken РѕС‚РЅРѕСЃРёС‚СЃСЏ
Рє Security/Application boundary.

RefreshToken РґРѕР»Р¶РµРЅ Р±С‹С‚СЊ СЂРµР°Р»РёР·РѕРІР°РЅ
РІ Security Phase.

Р’ С‡Р°СЃС‚РЅРѕСЃС‚Рё:

- RefreshToken РЅРµ СЏРІР»СЏРµС‚СЃСЏ Domain Entity;
- RefreshToken РЅРµ РЅР°С…РѕРґРёС‚СЃСЏ
  РІ domain.identity;
- RefreshToken lifecycle СЏРІР»СЏРµС‚СЃСЏ
  Security responsibility;
- token storage СЏРІР»СЏРµС‚СЃСЏ
  Security/Persistence responsibility;
- token hashing СЏРІР»СЏРµС‚СЃСЏ
  Security responsibility;
- token rotation СЏРІР»СЏРµС‚СЃСЏ
  Security responsibility.

---

# Domain Boundary

Business Domain Model
РЅРµ СЃРѕРґРµСЂР¶РёС‚:

- RefreshToken Entity;
- raw refresh token;
- tokenHash;
- token expiration state;
- token revocation state;
- token rotation state.

Domain Model С‚Р°РєР¶Рµ РЅРµ СЃРѕРґРµСЂР¶РёС‚
RefreshToken-specific Port.

---

# Security Boundary

Security РІР»Р°РґРµРµС‚:

- Refresh Token lifecycle;
- Access Token lifecycle;
- JWT;
- authentication credentials;
- token validation;
- token revocation;
- token rotation.

РљРѕРЅРєСЂРµС‚РЅР°СЏ СЂРµР°Р»РёР·Р°С†РёСЏ РІС‹РїРѕР»РЅСЏРµС‚СЃСЏ
РІ Security Phase.

---

# Persistence Boundary

Р¤РёР·РёС‡РµСЃРєРѕРµ С…СЂР°РЅРµРЅРёРµ Refresh Token
РѕСЃС‚Р°РµС‚СЃСЏ С‡Р°СЃС‚СЊСЋ Persistence Model.

РЎСѓС‰РµСЃС‚РІСѓСЋС‰Р°СЏ С‚Р°Р±Р»РёС†Р°:

refresh_token

СЃРѕРґРµСЂР¶РёС‚:

- id;
- user_id;
- token_hash;
- expires_at;
- revoked_at;
- created_at;
- updated_at.

Database Model РЅРµ РёР·РјРµРЅСЏРµС‚СЃСЏ
РґР°РЅРЅС‹Рј ADR.

Liquibase changesets
РЅРµ РёР·РјРµРЅСЏСЋС‚СЃСЏ РґР°РЅРЅС‹Рј ADR.

Refresh Token Persistence
Р±СѓРґРµС‚ РёСЃРїРѕР»СЊР·РѕРІР°РЅР° Р±СѓРґСѓС‰РµР№
Security implementation.

---

# Consequences

## Positive

### 1. Р§РёСЃС‚С‹Р№ Business Domain

Domain Model СЃРѕРґРµСЂР¶РёС‚
С‚РѕР»СЊРєРѕ РїСЂРµРґРјРµС‚РЅС‹Рµ СЃСѓС‰РЅРѕСЃС‚Рё
BlackoutRadar.

Security-specific concepts
РЅРµ Р·Р°РіСЂСЏР·РЅСЏСЋС‚ Domain.

### 2. Р§РµС‚РєРѕРµ СЂР°Р·РґРµР»РµРЅРёРµ РѕС‚РІРµС‚СЃС‚РІРµРЅРЅРѕСЃС‚Рё

Business Domain:

- Subscription;
- Address;
- PowerOutage;
- Notification;
- User;
- TransformerStation.

Security:

- Authentication;
- Authorization;
- JWT;
- Access Token;
- Refresh Token.

### 3. РЈРїСЂРѕС‰РµРЅРёРµ Persistence

РќРµ С‚СЂРµР±СѓРµС‚СЃСЏ mapping:

Domain RefreshToken
в†”
Persistence RefreshTokenEntity.

РќРµ С‚СЂРµР±СѓРµС‚СЃСЏ СЃРїРµС†РёР°Р»СЊРЅС‹Р№
RefreshTokenCredential
РЅР° Domain Port boundary.

### 4. Replaceable Security Infrastructure

РљРѕРЅРєСЂРµС‚РЅС‹Р№ РјРµС…Р°РЅРёР·Рј authentication
РјРѕР¶РµС‚ РёР·РјРµРЅСЏС‚СЊСЃСЏ
Р±РµР· РёР·РјРµРЅРµРЅРёСЏ Business Domain Model.

### 5. РћС‚СЃСѓС‚СЃС‚РІРёРµ persistence leakage

`token_hash` РѕСЃС‚Р°РµС‚СЃСЏ
С‚РµС…РЅРёС‡РµСЃРєРёРј Security/Persistence representation.

---

# Negative Consequences

### 1. RefreshToken Р±РѕР»СЊС€Рµ РЅРµ СЏРІР»СЏРµС‚СЃСЏ Domain Entity

Security lifecycle РЅРµР»СЊР·СЏ СЂРµР°Р»РёР·РѕРІС‹РІР°С‚СЊ
С‡РµСЂРµР· Domain Entity.

Lifecycle СЂРµР°Р»РёР·СѓРµС‚СЃСЏ
РІ Security/Application layer.

### 2. Security РїРѕР»СѓС‡Р°РµС‚ СЃРѕР±СЃС‚РІРµРЅРЅСѓСЋ РјРѕРґРµР»СЊ

Security implementation РґРѕР»Р¶РЅР° РёРјРµС‚СЊ
СЃРѕР±СЃС‚РІРµРЅРЅС‹Рµ РјРѕРґРµР»Рё Рё contracts
РґР»СЏ Refresh Token.

Р­С‚Рѕ СѓРІРµР»РёС‡РёРІР°РµС‚ Р»РѕРєР°Р»СЊРЅСѓСЋ СЃР»РѕР¶РЅРѕСЃС‚СЊ
Security subsystem.

### 3. TASK 6 С‚СЂРµР±СѓРµС‚ Р°СЂС…РёС‚РµРєС‚СѓСЂРЅРѕР№ РєРѕСЂСЂРµРєС†РёРё

РџРµСЂРІРѕРЅР°С‡Р°Р»СЊРЅРѕРµ СЂРµС€РµРЅРёРµ TASK 6
СЃ RefreshToken Domain Entity
СЃС‚Р°РЅРѕРІРёС‚СЃСЏ СѓСЃС‚Р°СЂРµРІС€РёРј.

РСЃС‚РѕСЂРёСЏ TASK 6 СЃРѕС…СЂР°РЅСЏРµС‚СЃСЏ
РІ TASK_LOG.

---

# Impact on TASK 10

TASK 10 вЂ” Identity and Subscription Persistence
РЅРµ РІРєР»СЋС‡Р°РµС‚ RefreshToken.

Scope TASK 10:

- User;
- TransformerStation;
- Subscription.

RefreshToken Persistence
РїРµСЂРµРЅРѕСЃРёС‚СЃСЏ РІ Security Phase.

---

# Impact on TASK 30

TASK 30 вЂ” JWT and Refresh Token Security
СЃС‚Р°РЅРѕРІРёС‚СЃСЏ РІР»Р°РґРµР»СЊС†РµРј РїРѕР»РЅРѕР№ СЂРµР°Р»РёР·Р°С†РёРё:

- Access Token;
- JWT;
- Refresh Token;
- secure storage;
- validation;
- expiration;
- revocation;
- rotation;
- authentication integration.

---

# Impact on Database

Database Schema РЅРµ РёР·РјРµРЅСЏРµС‚СЃСЏ.

РўР°Р±Р»РёС†Р° refresh_token
РѕСЃС‚Р°РµС‚СЃСЏ РЅРµРѕР±С…РѕРґРёРјРѕР№.

РЎСѓС‰РµСЃС‚РІСѓСЋС‰РёРµ:

- PK;
- FK;
- UNIQUE;
- indexes;
- timestamps

РѕСЃС‚Р°СЋС‚СЃСЏ Р±РµР· РёР·РјРµРЅРµРЅРёР№.

---

# Impact on Liquibase

Liquibase РЅРµ РёР·РјРµРЅСЏРµС‚СЃСЏ.

РќРѕРІС‹Р№ changeset
РґР»СЏ СѓРґР°Р»РµРЅРёСЏ Refresh Token
РЅРµ С‚СЂРµР±СѓРµС‚СЃСЏ.

РќРѕРІС‹Р№ changeset
РґР»СЏ СЃРѕР·РґР°РЅРёСЏ Refresh Token
РЅРµ С‚СЂРµР±СѓРµС‚СЃСЏ.

РўР°Р±Р»РёС†Р° СѓР¶Рµ СЃСѓС‰РµСЃС‚РІСѓРµС‚
Рё Р±СѓРґРµС‚ РёСЃРїРѕР»СЊР·РѕРІР°РЅР° Security Phase.

---

# Impact on Documentation

РќРµРѕР±С…РѕРґРёРјРѕ СЃРёРЅС…СЂРѕРЅРёР·РёСЂРѕРІР°С‚СЊ:

- 02-DOMAIN_MODEL.md;
- 07-SECURITY.md;
- TASK_PLAN.md;
- TASK_LOG.md;
- 00.5-GLOSSARY.md;
- Domain Model diagram.

РќРµ С‚СЂРµР±СѓРµС‚СЃСЏ РёР·РјРµРЅРµРЅРёРµ:

- Database ER diagram;
- Security Flow diagram;
- Package Responsibility diagram.

---

# Alternatives Considered

## Alternative 1 вЂ” РѕСЃС‚Р°РІРёС‚СЊ RefreshToken РІ Domain

РћС‚РєР»РѕРЅРµРЅРѕ.

РџСЂРёС‡РёРЅР°:

RefreshToken РЅРµ РёРјРµРµС‚
СЃР°РјРѕСЃС‚РѕСЏС‚РµР»СЊРЅРѕРіРѕ Business Domain meaning.

Р•РіРѕ lifecycle СЏРІР»СЏРµС‚СЃСЏ
Security lifecycle.

---

## Alternative 2 вЂ” РґРѕР±Р°РІРёС‚СЊ tokenHash РІ Domain

РћС‚РєР»РѕРЅРµРЅРѕ.

Р­С‚Рѕ РµС‰Рµ СЃРёР»СЊРЅРµРµ СЃРјРµС€РёРІР°РµС‚
Business Domain Model
СЃ Security/Persistence representation.

---

## Alternative 3 вЂ” РѕСЃС‚Р°РІРёС‚СЊ RefreshToken
РІ Domain, РЅРѕ СЃРєСЂС‹С‚СЊ tokenHash

РћС‚РєР»РѕРЅРµРЅРѕ.

Р­С‚Рѕ СЂРµС€Р°РµС‚ С‚РѕР»СЊРєРѕ
Persistence mapping problem,
РЅРѕ РЅРµ СЂРµС€Р°РµС‚ РїСЂРѕР±Р»РµРјСѓ
РЅРµРїСЂР°РІРёР»СЊРЅРѕР№ Р°СЂС…РёС‚РµРєС‚СѓСЂРЅРѕР№ РїСЂРёРЅР°РґР»РµР¶РЅРѕСЃС‚Рё
RefreshToken.

---

## Alternative 4 вЂ” СЃРѕР·РґР°С‚СЊ RefreshTokenCredential
РІ Domain

РћС‚РєР»РѕРЅРµРЅРѕ.

РџРѕСЃР»Рµ СѓРґР°Р»РµРЅРёСЏ RefreshToken
РёР· Domain РЅРµРѕР±С…РѕРґРёРјРѕСЃС‚СЊ
РІ С‚Р°РєРѕР№ abstraction РёСЃС‡РµР·Р°РµС‚.

Security layer РґРѕР»Р¶РµРЅ СЃР°РјРѕСЃС‚РѕСЏС‚РµР»СЊРЅРѕ
РѕРїСЂРµРґРµР»СЏС‚СЊ СЃРѕР±СЃС‚РІРµРЅРЅС‹Р№ credential contract.

---

# Architectural Rules Resulting from ADR

1. RefreshToken РЅРµ СЏРІР»СЏРµС‚СЃСЏ Domain Entity.
2. RefreshToken РЅРµ СЂР°Р·РјРµС‰Р°РµС‚СЃСЏ РІ domain.identity.
3. RefreshToken lifecycle СЏРІР»СЏРµС‚СЃСЏ Security concern.
4. tokenHash РЅРµ СЏРІР»СЏРµС‚СЃСЏ Domain state.
5. raw refresh token РЅРµ СЏРІР»СЏРµС‚СЃСЏ Domain state.
6. Security infrastructure РЅРµ РїСЂРѕРЅРёРєР°РµС‚ РІ Business Domain.
7. Database representation РјРѕР¶РµС‚ СЃСѓС‰РµСЃС‚РІРѕРІР°С‚СЊ
   РЅРµР·Р°РІРёСЃРёРјРѕ РѕС‚ Domain Entity.
8. TASK 10 РЅРµ СЂРµР°Р»РёР·СѓРµС‚ RefreshToken.
9. TASK 30 СЂРµР°Р»РёР·СѓРµС‚ RefreshToken Security.
10. РР·РјРµРЅРµРЅРёРµ СЌС‚РѕРіРѕ СЂРµС€РµРЅРёСЏ С‚СЂРµР±СѓРµС‚ РЅРѕРІРѕРіРѕ ADR.

---

# Related Documents

- [02-DOMAIN_MODEL](../02-DOMAIN_MODEL.md)
- [03-DATABASE](../03-DATABASE.md)
- [07-SECURITY](../07-SECURITY.md)
- [TASK_PLAN](../ai/TASK_PLAN.md)
- [TASK_LOG](../ai/TASK_LOG.md)
- [ADR-001 вЂ” Domain First Architecture](ADR-001-Domain-First-Architecture.md)
- [ADR-007 вЂ” Replaceable Infrastructure](ADR-007-Replaceable-Infrastructure.md)

---

# Related Diagrams

- [Domain Model](../diagrams/detailed/05-domain-model.puml)
- [Security Flow](../diagrams/detailed/10-security-flow.puml)
- [Package Responsibility](../diagrams/overview/03-package-responsibility.puml)

---

# Relation to ADR-016

**Note вЂ” 2026-09-20, Р±РµР· РёР·РјРµРЅРµРЅРёСЏ СЂРµС€РµРЅРёСЏ Рё СЃС‚Р°С‚СѓСЃР° РґР°РЅРЅРѕРіРѕ ADR.**

Р¤РѕСЂРјСѓР»РёСЂРѕРІРєРё СЂР°Р·РґРµР»РѕРІ
# Persistence Boundary,
# Impact on Database Рё
# Impact on Liquibase
Рѕ С‚РѕРј, С‡С‚Рѕ Database Schema Рё Liquibase changesets
РЅРµ РёР·РјРµРЅСЏСЋС‚СЃСЏ РґР°РЅРЅС‹Рј ADR,
РѕРїРёСЃС‹РІР°СЋС‚ СЃРѕСЃС‚РѕСЏРЅРёРµ РЅР° РјРѕРјРµРЅС‚ РїСЂРёРЅСЏС‚РёСЏ
РґР°РЅРЅРѕРіРѕ СЂРµС€РµРЅРёСЏ (2026-08-18).

Р‘РѕР»РµРµ РїРѕР·РґРЅРёР№ ADR-016
СѓС‚РѕС‡РЅСЏРµС‚ Рё СЂР°Р·РІРёРІР°РµС‚ persistence Рё lifecycle
СЂРµС€РµРЅРёСЏ РґР°РЅРЅРѕРіРѕ ADR:

- РґРѕР±Р°РІР»СЏРµС‚ `family_id` РІ СЃСѓС‰РµСЃС‚РІСѓСЋС‰СѓСЋ С‚Р°Р±Р»РёС†Сѓ `refresh_token`;
- РѕРїСЂРµРґРµР»СЏРµС‚ Rotation Family, rotation, replay detection
  Рё family-wide revocation;
- РІРЅРѕСЃРёС‚ РёР·РјРµРЅРµРЅРёРµ С‡РµСЂРµР· Liquibase changeset.

РђСЂС…РёС‚РµРєС‚СѓСЂРЅР°СЏ РіСЂР°РЅРёС†Р°, СѓСЃС‚Р°РЅРѕРІР»РµРЅРЅР°СЏ РґР°РЅРЅС‹Рј ADR
(RefreshToken РЅРµ СЏРІР»СЏРµС‚СЃСЏ С‡Р°СЃС‚СЊСЋ Business Domain Model),
РѕСЃС‚Р°РµС‚СЃСЏ РІ СЃРёР»Рµ Р±РµР· РёР·РјРµРЅРµРЅРёР№.

РЎРІСЏР·Р°РЅРЅС‹Р№ РґРѕРєСѓРјРµРЅС‚:

- [ADR-016 вЂ” Refresh Token Rotation Family and Replay Protection](ADR-016-Refresh-Token-Rotation-Family-and-Replay-Protection.md)

---

# Status

**Accepted**
