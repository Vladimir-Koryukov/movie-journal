# Movie Journal

Личный дневник фильмов с отдельными аккаунтами пользователей. В нём можно найти фильм через OMDb или добавить его вручную, отметить статус («планирую посмотреть» / «просмотрен»), поставить оценку и сохранить заметку. Для выбранных в OMDb фильмов сохраняются описание, постер, жанры и основной актёрский состав. Записи каждого пользователя доступны только в его аккаунте.

Backend построен на Spring Boot, frontend — на HTML, CSS и JavaScript со сборкой через Vite. Данные и HTTP-сессии хранятся в PostgreSQL. Nginx отдаёт интерфейс и передаёт API-запросы backend. Сборка и запуск выполняются через Docker Compose.

## Требования

- Работающий Docker Desktop в режиме Linux-контейнеров или Docker Engine с Docker Compose v2.
- Доступ в Интернет для первой сборки.

Java, Maven, Node.js и PostgreSQL отдельно устанавливать не требуется.

## Настройка окружения

Скопируйте `.env.example` в `.env` в корне проекта и задайте свой пароль вместо `change-me-before-start`:

```dotenv
APP_VERSION=0.3.0
POSTGRES_DB=movie_journal
POSTGRES_USER=movie_journal_app
POSTGRES_PASSWORD=change-me-before-start
HTTP_PORT=8080
OMDB_API_KEY=
```

`.env` не включается в Git. Для поиска фильмов получите бесплатный ключ на [странице OMDb API Key](https://www.omdbapi.com/apikey.aspx) и укажите его в `OMDB_API_KEY`. Бесплатный тариф рассчитан на 1000 запросов в сутки. Без ключа приложение запускается, а ручное добавление фильмов продолжает работать.

Контейнер backend предпочитает IPv4 для внешних запросов. Это позволяет использовать OMDb в IPv4-кластерах и сетях без рабочего IPv6-маршрута.

База и пользователь создаются автоматически при первом запуске. Если база уже создана, изменение `.env` само по себе не изменит пароль в PostgreSQL.

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

- Интерфейс: <http://localhost:8080/>.
- Проверка состояния: <http://localhost:8080/actuator/health>, поле `status` должно быть `UP`.

Если изменили `HTTP_PORT`, используйте этот порт в адресах.

## Логи

```shell
docker compose logs --tail=100 backend frontend migrate db
```

События Spring Boot и access-логи Nginx выводятся в JSON. Приложение записывает отдельные события регистрации и входа, изменений дневника и обращений к OMDb, не помещая в логи пароли, API-ключи, заметки или поисковые запросы. Error-логи Nginx, журнал PostgreSQL и служебные сообщения могут быть текстовыми.

## Остановка

```shell
docker compose down
```

Данные PostgreSQL сохраняются в Docker volume. Не добавляйте `-v`, если хотите сохранить записи. Для повторного запуска выполните `docker compose up -d`.

## Запуск в Kubernetes

Манифесты и [инструкция запуска в Minikube](k8s/README.md) находятся в папке `k8s`. PostgreSQL использует постоянный том, миграции выполняются отдельным Job, backend и frontend запускаются через Deployment.

Для сбора и поиска логов приложения доступны [VictoriaLogs и Vector](k8s/logging/README.md) с примерами запросов LogsQL.

Метрики Spring Boot, бизнес-операций и PostgreSQL собираются через [VictoriaMetrics и vmagent](k8s/monitoring/README.md). Для Grafana подготовлен dashboard с HTTP/JVM, PostgreSQL и бизнес-показателями.
