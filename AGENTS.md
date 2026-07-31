# AGENTS.md

## 1. О проекте (Project Overview)

Weezzy - backend для внутреннего ITMO-сервиса нетворкинга, общения и поиска людей
в команду. По смыслу это не классический dating app, а платформа, где студент может
создать профиль, указать скиллы, интересы и цели, получать рекомендации людей,
голосовать за анкеты и получать матч при взаимном `LIKE`.

Главная бизнес-логика:

- пользователь регистрируется и логинится через email/password;
- после регистрации пользователь создает свой профиль;
- профиль содержит имя, bio, telegram, факультет, образовательную программу, курс и
  статус;
- профиль может иметь несколько skills, interests и goals;
- рекомендации строятся по совпадениям signals: skills, interests, goals;
- уже проголосованные профили больше не должны появляться в рекомендациях;
- голосование хранится как один vote на пару `source_profile_id` + `target_profile_id`;
- повторный vote по той же паре обновляет action;
- нельзя голосовать за себя;
- взаимный `LIKE` создает match;
- пользователи могут предлагать новые skills/interests через suggestions, но они не
  попадают в общий каталог сразу.

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

- `docker-compose.yml` поднимает PostgreSQL, Redis и pgAdmin;
- PostgreSQL локально доступен на `localhost:5433`;
- Redis локально доступен на `localhost:6380`;
- pgAdmin локально доступен на `http://localhost:5050`.

Важно: Redis уже есть в Docker Compose, но бизнес-логика приложения сейчас его почти
не использует. Не добавлять Redis-зависимости в код без явной задачи.

## 3. Архитектура и структура (Project Structure)

Корень проекта:

- `build.gradle.kts` - зависимости, Java toolchain, настройки тестов;
- `settings.gradle.kts` - имя Gradle-проекта;
- `docker-compose.yml` - PostgreSQL, Redis, pgAdmin;
- `README.md` - краткие команды запуска;
- `src/main/resources/application.yaml` - дефолтный профиль и JWT-настройки;
- `src/main/resources/application-local.yaml` - datasource, Flyway, JPA validate;
- `src/main/resources/db/migration` - Flyway SQL migrations;
- `src/main/java/ru/itmo/nemat/weezzy` - production Java-код;
- `src/test/java/ru/itmo/nemat/weezzy` - тесты.

Основные Java-пакеты:

- `user` - регистрация, логин, users, roles, auth DTO;
- `security` - JWT service, JWT filter, security config, principal;
- `profile` - профили пользователей и user-owned endpoints;
- `profile.skill`, `profile.interest`, `profile.goal` - связи профиля с каталогами;
- `skill`, `interest`, `goal` - справочники skills/interests/goals;
- `skill.suggestion`, `interest.suggestion` - пользовательские предложения новых
  skills/interests;
- `recommendation` - рекомендательная логика;
- `connection.vote` - votes между профилями;
- `connection.match` - matches между профилями;
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

- без JWT доступны только `/api/auth/register` и `/api/auth/login`;
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

### Pagination

- добавить cursor pagination для динамических списков matches и votes;
- добавить обычную page pagination для profiles и catalogs;
- использовать единый формат ответа там, где это не ломает существующий API;
- покрыть границы страниц, сортировку и отсутствие дублей тестами.

### Onboarding

- определить минимально заполненный профиль, готовый к рекомендациям;
- описать шаги: профиль, skills, interests, goals, активация анкеты;
- возвращать прогресс и список незаполненных шагов для frontend;
- запретить активацию анкеты, если обязательные шаги не завершены;
- покрыть переходы между шагами и повторное прохождение тестами.
