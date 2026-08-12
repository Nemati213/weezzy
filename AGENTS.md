# AGENTS.md

## 1. О проекте (Project Overview)

Weezzy - backend для внутреннего ITMO-сервиса нетворкинга, общения и поиска людей
в команду. По смыслу это не классический dating app, а платформа, где студент может
создать профиль, указать скиллы, интересы и цели, получать рекомендации людей,
голосовать за анкеты и получать матч при взаимном `LIKE`.

Главная бизнес-логика:

- пользователь регистрируется и логинится через email/password;
- email подтверждается одноразовым токеном; пароль можно восстановить по одноразовой
  ссылке;
- access JWT работают вместе с refresh token rotation и серверными сессиями;
- после регистрации пользователь создает свой профиль;
- профиль содержит имя, bio, telegram, факультет, образовательную программу, курс и
  статус;
- профиль может иметь несколько skills, interests и goals;
- профиль имеет несколько фотографий, одну главную фотографию/avatar и настраиваемый
  порядок фотографий;
- файлы фотографий хранятся в S3-compatible object storage, а PostgreSQL хранит
  только metadata и object key;
- рекомендации строятся по совпадениям signals: skills, interests, goals;
- уже проголосованные профили больше не должны появляться в рекомендациях;
- голосование хранится как один vote на пару `source_profile_id` + `target_profile_id`;
- повторный vote по той же паре обновляет action;
- нельзя голосовать за себя;
- взаимный `LIKE` создает match;
- onboarding определяет готовность профиля и не позволяет активировать анкету без
  обязательных данных, signals и хотя бы одной готовой фотографии;
- пользователи могут предлагать новые skills/interests через suggestions, но они не
  попадают в общий каталог сразу.
- при удалении аккаунта user и персональные данные удаляются, а обезличенный профиль
  сохраняется для целостности votes и matches.

Проект сейчас является backend-only приложением. Фронта и мобильного приложения в
репозитории пока нет.

## 2. Технологический стек (Tech Stack)

Основное:

- Java 21
- Spring Boot 4.1
- Gradle Kotlin DSL (`build.gradle.kts`)
- PostgreSQL
- pgvector image для локального Postgres (`pgvector/pgvector:pg17`)
- Flyway migrations
- Spring Data JPA / Hibernate
- Spring MVC
- Spring Validation
- Spring Security
- JWT access tokens
- Redis для auth rate limiting
- Spring Mail для email verification и password reset
- AWS SDK for Java для S3-compatible object storage
- MinIO для локального хранения фотографий
- BCrypt password encoder
- Lombok

Тесты:

- JUnit 5
- Spring Boot Test
- MockMvc
- Testcontainers
- PostgreSQL Testcontainers
- Jackson ObjectMapper from `tools.jackson`

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

Корень проекта:

- `build.gradle.kts` - зависимости, Java toolchain, настройки тестов;
- `settings.gradle.kts` - имя Gradle-проекта;
- `docker-compose.yml` - PostgreSQL, Redis, pgAdmin, MinIO и инициализация bucket;
- `README.md` - краткие команды запуска;
- `src/main/resources/application.yaml` - дефолтный профиль и JWT-настройки;
- `src/main/resources/application-local.yaml` - datasource, Flyway, JPA validate;
- `src/main/resources/db/migration` - Flyway SQL migrations;
- `src/main/java/ru/itmo/nemat/weezzy` - production Java-код;
- `src/test/java/ru/itmo/nemat/weezzy` - тесты.

Основные Java-пакеты:

- `user` - регистрация, логин, users, roles и auth DTO;
- `user.emailverification`, `user.passwordreset` - подтверждение email и
  восстановление пароля;
- `user.accountdeletion` - удаление аккаунта и очистка пользовательских данных;
- `security` - JWT service, JWT filter, security config, principal;
- `security.session`, `security.ratelimit` - refresh-сессии и auth rate limiting;
- `profile` - профили пользователей и user-owned endpoints;
- `profile.deletion` - анонимизация профиля при удалении аккаунта;
- `profile.photo` - metadata фотографий, presigned upload/download, avatar и порядок;
- `profile.skill`, `profile.interest`, `profile.goal` - связи профиля с каталогами;
- `skill`, `interest`, `goal` - справочники skills/interests/goals;
- `skill.suggestion`, `interest.suggestion` - пользовательские предложения новых
  skills/interests;
- `recommendation` - рекомендательная логика;
- `connection.vote` - votes между профилями;
- `connection.match` - matches между профилями;
- `connection.block` - блокировки между профилями;
- `connection.event` - история impressions, votes, matches и blocks;
- `onboarding` - готовность профиля и активация анкеты;
- `storage` - абстракция object storage и S3-реализация;
- `common.pagination` - cursor pagination и общие форматы страниц;
- `common.exception` - базовые exception-типы;
- `common.error` - единый HTTP error handler.

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
- роли уже есть (`UserRole`), но полноценная admin-модель еще не доделана.

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
- Пиши чистый, читаемый код. Если добавляешь новую сложную функцию, напиши к ней
  JavaDoc или короткий комментарий, если это реально помогает понять код.
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

## 6. Текущий фокус (Current Roadmap)

### 1. Reports, moderation и блокировка аккаунтов

- позволить authenticated user пожаловаться на чужой профиль;
- поддержать причины жалобы: spam, harassment, inappropriate content, fake profile
  и other с пользовательским комментарием;
- запретить жалобы на собственный профиль и повторную открытую жалобу на ту же цель;
- хранить жизненный цикл жалобы со статусами `PENDING`, `REVIEWED`, `REJECTED` и
  `RESOLVED`, решением модератора и audit timestamps;
- добавить admin endpoints для очереди жалоб, фильтрации, просмотра и вынесения
  решения;
- поддержать временную приостановку и постоянную блокировку аккаунта с причиной,
  сроком и автором решения;
- при санкции отзывать активные auth sessions и запрещать login, refresh и новые
  взаимодействия заблокированного пользователя;
- отдельно закрыть обход постоянной блокировки через удаление аккаунта и повторную
  регистрацию на тот же email: хранить после удаления privacy-safe HMAC fingerprint
  нормализованного email в PostgreSQL, не сохраняя исходный адрес;
- корректно обрабатывать жалобы и audit history после удаления аккаунта или
  анонимизации профиля;
- добавить Flyway migrations, индексы, проверки ролей и интеграционные тесты happy
  path, validation, duplicates, auth и admin access.

### 2. Notifications и transactional outbox

- добавить in-app уведомления как минимум для нового match, решения по жалобе и
  административной санкции;
- поддержать unread/read state, cursor pagination, отметку одного уведомления и всех
  уведомлений как прочитанных;
- записывать outbox event в той же PostgreSQL-транзакции, где произошло бизнес-событие;
- обрабатывать outbox асинхронным worker с retry, backoff и идемпотентной доставкой;
- не использовать Redis как основное хранилище уведомлений или outbox;
- подготовить расширение каналов доставки на email без связывания доменной логики с
  конкретным sender;
- добавить метрики pending/failed events, политику очистки обработанных записей и
  интеграционные тесты транзакционности, повторной обработки и cursor pagination.
