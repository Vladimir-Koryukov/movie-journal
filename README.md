# Movie Journal

Личный дневник фильмов: добавляйте фильмы, отмечайте статус («планирую посмотреть» / «просмотрен»), ставьте оценку и оставляйте заметки.

Бэкенд — Spring Boot, фронтенд — HTML/CSS/JS (сборка через Vite). Данные хранятся в PostgreSQL. REST API и интерфейс отдаются одним JAR-файлом.

## Требования

- **JDK 25**
- **PostgreSQL 18**
- **Node.js 24** и **npm 11**
- **IntelliJ IDEA**

## Подготовка базы данных

1. Установите PostgreSQL и запустите сервер.
2. Подключитесь к служебной базе `postgres` под административной ролью.
3. Создайте роль и базу:

```sql
CREATE ROLE movie_journal_app LOGIN;
\password movie_journal_app
CREATE DATABASE movie_journal
    OWNER movie_journal_app
    ENCODING 'UTF8'
    TEMPLATE template0;
```

Пароль задайте в интерактивном запросе. Запомните его — он понадобится дальше.

## Настройка окружения

Создайте файл `backend/.env` на основе `backend/.env.example`:

```dotenv
DB_URL=jdbc:postgresql://127.0.0.1:5432/movie_journal
DB_USERNAME=movie_journal_app
DB_PASSWORD=ваш_пароль
PORT=8080
```

`PORT` — HTTP-порт приложения. Можно оставить `8080` или указать свой.

## Запуск в IntelliJ IDEA

### 1. Открыть проект

- `File` → `Open` → выберите папку `backend` (там лежит `pom.xml`).
- Дождитесь загрузки зависимостей Maven.

### 2. Настроить JDK

- `File` → `Project Structure` → `Project`.
- Убедитесь, что выбран **JDK 25**.
- Если JDK нет, скачайте через `Add SDK` → `Download JDK`.

### 3. Настроить переменные окружения

- `Run` → `Edit Configurations…`.
- Создайте конфигурацию `Spring Boot` (или `Application`).
- **Main class:** `dev.vladimir.moviejournal.MovieJournalApplication`.
- **Environment variables:** выберите `backend/.env`. Если IDE не поддерживает `.env`, добавьте вручную:
  - `DB_URL=jdbc:postgresql://127.0.0.1:5432/movie_journal`
  - `DB_USERNAME=movie_journal_app`
  - `DB_PASSWORD=ваш_пароль`
  - `PORT=8081`

### 4. Собрать фронтенд

Перед первым запуском соберите фронтенд. Откройте терминал в IDEA (`Alt+F12`) и выполните из корня проекта:

```powershell
cd frontend
npm ci
npm run build
```

Это создаст `frontend/dist`, которую Maven включит в JAR.

### 5. Применить миграции

- `Run` → `Edit Configurations…` → создайте вторую конфигурацию.
- **Main class:** `dev.vladimir.moviejournal.MovieJournalApplication`.
- **Program arguments:** `--migrate`.
- **Environment variables:** те же, что и выше.
- Запустите. Процесс завершится с кодом `0` и создаст таблицы.

### 6. Запустить приложение

Запустите первую конфигурацию (без `--migrate`). Откройте:

- Интерфейс: [http://localhost:8081/]
- Проверка состояния: [http://localhost:8081/actuator/health] — должно быть `{"status":"UP"}`.

## Остановка

`Ctrl+C` в консоли IDEA или кнопка `Stop`. Приложение завершится корректно (graceful shutdown, до 20 секунд).
