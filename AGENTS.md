# AGENTS.md

## 1. О проекте (Project Overview)

Weezzy - backend для внутреннего ITMO-сервиса нетворкинга, общения и поиска людей
в команду. По смыслу это не классический dating app, а платформа, где студент может
создать профиль, указать скиллы, интересы и цели, получать рекомендации людей,
голосовать за анкеты и получать матч при взаимном `LIKE`.

Основные возможности: auth с подтверждением email и refresh rotation, профили с
фотографиями и signals, рекомендации, votes/matches/blocks, moderation,
уведомления через outbox и экспресс-обеды. Onboarding контролирует активацию
профиля. При удалении аккаунта персональные данные удаляются, а обезличенный
профиль сохраняется для целостности истории.

Проект сейчас является backend-only приложением. Фронта и мобильного приложения в
репозитории пока нет.

## 2. Технологический стек (Tech Stack)

- Java 21, Spring Boot 4.1, Gradle Kotlin DSL;
- Spring MVC, Validation, Security, Data JPA/Hibernate;
- PostgreSQL 17/pgvector и Flyway;
- Redis только для auth rate limiting;
- Spring Mail, AWS SDK и MinIO/S3-compatible storage;
- JUnit 5, Spring Boot Test, MockMvc и Testcontainers.

Локальная инфраструктура:

- `docker-compose.yml` поднимает PostgreSQL, Redis, pgAdmin и MinIO;
- PostgreSQL локально доступен на `localhost:5433`;
- Redis локально доступен на `localhost:6380`;
- pgAdmin локально доступен на `http://localhost:5050`.
- MinIO S3 API локально доступен на `http://localhost:9000`;
- MinIO Console локально доступна на `http://localhost:9001`.

Redis используется для rate limiting auth-операций и не является основным хранилищем
бизнес-данных. Не расширять его использование без явной задачи.

## 3. Архитектура и структура (Project Structure)

Production-код находится в `src/main/java/ru/itmo/nemat/weezzy`, тесты — в
`src/test/java`, конфигурация — в `src/main/resources`, Flyway SQL — в
`src/main/resources/db/migration`.

Ключевые домены: `user/security`, `profile/onboarding`, каталоги signals,
`recommendation`, `connection`, `location/lunch`, `moderation`,
`notification/outbox`, `storage` и `common`.

Архитектурный стиль:

- package-by-domain, а не раздельные глобальные пакеты `controller/service/repository`;
- внутри домена обычно есть `Entity`, `Repository`, `Service`, `Controller`, `dto`;
- для вложенных сущностей используются подпакеты, например `profile.skill`;
- контроллеры тонкие: HTTP, DTO, получение authenticated user, вызов service;
- бизнес-логика находится в service-классах;
- доступ к базе идет через Spring Data repositories;
- schema-first через Flyway: новые таблицы/индексы добавлять миграциями;
- Hibernate `ddl-auto=validate`, поэтому entity обязаны совпадать с миграциями;
- все user-owned действия должны брать user/profile из JWT, а не из request body.

## 4. Правила разработки и Code Style (Development Guidelines)

Java style:

- классы и records - `PascalCase`;
- методы, поля, переменные - `camelCase`;
- enum constants - `UPPER_SNAKE_CASE`;
- пакеты - lowercase, доменные: `skill.suggestion`, `connection.vote`;
- DTO records называются `CreateXRequest`, `UpdateXRequest`, `XResponse`;
- exception-классы называются по смыслу: `DuplicateSkillException`,
  `ProfileNotFoundException`;
- использовать constructor injection через `final` fields и Lombok
  `@RequiredArgsConstructor`;
- entity сейчас обычно используют Lombok `@Data`;
- даты в текущем проекте держать в `LocalDateTime`, чтобы не плодить разные типы;
- строки желательно держать до 85-90 символов, если это не ухудшает читаемость;
- не делать масштабный рефакторинг структуры без отдельного решения.

Spring/JPA rules:

- write operations помечать `@Transactional`;
- read-only operations помечать `@Transactional(readOnly = true)`;
- связи entity оформлять явно: `@ManyToOne`, `@OneToOne`, `@JoinColumn`;
- для many-to-many связей сейчас используются отдельные join entity:
  `ProfileSkill`, `ProfileInterest`, `ProfileGoal`;
- composite keys оформлены через id-классы (`ProfileVoteId`, `ProfileMatchId`);
- не полагаться на Hibernate auto-DDL, все изменения схемы делать через Flyway;
- для новых lookup/search полей сразу думать про индекс.

Security rules:

- без JWT доступны register, login, refresh, подтверждение/повторная отправка email и
  восстановление пароля; точный список задан в `SecurityConfig`;
- все остальные endpoints требуют authentication;
- текущий user берется через `@AuthenticationPrincipal JwtAuthenticatedUser`;
- user-owned endpoints должны быть вида `/me`, например `/api/profiles/me`;
- не принимать `userId` или `sourceProfileId` из body/path там, где это текущий user;
- административные endpoints находятся под `/api/admin/**` и требуют роль `ADMIN`.

Error handling:

- использовать кастомные exceptions, наследованные от:
  `NotFoundException`, `ConflictException`, `BadRequestException`;
