# TTG Subscriber Service

Resource-server на Spring Boot, владеющий **подписками** и **промо-кодами** (перенос из core-api).
Сервис только валидирует JWT, выпущенные `auth-service` (общий HMAC-секрет), и не выпускает токены сам.
Таблицы пользователей здесь нет: принципал собирается из claims токена.

- Spring Boot 4.0.3, Java 21, Maven
- PostgreSQL, схема `subscriber`, миграции Liquibase
- Security 7, stateless, `@Secured("ADMIN")` / `@Secured("USER")` (роли из claim `roles`, **без** префикса `ROLE_`)

## Сборка и запуск

```sh
mvn -DskipTests package
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
- `GET  /api/subscriptions/payments/terms` — цена месяца, валюта, предел срока и ступени скидки (их показывает окно покупки). Скидка за срок: от 3 мес. — 3%, от 6 — 6%, от 12 — 9%, ровно 36 мес. — 15% (пасхалка, на сайте не рекламируется; `SubscriptionDiscount`), сумму платежа считает только сервер
- `POST /api/subscriptions/payments` — `{ "months": N, "email": "почта для чека" }` создать платёж на N месяцев и получить ссылку на форму PayAnyWay, которая сразу открывает экран оплаты картой (СБП там же отдельной кнопкой). Платить может только аккаунт с подтверждённой почтой и принятыми офертой и согласием на обработку ПД (проверка через `GET {AUTH_SERVICE_URL}/api/auth/me` с токеном пользователя)
  (пока `PAYANYWAY_ADMIN_ONLY=true`, только для `ADMIN`)

Админские (`@Secured("ADMIN")`):
- `POST /api/subscriptions/codes`, `GET /api/subscriptions/codes`
- `POST /api/subscriptions/codes/{id}/deactivate`, `.../reactivate`
- `GET  /api/subscriptions/all`
- `GET  /api/rewards/resources`, `PUT /api/rewards/resources/{perk}`, `POST /api/rewards/grant/{username}`
- `GET    /api/admin/subscriptions/{userId}` — подписки пользователя
- `PUT    /api/admin/subscriptions/{userId}/grant` — `{ "months": N }` выдать/продлить
- `DELETE /api/admin/subscriptions/{userId}` — немедленно отключить активные подписки
- `GET    /api/admin/subscriptions/{userId}/codes` — промокоды, погашённые пользователем
- `GET    /api/admin/subscriptions/{userId}/history` — история подписки: оплаты, выдачи и отключения админом, промокоды, с датой окончания до и после (новые сверху; ведётся с 12.10.2026)

Service-to-service (заголовок `X-Service-Token`, **без** JWT):
- `GET /api/internal/subscriptions/{userId}/status` — статус для core-api `VttgAccessService` и auth-service

Уведомление PayAnyWay об оплате (без JWT, проверяется подписью):
- `GET|POST /api/subscriptions/payments/payanyway` — Pay URL

## Переменные окружения (Dokploy)

| Переменная                     | Назначение                                                                 |
|--------------------------------|---------------------------------------------------------------------------|
| `SPRING_DATASOURCE_URL`        | JDBC-URL PostgreSQL                                                        |
| `SPRING_DATASOURCE_USERNAME`   | пользователь БД                                                            |
| `SPRING_DATASOURCE_PASSWORD`   | пароль БД                                                                  |
| `DB_POOL_SIZE`                 | максимум соединений Hikari (по умолчанию 10)                               |
| `DB_MIN_IDLE`                  | минимум idle-соединений Hikari (по умолчанию 2)                            |
| `AUTH_SERVICE_JWT_SECRET`      | общий HMAC-секрет для валидации JWT (`auth-service.jwt-secret`)            |
| `INTERNAL_SERVICE_SECRET`      | секрет заголовка `X-Service-Token` для `/api/internal/**` и вызовов core-api и auth-service |
| `AUTH_SERVICE_URL`             | базовый URL auth-service: роли, проверка аккаунта перед оплатой, письмо об оплате (по умолчанию `http://auth-service:8080`) |
| `CORE_API_URL`                 | базовый URL core-api для начисления достижений (`core-api.base-url`)       |
| `TELEGRAM_BOT_TOKEN`           | токен Telegram-бота для выпуска приглашений в чат разработки (пусто — выключено) |
| `TELEGRAM_DEV_CHAT_ID`         | id секретного чата разработки (для супергруппы — вида `-100...`)           |
| `SERVER_PORT`                  | порт приложения (по умолчанию 8083)                                        |
| `APP_ALLOWED_FRONTEND_ORIGINS` | список разрешённых CORS-origin фронтенда (через запятую)                   |
| `SPRINGDOC_ENABLED`            | включить Swagger UI / OpenAPI (по умолчанию false)                         |
| `PAYANYWAY_ACCOUNT_ID`         | номер расширенного счёта PayAnyWay (`MNT_ID`); пусто — оплата выключена   |
| `PAYANYWAY_INTEGRITY_CODE`     | код проверки целостности данных из настроек счёта (секрет)                 |
| `PAYANYWAY_FORM_URL`           | платёжная форма (по умолчанию `https://www.payanyway.ru/assistant.htm`, демо — `https://demo.moneta.ru/assistant.htm`) |
| `PAYANYWAY_TEST_MODE`          | `true` — `MNT_TEST_MODE=1`, деньги не списываются (по умолчанию false: самозанятым тестовый режим недоступен) |
| `PAYANYWAY_ADMIN_ONLY`         | `true` — платить могут только администраторы (по умолчанию true)          |
| `PAYANYWAY_PRICE`              | цена одного месяца подписки в рублях, обязательна (без неё оплата выключена) |
| `PAYANYWAY_MAX_MONTHS`         | сколько месяцев можно оплатить за раз (по умолчанию 0 — без ограничения)   |
| `PAYANYWAY_SITE_URL`           | адрес сайта для возврата с формы оплаты, например `https://ttg.club`; возврат идёт на `/subscription?payment=success\|pending\|fail\|cancel` |

Секреты в репозитории не хранятся и `.env` не создаётся — значения задаются в Dokploy.

## Интеграция достижений (best-effort)

Каталог достижений живёт в core-api. При погашении кода сервис делает **best-effort** вызов
`POST {CORE_API_URL}/api/internal/achievements/grant` (`CoreApiAchievementClient`, заголовок
`X-Service-Token`). Ошибка вызова только логируется и не валит погашение. Эндпоинт на стороне
core-api появится позже.

## Приглашения в Telegram-чат разработки

Для кодов с перком `DEV_CHAT_ACCESS` (в т.ч. любых тиров 4+) сервис при **выпуске** сам
создаёт одноразовую ссылку-приглашение через Telegram Bot API (`TelegramInviteClient`,
`createChatInviteLink` с `member_limit=1` — после первого входа Telegram гасит ссылку)
и хранит её в `redemption_code.telegram_invite_link`. Вызов **обязательный**: ошибка
откатывает выпуск всей партии; партия таких кодов ограничена 50 шт. При **погашении**
кода без ссылки (выпущен до фичи) работает best-effort фолбэк — ошибка только логируется.
Пользователь видит ссылку в наградах кода (`/api/subscriptions/my-codes`, `/api/rewards/me`):
персональная ссылка кода приоритетнее общей из `reward_resource`.

Настройка: `TELEGRAM_BOT_TOKEN` (можно тот же бот, что постит новости в core-api) и
`TELEGRAM_DEV_CHAT_ID`. Бот должен быть **администратором** чата с правом «Приглашать
пользователей». Пока переменные не заданы, выпуск кодов с этим перком отвечает 503.

## Оплата через PayAnyWay

Пользователь выбирает число месяцев и вызывает `POST /api/subscriptions/payments`; автопродления нет.
Сервис создаёт платёж на цену месяца × срок (таблица `payment`)
и возвращает подписанную (`MNT_SIGNATURE`) ссылку на платёжную форму MONETA.Assistant. После оплаты
PayAnyWay присылает уведомление на Pay URL; сервис проверяет подпись, номер счёта, сумму и валюту,
продлевает подписку записью типа `BUY` и отвечает XML `MNT_RESPONSE` с кодом 200. Повторное
уведомление по оплаченному платежу подтверждается без повторного продления; неверная подпись — ответ
`FAIL`, и PayAnyWay повторит уведомление в течение суток. Тестовая оплата (`MNT_TEST_MODE=1`)
принимается только при `PAYANYWAY_TEST_MODE=true`. В ответе на принятую оплату передаются данные чека:
позиция (`INVENTORY`, «НДС не облагается») и почта для чека, указанная при оплате (`CUSTOMER`),
на неё PayAnyWay отправляет чек.
После оплаты на ту же почту уходит наше письмо со сроком подписки: subscriber просит auth-service
(`POST /api/internal/mail/subscription-paid`, `X-Service-Token`), ошибка почты оплату не отменяет.

Настройки счёта в личном кабинете PayAnyWay:
- Pay URL — `https://<сайт>/api/subscriptions/payments/payanyway` (сайт проксирует его сюда), метод POST;
- Check URL — пусто;
- «Подпись формы оплаты обязательна» — включено;
- «Можно переопределять настройки в URL» — включено (возврат на страницу подписки);
- «Тестовый режим» — самозанятым недоступен: проверка идёт боевыми платежами при `PAYANYWAY_ADMIN_ONLY=true`.
