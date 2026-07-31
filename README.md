# Weezzy

[![CI](https://github.com/Nemati213/weezzy/actions/workflows/ci.yml/badge.svg)](https://github.com/Nemati213/weezzy/actions/workflows/ci.yml)

Weezzy — backend внутреннего сервиса ИТМО для нетворкинга, общения и поиска людей
в команду. Пользователь создаёт профиль, указывает навыки, интересы и цели, получает
рекомендации других участников и голосует за анкеты. Взаимный `LIKE` создаёт матч.

Проект пока содержит только backend. Фронтенд и мобильное приложение находятся вне
этого репозитория.

## Что реализовано

- регистрация и вход по email/password;
- JWT-аутентификация и роли `USER`/`ADMIN`;
- пользовательский профиль со статусами `DRAFT`, `ACTIVE`, `HIDDEN`;
- привязка skills, interests и goals к профилю;
- каталоги skills, interests и goals;
- пользовательские предложения новых skills/interests;
- административное одобрение и отклонение предложений;
- рекомендации по совпадениям skills, interests и goals;
- фильтры рекомендаций по факультету, программе, курсу и signal IDs;
- объяснение score, cursor для бесконечного скролла и учёт impressions;
- голоса `LIKE`/`PASS`;
- создание матча при взаимном `LIKE`;
- получение списка матчей текущего пользователя;
- интерактивная документация OpenAPI/Swagger UI.

## Стек

- Java 21;
- Spring Boot 4.1;
- Spring MVC, Spring Security, Spring Data JPA;
- PostgreSQL 17 и Flyway;
- JWT и BCrypt;
- Gradle Kotlin DSL;
- JUnit 5, MockMvc и Testcontainers;
- Docker Compose для локальной инфраструктуры.

Redis уже добавлен в Docker Compose, но текущая бизнес-логика его не использует.

## Быстрый запуск

Требования:

- JDK 21;
- Docker Desktop с Docker Compose.

Запустить PostgreSQL, Redis и pgAdmin:

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
- PostgreSQL: `localhost:5433`;
- Redis: `localhost:6380`;
- pgAdmin: `http://localhost:5050`.

Локальные реквизиты PostgreSQL:

```text
database: weezzy
username: weezzy
password: weezzy_dev_password
```

Локальные реквизиты pgAdmin:

```text
email: admin@weezzy.dev
password: admin
```

Остановить инфраструктуру:

```powershell
docker compose down
```

Удалить контейнеры вместе с локальными данными:

```powershell
docker compose down -v
```

## Авторизация

Без JWT доступны:

- `POST /api/auth/register`;
- `POST /api/auth/login`;
- `/v3/api-docs/**`;
- `/swagger-ui/**`.

Остальные запросы требуют access token:

```http
Authorization: Bearer <accessToken>
```

В Swagger UI токен можно указать через кнопку `Authorize`. Передавать слово
`Bearer` вручную в поле авторизации не нужно.

Дефолтный access token живёт один час. Локальный JWT secret предназначен только для
разработки и должен быть заменён в production.

Основные переменные конфигурации:

```text
APP_SECURITY_JWT_SECRET
APP_SECURITY_JWT_ACCESS_TOKEN_TTL
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
```

## Основные API

### Пользователь и профиль

| Метод | Endpoint | Назначение |
|---|---|---|
| `POST` | `/api/auth/register` | Регистрация |
| `POST` | `/api/auth/login` | Вход |
| `GET` | `/api/auth/me` | Текущий пользователь |
| `POST` | `/api/profiles` | Создание своего профиля |
| `GET` | `/api/profiles/me` | Свой профиль |
| `PATCH` | `/api/profiles/me` | Обновление своего профиля |
| `GET` | `/api/profiles/{id}` | Профиль по ID |
| `GET` | `/api/profiles` | Список профилей |

Связи своего профиля со справочниками:

```text
POST/GET/DELETE /api/profiles/me/skills/{skillId}
POST/GET/DELETE /api/profiles/me/interests/{interestId}
POST/GET/DELETE /api/profiles/me/goals/{goalId}
```

Для `GET` сегмент `/{id}` отсутствует.

### Каталоги и предложения

Каталоги доступны по `/api/skills`, `/api/interests`, `/api/goals`.

- чтение каталогов требует JWT;
- создание skills и изменение interests требуют роль `ADMIN`;
- операции изменения goals сейчас доступны любому авторизованному пользователю;
- предложенный skill/interest не попадает в каталог до одобрения администратором.

Пользовательские endpoints:

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

Очереди администратора поддерживают параметры `status`, `page`, `size`.

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
| `courses` | Курсы `1..6`, например `2,3` |
| `skillIds` | UUID навыков через запятую |
| `interestIds` | UUID интересов через запятую |
| `goalIds` | UUID целей через запятую |

Разные группы фильтров объединяются через `AND`. Несколько значений внутри одной
группы объединяются через `OR`.

Score рассчитывается по весам:

- skill: `3`;
- interest: `2`;
- goal: `5`.

Ответ содержит `content`, структурированный `reason` и `nextCursor`. При изменении
фильтров клиент должен сбросить cursor и начать выдачу заново.

Уже проголосованные анкеты исключаются навсегда. Показанные без голоса анкеты
исключаются через recommendation impressions на семь дней, после чего могут
появиться снова.

### Голоса и матчи

```text
POST /api/votes/{targetProfileId}
GET  /api/votes
GET  /api/matches
```

Тело голосования:

```json
{
  "action": "LIKE"
}
```

Допустимые значения: `LIKE`, `PASS`. Повторное голосование по той же паре обновляет
существующий vote. Голосовать за себя нельзя. Взаимный `LIKE` создаёт один match.

`GET /api/matches` возвращает только матчи текущего пользователя, новые матчи идут
первыми.

### Блокировки

```text
POST   /api/blocks/{blockedProfileId}
GET    /api/blocks
DELETE /api/blocks/{blockedProfileId}
```

Блокировка направленная: пользователь может снять только установленную им
блокировку. Если блокировка существует хотя бы в одном направлении, профили не
попадают друг другу в рекомендации и не могут голосовать или создавать match.
Блокировка удаляет существующий match, но сохраняет votes. Разблокировка не
восстанавливает match автоматически.

## Миграции и база данных

Flyway запускается вместе с приложением. Hibernate работает в режиме
`ddl-auto=validate`, поэтому схема изменяется только SQL-миграциями из:

```text
src/main/resources/db/migration
```

После изменения уже применённой миграции локальную dev-базу может потребоваться
пересоздать:

```powershell
docker compose down -v
docker compose up -d
```

## Тесты

Полный прогон:

```powershell
.\gradlew.bat test --rerun-tasks --console=plain
```

Тесты используют Testcontainers и самостоятельно запускают PostgreSQL. Docker
Desktop должен быть запущен.

Быстрая проверка компиляции production и test-кода:

```powershell
.\gradlew.bat compileTestJava --console=plain
```

## Структура проекта

```text
src/main/java/ru/itmo/nemat/weezzy
├── common          общие ошибки и конфигурация
├── connection      votes, matches и blocks
├── goal            каталог целей
├── interest        каталог и предложения интересов
├── profile         профиль и его signals
├── recommendation  рекомендации, filters и impressions
├── security        JWT и Spring Security
├── skill           каталог и предложения навыков
└── user            пользователи, роли и auth
```

Архитектура организована package-by-domain. Контроллеры отвечают за HTTP и
authenticated principal, бизнес-логика находится в сервисах, доступ к данным — в
Spring Data repositories.