- не кидать голый `RuntimeException` для ожидаемых бизнес-ошибок;
- HTTP errors централизованы в `ApiExceptionHandler`;
- validation errors идут через Bean Validation annotations и возвращают `400`;
- duplicates обычно возвращают `409 Conflict`;
- missing entities обычно возвращают `404 Not Found`.

Testing rules:

- для controller/integration tests использовать `@SpringBootTest` + `@AutoConfigureMockMvc`;
- для БД использовать Testcontainers PostgreSQL;
- helper `AuthenticatedTestUser` использовать для регистрации тестового user,
  получения JWT и авторизации MockMvc-запросов;
- тесты должны проверять happy path, validation, auth required, not found/conflict;
- при изменении security обязательно добавлять/обновлять проверки `401`;
- при изменении миграций запускать полный `.\gradlew.bat test`;
- полный тестовый прогон требует запущенный Docker Desktop.

Команды:

```powershell
docker compose up -d
.\gradlew.bat bootRun
.\gradlew.bat compileTestJava --console=plain
.\gradlew.bat test --rerun-tasks --console=plain
```

## 5. Инструкция для AI-агента (Agent Workflow / Rules)

- Твоя роль: Senior Developer. Я делегирую тебе задачи, чтобы ускорить разработку.
- Не ломай существующую логику. Если нужно сделать масштабный рефакторинг - сначала
  спроси разрешения.
- Пиши чистый, читаемый код. Не добавляй комментарии и JavaDoc без явной
  необходимости или прямой просьбы.
- Если в задаче не указано иное, старайся переиспользовать уже существующие в проекте
  helpers, DTO, services, repositories и patterns, не пиши велосипеды.
- Отвечай по делу, без лишней воды. Давай сразу рабочий код.
- Перед правками сначала посмотри релевантные файлы и текущий `git status`.
- Не перетирай пользовательские незакоммиченные изменения.
- Используй `apply_patch` для ручных правок файлов.
- Не коммить изменения без прямой просьбы.
- Если задача затрагивает API, обнови или добавь тесты.
- Если тесты нельзя прогнать из-за Docker/окружения, честно напиши причину.
- В финальном ответе кратко перечисли, что изменилось, и какие проверки прошли.

## 6. Текущий snapshot экспресс-обедов

- Заявки, matching pipeline, формирование групп, продления и lifecycle уже работают.
- Matching использует только точную корзину `location_id + time_slot`; соседние
  слоты объединяются только через подтверждённое пользователем продление.
- Группы формируются по 3–4 человека; менее чем за 5 минут допускаются аварийные
  пары. Совместимость, санкции и blocks повторно проверяются транзакционно.
- Все фоновые переходы конкурентно безопасны и идемпотентны. Корзины matching
  claim-ятся advisory lock, независимые lifecycle-строки — через `SKIP LOCKED`.
- Формирование, предложение продления и системная отмена публикуются через
  transactional outbox.
- Системная отмена сохраняет историю membership, возвращает eligible-заявки в
  `SEARCHING`, а невалидные переводит в `EXPIRED`.
- `GET /api/lunch/groups/me` доступен только участнику активной группы и не раскрывает
  Telegram.
- Временный REST-чат работает через `POST/GET /api/lunch/groups/me/messages`:
  отправка идемпотентна по `clientMessageId`, история использует cursor pagination
  `before/after`, сообщения возвращаются в хронологическом порядке.
- Чат доступен только current membership группы со статусом `ACTIVE`; после
  `COMPLETED`/`CANCELLED` чтение и отправка запрещены. Ответы не раскрывают Telegram,
  email, внутренний user ID и `clientMessageId`.
- Сообщения закрытых групп удаляются batch cleanup-воркером после настраиваемого
  retention. Cleanup использует `SKIP LOCKED`, безопасен для нескольких инстансов и
  публикует метрики запусков, ошибок, длительности и количества удалений.
- Chat send/lifecycle races, конкурентный cleanup, pagination и retention проверены
  интеграционными тестами на PostgreSQL. Полный Testcontainers suite проходит.
- Длительности окна, шага слотов, продления, ответа и активной группы задаются через
  `app.lunch`; retention и cleanup чата — через `app.lunch.chat`.

## 7. Оставшиеся задачи по экспресс-обедам

1. «Хочу остаться на связи»:
   - отдельные выборы участников внутри lunch-группы;
   - Telegram раскрывать только после взаимного выбора;
   - не создавать обычный `ProfileMatch`;
   - уведомление о взаимности публиковать через transactional outbox.
2. Финальное укрепление:
   - конкурентные интеграционные тесты contact flow и transactional outbox;
   - метрики и наблюдаемость для matching/lifecycle workers;
   - проверка идемпотентности, retry и гонок contact flow на реальном PostgreSQL.

## 8. Другие известные задачи

- Закрыть обход постоянной блокировки через удаление аккаунта и повторную регистрацию
  на тот же email: хранить privacy-safe HMAC fingerprint нормализованного email.
- Если заблокированному санкцией пользователю потребуется доставка решения, добавить
  внешний канал, например email; in-app inbox при блокировке намеренно недоступен.
