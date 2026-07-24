# Weezzy

Weezzy is a Spring Boot backend for networking, team search, and people recommendations.

## Local infrastructure

Start PostgreSQL, Redis, and pgAdmin:

```powershell
docker compose up -d
```

Useful local URLs and credentials:

- PostgreSQL: `localhost:5433`, database `weezzy`, user `weezzy`, password `weezzy_dev_password`
- Redis: `localhost:6380`
- pgAdmin: `http://localhost:5050`, email `admin@weezzy.dev`, password `admin`

Stop containers:

```powershell
docker compose down
```

Remove containers and local database volumes:

```powershell
docker compose down -v
```

## Application

Run the app with the default local profile:

```powershell
.\gradlew.bat bootRun
```

Run tests:

```powershell
.\gradlew.bat test
```
