# TTG Subscriber Service

Resource-server на Spring Boot, владеющий **подписками** и **промо-кодами** (перенос из core-api).
Сервис только валидирует JWT, выпущенные `auth-service` (общий HMAC-секрет), и не выпускает токены сам.
Таблицы пользователей здесь нет: принципал собирается из claims токена.

- Spring Boot 4.0.3, Java 21, Maven
- PostgreSQL, схема `subscriber`, миграции Liquibase
- Security 7, stateless, `@Secured("ADMIN")` / `@Secured("USER")` (роли из claim `roles`, **без** префикса `ROLE_`)

## Сборка и запуск

```sh
./mvnw -DskipTests package
java -jar target/subscriber.jar
```

Docker-образ собирается по `Dockerfile` (multi-stage), деплой — через `.github/workflows/deploy.yml` (Dokploy).

## Конвенция идентификатора пользователя

Подписки и награды хранятся по **username** (колонки `owner_username` / `username`). Путь-параметр
`{userId}` в админских и внутренних эндпоинтах трактуется как **username** из auth-service.

## Эндпоинты

Пользовательские (Bearer JWT, `@Secured`):
- `POST /api/subscriptions/redeem` — погасить код
- `POST /api/subscriptions/{id}/activate` — активировать подписку
- `GET  /api/subscriptions/status` — реал-тайм статус текущего пользователя
- `GET  /api/subscriptions/my`, `GET /api/subscriptions/my-codes`
- `GET  /api/rewards/me`, `GET /api/rewards/supporters`

Админские (`@Secured("ADMIN")`):
- `POST /api/subscriptions/codes`, `GET /api/subscriptions/codes`
- `POST /api/subscriptions/codes/{id}/deactivate`, `.../reactivate`
- `GET  /api/subscriptions/all`
- `GET  /api/rewards/resources`, `PUT /api/rewards/resources/{perk}`, `POST /api/rewards/grant/{username}`
- `GET    /api/admin/subscriptions/{userId}` — подписки пользователя
- `PUT    /api/admin/subscriptions/{userId}/grant` — `{ "months": N }` выдать/продлить
- `DELETE /api/admin/subscriptions/{userId}` — немедленно отключить активные подписки

Service-to-service (заголовок `X-Service-Token`, **без** JWT):
- `GET /api/internal/subscriptions/{userId}/status` — статус для core-api `VttgAccessService` и auth-service

## Переменные окружения (Dokploy)

| Переменная                     | Назначение                                                                 |
|--------------------------------|---------------------------------------------------------------------------|
| `SPRING_DATASOURCE_URL`        | JDBC-URL PostgreSQL                                                        |
| `SPRING_DATASOURCE_USERNAME`   | пользователь БД                                                            |
| `SPRING_DATASOURCE_PASSWORD`   | пароль БД                                                                  |
| `DB_POOL_SIZE`                 | максимум соединений Hikari (по умолчанию 10)                               |
| `DB_MIN_IDLE`                  | минимум idle-соединений Hikari (по умолчанию 2)                            |
| `AUTH_SERVICE_JWT_SECRET`      | общий HMAC-секрет для валидации JWT (`auth-service.jwt-secret`)            |
| `INTERNAL_SERVICE_SECRET`      | секрет заголовка `X-Service-Token` для `/api/internal/**` и вызовов core-api |
| `CORE_API_URL`                 | базовый URL core-api для начисления достижений (`core-api.base-url`)       |
| `SERVER_PORT`                  | порт приложения (по умолчанию 8083)                                        |
| `APP_ALLOWED_FRONTEND_ORIGINS` | список разрешённых CORS-origin фронтенда (через запятую)                   |
| `SPRINGDOC_ENABLED`            | включить Swagger UI / OpenAPI (по умолчанию false)                         |

Секреты в репозитории не хранятся и `.env` не создаётся — значения задаются в Dokploy.

## Интеграция достижений (best-effort)

Каталог достижений живёт в core-api. При погашении кода сервис делает **best-effort** вызов
`POST {CORE_API_URL}/api/internal/achievements/grant` (`CoreApiAchievementClient`, заголовок
`X-Service-Token`). Ошибка вызова только логируется и не валит погашение. Эндпоинт на стороне
core-api появится позже.
