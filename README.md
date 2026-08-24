# Weezzy

[![CI](https://github.com/Nemati213/weezzy/actions/workflows/ci.yml/badge.svg)](https://github.com/Nemati213/weezzy/actions/workflows/ci.yml)

Weezzy — backend внутреннего ITMO-сервиса для нетворкинга, общения и поиска людей
в команду. Пользователь создаёт профиль, указывает навыки, интересы и цели, получает
рекомендации других участников и голосует за анкеты. Взаимный `LIKE` создаёт match
и открывает Telegram обоих участников.

Проект пока содержит только backend. Клиентские приложения находятся вне этого
репозитория.

## Что реализовано

- регистрация и вход по email/password;
- обязательное подтверждение email, повторная отправка и SMTP в production;
- восстановление пароля одноразовой ссылкой и отзыв активных сессий;
- удаление аккаунта с очисткой персональных данных и сохранением общей истории;
- короткоживущие access JWT, refresh token rotation и сессии устройств;
- logout одной сессии и отзыв всех сессий пользователя;
- роли `USER`/`ADMIN`;
- единый JSON-формат API-ошибок, включая `401`, `403` и request ID;
- Redis rate limit для login/register в production;
- onboarding с прогрессом, недостающими шагами и проверкой готовности к активации;
- профиль со статусами `DRAFT`, `ACTIVE`, `HIDDEN`;
- фотографии профиля с прямой загрузкой в S3/MinIO, порядком и avatar;
- skills, interests и goals профиля;
- каталоги и модерация пользовательских предложений новых skills/interests;
- рекомендации со scoring и сортировкой в PostgreSQL;
- фильтры, cursor pagination, cooldown показов и объяснение score;
- голоса `LIKE`/`PASS`, взаимные matches, unmatch и blocks;
- Telegram скрыт до match;
- жалобы на профили, административная модерация и временные/постоянные санкции;
- in-app уведомления с unread state и cursor pagination;
- transactional outbox с retry, backoff, stale-lock recovery и cleanup;
- университеты, активные локации и ежедневные заявки на экспресс-обед;
- конкурентный matching экспресс-обедов по точной локации и временному слоту;
- группы из 3–4 участников, аварийные пары, продления и lifecycle групп;
- временный REST-чат активной lunch-группы с идемпотентной отправкой и cleanup;
- метрики matching, lifecycle и chat cleanup экспресс-обедов;
- page pagination для профилей/каталогов и cursor pagination для динамических списков;
- interaction events для impressions, votes, matches и blocks;
- Actuator health/liveness/readiness, Micrometer metrics и Prometheus;
- ECS JSON logging и `X-Request-ID` в production;
- Dockerfile, production profile и CI security checks.

## Стек

- Java 21;
- Spring Boot 4.1;
- Spring MVC, Validation, Security, Data JPA и Data Redis;
- PostgreSQL 17, pgvector image и Flyway;
- Redis 8;
- S3-compatible object storage и MinIO для локальной разработки;
- JWT и BCrypt;
- Gradle Kotlin DSL;
- JUnit 5, MockMvc и Testcontainers;
- Docker Compose;
- GitHub Actions, Dependency Review и CodeQL.

## Быстрый локальный запуск

Требования:

- JDK 21;
- Docker Desktop с Docker Compose.

Поднять PostgreSQL, Redis, pgAdmin и MinIO:

```powershell
docker compose up -d
```

Запустить приложение:

```powershell
.\gradlew.bat bootRun
```

После запуска доступны:

- API: `http://localhost:8080`;
- Swagger UI: `http://localhost:8080/swagger-ui.html`;
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`;
- health: `http://localhost:8080/actuator/health`;
- readiness: `http://localhost:8080/actuator/health/readiness`;
- PostgreSQL: `localhost:5433`;
- Redis: `localhost:6380`;
- pgAdmin: `http://localhost:5050`;
- MinIO S3 API: `http://localhost:9000`;
- MinIO Console: `http://localhost:9001`.

Локальные реквизиты PostgreSQL:

```text
database: weezzy
username: weezzy
password: weezzy_dev_password
```

Локальные реквизиты MinIO:

```text
username: weezzy
password: weezzy_dev_password
bucket: weezzy-profile-photos
```

Bucket создаётся автоматически контейнером `minio-init` и остаётся приватным.

Локальный rate limit отключён. Redis используется rate limiter-ом при включении
`app.security.rate-limit.enabled=true` и обязателен в production.

Остановить инфраструктуру:

```powershell
docker compose down
```

Удалить контейнеры вместе с локальными данными:

```powershell
docker compose down -v
```

## Production и Docker

Собрать image:

```powershell
docker build -t weezzy .
```

Dockerfile использует multi-stage build, запускает приложение не от root и
автоматически включает профиль `production`.

Обязательные production variables:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
REDIS_URL
JWT_SECRET
FRONTEND_BASE_URL
MAIL_HOST
MAIL_USERNAME
MAIL_PASSWORD
MAIL_FROM
S3_ENDPOINT
S3_ACCESS_KEY
S3_SECRET_KEY
S3_BUCKET
```

Опциональные variables:

```text
JWT_ACCESS_TOKEN_TTL=PT15M
REFRESH_TOKEN_TTL=P30D
AUTH_SESSION_MAX_TTL=P90D
LOGIN_RATE_LIMIT_CAPACITY=10
LOGIN_RATE_LIMIT_WINDOW=1m
REGISTER_RATE_LIMIT_CAPACITY=5
REGISTER_RATE_LIMIT_WINDOW=1h
EMAIL_VERIFICATION_TOKEN_TTL=PT24H
EMAIL_RESEND_RATE_LIMIT_CAPACITY=3
EMAIL_RESEND_RATE_LIMIT_WINDOW=1h
PASSWORD_RESET_TOKEN_TTL=PT30M
PASSWORD_FORGOT_RATE_LIMIT_CAPACITY=3
PASSWORD_FORGOT_RATE_LIMIT_WINDOW=1h
MAIL_PORT=587
S3_REGION=us-east-1
S3_UPLOAD_URL_TTL=PT15M
S3_DOWNLOAD_URL_TTL=PT1H
S3_CONNECT_TIMEOUT=PT2S
S3_SOCKET_TIMEOUT=PT5S
S3_API_CALL_ATTEMPT_TIMEOUT=PT7S
S3_API_CALL_TIMEOUT=PT15S
PROFILE_PHOTO_MAX_FILE_SIZE=10485760
PROFILE_PHOTO_MAX_COUNT=6
PROFILE_PHOTO_PENDING_TTL=P1D
LUNCH_ZONE_ID=Europe/Moscow
LUNCH_WINDOW_START=12:00
LUNCH_WINDOW_END=15:00
LUNCH_SLOT_INTERVAL=PT15M
LUNCH_EXTENSION_DURATION=PT10M
LUNCH_EXTENSION_RESPONSE_TIMEOUT=PT5M
LUNCH_MAX_EXTENSIONS=2
LUNCH_GROUP_DURATION=PT1H
LUNCH_MATCHING_ENABLED=true
LUNCH_MATCHING_FIXED_DELAY=PT1M
LUNCH_MATCHING_BUCKET_BATCH_SIZE=50
LUNCH_LIFECYCLE_ENABLED=true
LUNCH_LIFECYCLE_FIXED_DELAY=PT1M
LUNCH_LIFECYCLE_BATCH_SIZE=100
LUNCH_CHAT_RETENTION=P7D
LUNCH_CHAT_CLEANUP_ENABLED=true
LUNCH_CHAT_CLEANUP_FIXED_DELAY=PT1H
LUNCH_CHAT_CLEANUP_BATCH_SIZE=500
OUTBOX_WORKER_FIXED_DELAY=PT1S
OUTBOX_WORKER_BATCH_SIZE=50
OUTBOX_WORKER_MAX_ATTEMPTS=5
OUTBOX_CLEANUP_FIXED_DELAY=PT1H
OUTBOX_PROCESSED_RETENTION=P7D
WEEZZY_VERSION=<release-version>
```

Production profile не содержит fallback-значений для БД, Redis или JWT secret.
Без обязательных secrets приложение завершает запуск с ошибкой.

## Авторизация и безопасность

Без JWT доступны:

- `POST /api/auth/register`;
- `POST /api/auth/login`;
- `POST /api/auth/refresh`;
- `POST /api/auth/email/verify`;
- `POST /api/auth/email/resend`;
- `POST /api/auth/password/forgot`;
- `POST /api/auth/password/reset`;
- `/v3/api-docs/**`;
- `/swagger-ui/**`;
- `/actuator/health` и `/actuator/health/**`.

Остальные endpoints требуют access token:

```http
Authorization: Bearer <accessToken>
```

Register создаёт неподтверждённого пользователя и отправляет ссылку подтверждения.
До подтверждения login возвращает `403 Forbidden`. После подтверждения login
возвращает access token вместе с одноразовым refresh token.
Access token по умолчанию действует 15 минут. Вызов `/api/auth/refresh` заменяет
refresh token новым; повторное использование уже заменённого токена отзывает всю
сессию устройства. В базе хранится только SHA-256 hash секрета refresh token.

Запрос восстановления пароля всегда возвращает `202 Accepted`, независимо от
существования email. Ссылка действует 30 минут, повторный запрос отзывает предыдущую.
После успешной смены пароля все refresh-сессии пользователя отзываются. Сброс пароля
не подтверждает email автоматически.

Удаление аккаунта требует повторного ввода текущего пароля. Строка пользователя
удаляется физически вместе с сессиями и временными токенами, а профиль превращается
в обезличенный tombstone `Deleted account`. Skills, interests, goals, blocks,
recommendation impressions и interaction events удаляются. Votes и matches
сохраняются, но любые новые взаимодействия с удалённым профилем запрещены.

В production login, register, повторная отправка email и запрос восстановления
пароля ограничиваются по IP и операции. При исчерпании
лимита API возвращает `429 Too Many Requests`, `Retry-After`,
`X-RateLimit-Limit` и `X-RateLimit-Remaining`. Redis-операция атомарна, поэтому
лимит корректен при нескольких инстансах приложения. При недоступном Redis auth
endpoints fail closed с `503 Service Unavailable`.

Каждый HTTP-ответ содержит `X-Request-ID`. Валидный входящий request ID
прокидывается дальше, иначе сервер генерирует UUID. Тот же ID записывается в MDC,
structured logs и тело API-ошибки.

## Основные API

### Auth и onboarding

| Метод | Endpoint | Назначение |
|---|---|---|
| `POST` | `/api/auth/register` | Регистрация и отправка письма подтверждения |
| `POST` | `/api/auth/login` | Вход и получение JWT |
| `POST` | `/api/auth/refresh` | Rotation refresh token и новая пара токенов |
| `POST` | `/api/auth/email/verify` | Подтверждение email одноразовым токеном |
| `POST` | `/api/auth/email/resend` | Повторная отправка письма подтверждения |
| `POST` | `/api/auth/password/forgot` | Отправка ссылки восстановления пароля |
| `POST` | `/api/auth/password/reset` | Смена пароля одноразовым токеном |
| `POST` | `/api/auth/logout` | Отзыв текущей сессии |
| `POST` | `/api/auth/logout-all` | Отзыв всех сессий пользователя |
| `GET` | `/api/auth/me` | Текущий пользователь |
| `DELETE` | `/api/users/me` | Удаление аккаунта с подтверждением паролем |
| `GET` | `/api/onboarding/me` | Прогресс и недостающие шаги onboarding |

Onboarding состоит из шагов:

```text
PROFILE_DETAILS
SKILLS
INTERESTS
GOALS
PHOTOS
ACTIVATION
```

Ответ содержит `profileId`, `profileStatus`, процент `progress`, флаг
`activationAllowed` и список `missingSteps`. Профиль нельзя перевести в `ACTIVE`,
пока обязательные шаги не завершены. Если данные активного профиля снова стали
неполными, профиль возвращается в `DRAFT` и onboarding можно пройти повторно.

### Профиль

| Метод | Endpoint | Назначение |
|---|---|---|
| `POST` | `/api/profiles` | Создать свой профиль |
| `GET` | `/api/profiles/me` | Получить свой профиль |
| `PATCH` | `/api/profiles/me` | Обновить свой профиль и статус |
| `GET` | `/api/profiles/{id}` | Получить доступный профиль по ID |
| `GET` | `/api/profiles` | Page-список профилей |

Связи своего профиля:

```text
POST   /api/profiles/me/skills/{skillId}
GET    /api/profiles/me/skills
DELETE /api/profiles/me/skills/{skillId}

POST   /api/profiles/me/interests/{interestId}
GET    /api/profiles/me/interests
DELETE /api/profiles/me/interests/{interestId}

POST   /api/profiles/me/goals/{goalId}
GET    /api/profiles/me/goals
DELETE /api/profiles/me/goals/{goalId}
```

Фотографии своего профиля:

```text
POST   /api/profiles/me/photos/uploads
POST   /api/profiles/me/photos/{photoId}/confirm
GET    /api/profiles/me/photos
PATCH  /api/profiles/me/photos/order
PUT    /api/profiles/me/photos/{photoId}/avatar
DELETE /api/profiles/me/photos/{photoId}
```

Upload endpoint возвращает временный presigned `PUT` URL. Клиент загружает файл
напрямую в object storage, после чего подтверждает загрузку отдельным запросом.
Готовые фотографии возвращаются с временными presigned `GET` URL.

Telegram отсутствует в обычном публичном ответе профиля. Контакт возвращается
только владельцу и участникам существующего match. Block в любом направлении
закрывает прямое чтение профиля и любые новые взаимодействия.

### Каталоги и suggestions

Каталоги доступны по:

```text
/api/skills
/api/interests
/api/goals
```

Чтение требует JWT. Создание записей, а также изменение/удаление interests и goals
требуют роль `ADMIN`.

Пользовательские предложения:

```text
POST /api/skill-suggestions
GET  /api/skill-suggestions/me
POST /api/interest-suggestions
GET  /api/interest-suggestions/me
```

Административная модерация:

```text
GET   /api/admin/skill-suggestions
PATCH /api/admin/skill-suggestions/{id}/approve
PATCH /api/admin/skill-suggestions/{id}/reject

GET   /api/admin/interest-suggestions
PATCH /api/admin/interest-suggestions/{id}/approve
PATCH /api/admin/interest-suggestions/{id}/reject
```

Предложение не попадает в общий каталог до одобрения администратором.

### Рекомендации

```http
GET /api/recommendations
```

Параметры:

| Параметр | Значение |
|---|---|
| `limit` | Размер выдачи, по умолчанию `20`, максимум `100` |
| `cursor` | Cursor из предыдущего ответа |
| `faculty` | Точное название факультета |
| `studyProgram` | Точное название образовательной программы |
| `courses` | Список курсов `1..6` |
| `skillIds` | UUID skills |
| `interestIds` | UUID interests |
| `goalIds` | UUID goals |

Scoring, фильтрация, стабильная сортировка и cursor выполняются в PostgreSQL.
Дефолтные веса:

- skill: `3`;
- interest: `2`;
- goal: `5`.

Веса и cooldown настраиваются через `app.recommendation`. Уже проголосованные,
заблокированные и собственный профили исключаются. Показанные без голоса анкеты
не выдаются повторно семь дней. Пустой набор signals обрабатывается как cold start
и возвращает пустую страницу без тяжёлого ranking query.

Ответ содержит `content`, объяснение совпадений и `nextCursor`.

### Votes, matches и blocks

```text
POST   /api/votes/{targetProfileId}
GET    /api/votes
GET    /api/matches
DELETE /api/matches/{matchedProfileId}
POST   /api/blocks/{blockedProfileId}
GET    /api/blocks
DELETE /api/blocks/{blockedProfileId}
```

Тело голосования:

```json
{
  "action": "LIKE"
}
```

Допустимы `LIKE` и `PASS`. Повторный vote обновляет существующий vote. Нельзя
голосовать, матчиться или блокировать самого себя. Взаимный `LIKE` создаёт ровно
один match даже при конкурентных запросах.

Изменение `LIKE` на `PASS` удаляет существующий match. Unmatch также удаляет match
и не позволяет ему мгновенно восстановиться из старых голосов. Block в любом
направлении удаляет match, закрывает профиль и запрещает vote/match; unblock не
восстанавливает match автоматически.

### Экспресс-обеды

Справочники университетов и локаций:

```text
GET  /api/universities
GET  /api/universities/{id}
GET  /api/locations
GET  /api/locations/{id}
POST /api/universities      ADMIN
POST /api/locations         ADMIN
```

Пользовательские endpoints:

| Метод | Endpoint | Назначение |
|---|---|---|
| `POST` | `/api/lunch/requests` | Создать одиночную заявку |
| `GET` | `/api/lunch/requests/me` | Получить активную заявку |
| `DELETE` | `/api/lunch/requests/me` | Отменить заявку до формирования группы |
| `POST` | `/api/lunch/requests/me/extend` | Принять предложение продления |
| `GET` | `/api/lunch/groups/me` | Получить активную группу и участников |
| `POST` | `/api/lunch/groups/me/messages` | Отправить сообщение в активную группу |
| `GET` | `/api/lunch/groups/me/messages` | Получить сообщения активной группы |

Пример создания заявки:

```json
{
  "locationId": "00000000-0000-0000-0000-000000000000",
  "time": "IN_30_MINUTES",
  "topic": "NETWORKING",
  "comment": "Хочу обсудить backend"
}
```

Допустимое время: `NOW`, `IN_30_MINUTES`, `IN_1_HOUR`. Темы:
`CASUAL_CHAT`, `STUDY`, `STARTUPS`, `IT_CAREER`, `NETWORKING`.

Окно по умолчанию — `12:00–15:00` в `Europe/Moscow`, шаг слотов — 15 минут.
Matching использует точное совпадение `location + timeSlot`: сначала группы 3–4
с общей темой, затем смешанные группы, а менее чем за 5 минут — аварийные пары.
Соседний слот используется только после подтверждённого продления.

При формировании, предложении продления и системной отмене уведомления создаются
через transactional outbox. Невалидная группа отменяется до начала обеда;
eligible-заявки возвращаются в поиск. Активная группа автоматически завершается
через `LUNCH_GROUP_DURATION`. Ответ группы не раскрывает Telegram.

Для знакомства после ланча отдельный тип связи не используется. Клиент открывает
обычную карточку участника по `profileId` из ответа группы и отправляет `LIKE` через
`POST /api/votes/{profileId}`. Взаимный `LIKE` создаёт стандартный `ProfileMatch` и
раскрывает Telegram по общим правилам; `PASS` в интерфейсе lunch-группы показывать
необязательно.

Чат доступен только текущим участникам группы со статусом `ACTIVE`. После
`COMPLETED` или `CANCELLED` чтение и отправка запрещены. Отправитель всегда
определяется из JWT. Для идемпотентной отправки клиент передаёт UUID сообщения:

```json
{
  "clientMessageId": "00000000-0000-0000-0000-000000000000",
  "content": "Встречаемся у главного входа"
}
```

Первая отправка возвращает `201`, идентичный retry — `200`. Повторное
использование `clientMessageId` с другим текстом возвращает `409`.

История читается cursor-параметрами `before` и `after`, которые нельзя передавать
одновременно. `before` загружает более старые сообщения, `after` используется для
polling новых. Допустимый `limit` — от 1 до 100, значение по умолчанию — 50. Ответ
содержит `nextBeforeCursor` и `nextAfterCursor`; сообщения возвращаются от старых
к новым. Email, Telegram, внутренний user ID и `clientMessageId` не раскрываются.
Сообщения удаляются batch-воркером через `LUNCH_CHAT_RETENTION` после завершения
или отмены группы; активные группы cleanup не затрагивает.

### Уведомления и модерация

```text
GET   /api/notifications/me
PATCH /api/notifications/me/{notificationId}/read
PATCH /api/notifications/me/read-all

POST  /api/reports/{targetProfileId}

GET   /api/admin/reports
GET   /api/admin/reports/{reportId}
PATCH /api/admin/reports/{reportId}/review
PATCH /api/admin/reports/{reportId}/decision

POST  /api/admin/users/{targetUserId}/sanctions
GET   /api/admin/sanctions
GET   /api/admin/sanctions/{sanctionId}
GET   /api/admin/users/{targetUserId}/sanctions
PATCH /api/admin/sanctions/{sanctionId}/revoke
```

Пользовательские уведомления включают LIKE, match, решения по жалобам, санкции,
формирование lunch-группы, предложение продления и системную отмену группы.
Доставка идемпотентна; outbox worker поддерживает retry и восстановление stale locks.

## Pagination

Profiles, skills, interests, goals и административные очереди используют обычную
page pagination:

```text
?page=0&size=20
```

Ответ `PageResponse` содержит:

```text
content, page, size, totalElements, totalPages, hasNext, hasPrevious
```

Votes, matches, blocks и recommendations используют cursor pagination:

```text
?limit=20&cursor=<opaque-token>
```

Ответ содержит `content` и nullable `nextCursor`. Cursor непрозрачен для клиента;
при изменении фильтров его нужно сбросить. Сортировки имеют уникальный tie-breaker,
а необходимые lookup/cursor индексы создаются Flyway migrations.

## Interaction events

Для будущей аналитики и Recommendation V3 сохраняются события:

```text
RECOMMENDATION_IMPRESSION
LIKE
PASS
MATCH
UNMATCH
BLOCK
UNBLOCK
```

События записываются в одной транзакции с бизнес-действием. Таблица индексирована
по source, target, типу и времени события.

## Observability

Публичные probes:

```text
GET /actuator/health
GET /actuator/health/liveness
GET /actuator/health/readiness
```

С JWT доступны:

```text
GET /actuator/info
GET /actuator/metrics
GET /actuator/prometheus
```

Production logs выводятся в ECS JSON и содержат request ID, HTTP method, path,
status и duration. Rate limiter публикует метрику `auth.rate.limit.requests` без
IP в labels.

Lunch-воркеры публикуют метрики без идентификаторов пользователей, групп и локаций
в labels:

- `weezzy.lunch.matching.*` — запуски, длительность, корзины, группы и участники;
- `weezzy.lunch.request.lifecycle.*` — запуски, истёкшие заявки и продления;
- `weezzy.lunch.group.lifecycle.*` — запуски, отменённые и завершённые группы;
- `weezzy.lunch.chat.cleanup.*` — запуски cleanup и удалённые сообщения.

## Миграции и тесты

Flyway запускается вместе с приложением. Hibernate использует
`ddl-auto=validate`, поэтому схема изменяется только новыми SQL-файлами в:

```text
src/main/resources/db/migration
```

Полный тестовый прогон:

```powershell
.\gradlew.bat test --rerun-tasks --console=plain
```

Тесты используют Testcontainers PostgreSQL и Redis. Docker Desktop должен быть
запущен. Текущий suite покрывает HTTP API, security, onboarding, pagination,
privacy/match lifecycle, moderation, outbox, lunch matching/lifecycle,
concurrency, recommendation ranking и rate limiting.

## CI

Workflow `.github/workflows/ci.yml` запускает:

- полный Gradle test suite;
- Dependency Review для pull requests;
- отправку Gradle dependency graph в GitHub;
- CodeQL-анализ Java-кода;
- загрузку test reports при падении тестов.

## Структура

```text
src/main/java/ru/itmo/nemat/weezzy
├── common          общие DTO, ошибки, pagination и observability
├── connection      votes, matches, blocks и interaction events
├── goal            каталог goals
├── interest        каталог и suggestions interests
├── location        университеты и локации встреч
├── lunch           заявки, matching, группы и lifecycle экспресс-обедов
├── moderation      жалобы и санкции
├── notification    in-app уведомления
├── onboarding      прогресс и правила активации профиля
├── outbox          transactional outbox, delivery и cleanup
├── profile         профиль и его signals
├── recommendation  ranking, filters, cursor и impressions
├── security        JWT, security errors и Redis rate limit
├── skill           каталог и suggestions skills
├── storage         S3-compatible object storage
└── user            пользователи, роли и auth
```

Архитектура организована package-by-domain. Контроллеры отвечают за HTTP и
authenticated principal, бизнес-логика находится в services, доступ к данным —
в repositories, а изменения схемы — в Flyway migrations.

## Ближайший roadmap

- privacy-safe fingerprint удалённого email против повторной регистрации.
- внешний канал доставки решения пользователю с активной санкцией, если он
  потребуется продукту.
