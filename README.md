# Weezzy

[![CI](https://github.com/Nemati213/weezzy/actions/workflows/ci.yml/badge.svg)](https://github.com/Nemati213/weezzy/actions/workflows/ci.yml)

Backend внутреннего ITMO-сервиса для нетворкинга, общения и поиска людей в команду.
Профили с навыками, интересами и целями, рекомендации, взаимные LIKE и
экспресс-обеды. Фронта и мобильного приложения в репозитории пока нет.

## Возможности

- Auth: подтверждение email, восстановление пароля, JWT, refresh rotation,
  сессии устройств, logout и удаление аккаунта.
- Профили: onboarding, фотографии в S3/MinIO, навыки, интересы и цели.
- Знакомства: рекомендации с фильтрами, LIKE/PASS, matches и blocks.
  Telegram раскрывается только владельцу профиля и участникам match.
- Модерация: жалобы, временные и постоянные санкции, защита от повторной
  регистрации на тот же email через HMAC fingerprint.
- Уведомления: in-app inbox и transactional outbox с retry и cleanup.
  Назначение и отзыв санкций доставляются также по email.
- Экспресс-обеды: заявки по локации и слоту, matching, группы, продления,
  lifecycle и временный REST-чат.
- Эксплуатация: health probes, Micrometer/Prometheus, request ID и JSON-логи.

**Стек:** Java 21, Spring Boot 4.1, Gradle Kotlin DSL, PostgreSQL 17/pgvector,
Flyway, Redis 8, Spring Mail, S3-compatible storage, JUnit 5 и Testcontainers.

## Локальный запуск

Нужны JDK 21 и запущенный Docker Desktop.

```powershell
docker compose up -d
.\gradlew.bat bootRun
```

По умолчанию включён профиль `local`: письма имитируются локальными sender-ами,
auth rate limit отключён. Redis используется также для отзыва access-токенов.

| Сервис | Адрес |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI | http://localhost:8080/v3/api-docs |
| Health / readiness | `/actuator/health` / `/actuator/health/readiness` |
| PostgreSQL | `localhost:5433` |
| Redis | `localhost:6380` |
| pgAdmin | http://localhost:5050 |
| MinIO API / Console | http://localhost:9000 / http://localhost:9001 |

Локально PostgreSQL: БД `weezzy`, пользователь `weezzy`,
пароль `weezzy_dev_password`. Те же логин и пароль используются для MinIO.
Приватный bucket `weezzy-profile-photos` создаётся автоматически.

Остановить инфраструктуру: `docker compose down`.

## Правила API

Полный список endpoints, DTO и параметров доступен в Swagger.

- Большинство endpoints требуют `Authorization: Bearer <accessToken>`.
  Auth register/login/refresh, подтверждение email, восстановление пароля,
  Swagger и health probes доступны без JWT.
- Login требует подтверждённый email. Refresh token одноразовый; его повторное
  использование отзывает сессию устройства.
- Действия со своим аккаунтом и профилем берут пользователя из JWT.
  Административные операции требуют `ADMIN`.
- Onboarding контролирует активацию профиля. Block закрывает взаимодействия
  и удаляет match; удаление аккаунта очищает персональные данные.
- Активная санкция ограничивает доступ к приложению, включая inbox.
  Повторная регистрация на тот же email запрещена до истечения или отзыва санкции.
- Lunch matching использует точную локацию и слот. Чат доступен только участникам
  активной группы. Знакомство после обеда использует обычный LIKE/match.
- In-app уведомления идемпотентны. Email через SMTP может повториться при сбое
  после отправки, но до фиксации успешной обработки outbox.

## Production

```powershell
docker build -t weezzy .
```

[Dockerfile](Dockerfile) включает профиль `production` и запускает приложение
не от root. Обязательные переменные окружения:

| Назначение | Переменные |
|---|---|
| PostgreSQL | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |
| Redis | `REDIS_URL` |
| Security | `JWT_SECRET`, `SANCTION_EMAIL_HMAC_KEY` |
| Ссылки в письмах | `FRONTEND_BASE_URL` |
| SMTP | `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` |
| Object storage | `S3_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_BUCKET` |

`SANCTION_EMAIL_HMAC_KEY` должен быть не короче 32 байт и сохраняться между
релизами: его замена нарушит поиск ранее сохранённых email fingerprints.

Production включает auth rate limit и отзыв access-токенов через Redis,
SMTP и ECS JSON-логи. Реальная доставка писем о санкциях через production SMTP
пока не проверена; интеграционные тесты используют mock sender.

Все настройки, значения по умолчанию и optional env variables:
[общие](src/main/resources/application.yaml),
[local](src/main/resources/application-local.yaml),
[production](src/main/resources/application-production.yaml).

## Разработка и проверки

Код организован по доменам в `src/main/java/ru/itmo/nemat/weezzy`.
Правила разработки и актуальный snapshot: [AGENTS.md](AGENTS.md).

Схема меняется через [Flyway migrations](src/main/resources/db/migration);
Hibernate использует `ddl-auto=validate`.

```powershell
.\gradlew.bat compileTestJava --console=plain
.\gradlew.bat test --rerun-tasks --console=plain
```

Интеграционные тесты используют Testcontainers PostgreSQL и Redis; нужен Docker.
[CI](.github/workflows/ci.yml) запускает тесты, Dependency Review и CodeQL.

## Дальше

Team Finder / Opportunities начат: команды поддерживают приглашения и управление
составом, заявки на набор — позиции, навыки, дедлайн, карточку, список владельца,
редактирование основных полей и закрытие. Следующий шаг — отклики на позиции и
рекомендации заявок и кандидатов. Backend MVP экспресс-обедов завершён.
