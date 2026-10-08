# Movie Journal

Личный дневник фильмов: добавляйте фильмы, отмечайте статус («планирую посмотреть» / «просмотрен»), ставьте оценку и оставляйте заметки.

Бэкенд — Spring Boot, фронтенд — HTML/CSS/JS со сборкой через Vite. Данные хранятся в PostgreSQL. Nginx отдаёт интерфейс и передаёт API-запросы бэкенду. Сборка и запуск выполняются через Docker Compose.

## Требования

- Работающий Docker Desktop в режиме Linux-контейнеров или Docker Engine с Docker Compose v2.
- Доступ в Интернет для первой сборки.

Java, Maven, Node.js и PostgreSQL отдельно устанавливать не требуется.

## Настройка окружения

Скопируйте `.env.example` в `.env` в корне проекта и задайте свой пароль вместо `change-me-before-start`:

```dotenv
APP_VERSION=0.2.0
POSTGRES_DB=movie_journal
POSTGRES_USER=movie_journal_app
POSTGRES_PASSWORD=change-me-before-start
HTTP_PORT=8080
```

`.env` не включается в Git. База и пользователь создаются автоматически при первом запуске. Если база уже создана, изменение `.env` само по себе не изменит пароль в PostgreSQL.

## Сборка и запуск

Выполните из корня проекта по очереди, переходя дальше после успеха предыдущей команды:

```shell
docker compose config --quiet
docker compose build backend
docker compose build frontend
docker compose up -d
docker compose ps -a
```

Сборка backend включает тесты. Compose запускает PostgreSQL, применяет миграции через сервис `migrate`, затем запускает backend и Nginx. Ожидается `healthy` у `db`, `backend` и `frontend`; состояние `Exited (0)` у `migrate` означает успешное завершение миграций.

- Интерфейс: [http://localhost:8080/].
- Проверка состояния: [http://localhost:8080/actuator/health], поле `status` должно быть `UP`.

Если изменили `HTTP_PORT`, используйте этот порт в адресах.

## Логи

```shell
docker compose logs --tail=100 backend frontend migrate db
```

События Spring Boot и access-логи Nginx выводятся в JSON. Error-логи Nginx, журнал PostgreSQL и служебные сообщения могут быть текстовыми.

## Остановка

```shell
docker compose down
```

Данные PostgreSQL сохраняются в Docker volume. Не добавляйте `-v`, если хотите сохранить записи. Для повторного запуска выполните `docker compose up -d`.
