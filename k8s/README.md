# Movie Journal в Kubernetes

Манифесты запускают frontend на Nginx, backend на Spring Boot и PostgreSQL в одноузловом Minikube. Данные базы хранятся на постоянном томе, миграции выполняются отдельным Job перед запуском backend.

## Требования

- Работающий Docker Engine или Docker Desktop с Linux-контейнерами.
- Minikube и kubectl.
- 2 CPU и 4 ГБ RAM для кластера, доступ в Интернет для первой сборки.

Java, Maven, Node.js и PostgreSQL отдельно устанавливать не требуется. Все команды выполняются из корня проекта.

## Кластер и образы

```shell
minikube start -p minikube --driver=docker --container-runtime=docker --cpus=2 --memory=4096
kubectl config use-context minikube
kubectl get nodes
kubectl get storageclass
```

Ожидается узел `Ready` и класс хранилища `standard` с отметкой `default`.

Для сборки внутри Minikube переключите Docker CLI в текущем терминале. Выберите команду для своей оболочки.

PowerShell:

```powershell
& minikube -p minikube docker-env --shell powershell | Invoke-Expression
```

Bash:

```bash
eval "$(minikube -p minikube docker-env)"
```

Соберите образы по очереди, дожидаясь успешного завершения каждой команды:

```shell
docker build --progress=plain -t movie-journal-backend:0.2.0 ./backend
docker build --progress=plain -t movie-journal-frontend:0.2.0 ./frontend
docker pull postgres:18-alpine
```

Сборка backend включает тесты. Образы, собранные через `docker-env`, уже доступны кластеру. Верните Docker CLI к исходному окружению.

PowerShell:

```powershell
& minikube -p minikube docker-env --unset --shell powershell | Invoke-Expression
```

Bash:

```bash
eval "$(minikube -p minikube docker-env -u)"
```

Проверьте наличие образов:

```shell
minikube -p minikube image ls
```

Если образы уже собраны в локальном Docker вне Minikube, вместо повторной сборки загрузите их:

```shell
minikube -p minikube image load --daemon movie-journal-backend:0.2.0
minikube -p minikube image load --daemon movie-journal-frontend:0.2.0
```

## Настройки и база данных

`config.yml` содержит параметры подключения в ConfigMap и пример пароля в Secret. Перед первым запуском замените `training-only-password` своим значением. Не публикуйте файл с реальным паролем. Изменение Secret после создания базы автоматически не меняет пароль пользователя PostgreSQL. Файл `.env` для этого запуска не используется.

```shell
kubectl apply -f k8s/namespace.yml
kubectl apply -f k8s/config.yml
kubectl apply -f k8s/postgres.yml
kubectl rollout status -n movie-journal statefulset/db --timeout=180s
kubectl get pods,pvc -n movie-journal
```

Ожидается `db-0` — `1/1 Running`, PVC `data-db-0` — `Bound`.

## Миграция и приложение

```shell
kubectl apply -f k8s/migrate.yml
kubectl wait -n movie-journal --for=condition=Complete job/migrate --timeout=240s
```

После успешного завершения миграции:

```shell
kubectl apply -f k8s/backend.yml
kubectl rollout status -n movie-journal deployment/backend --timeout=240s
kubectl apply -f k8s/frontend-config.yml
kubectl apply -f k8s/frontend.yml
kubectl rollout status -n movie-journal deployment/frontend --timeout=180s
kubectl get pods,services,jobs,pvc -n movie-journal
```

У backend, frontend и базы должны быть готовые Pod. Статус `Completed` у Pod миграции означает успешное завершение задачи. Повторный `apply` завершённого Job не запускает его снова; для повторной миграции удалите только `job/migrate` и примените `migrate.yml` заново.

## Доступ к приложению

В отдельном терминале:

```shell
kubectl --context=minikube port-forward -n movie-journal service/frontend 18080:80 --address=127.0.0.1
```

Оставьте команду работающей. Интерфейс доступен по адресу [http://127.0.0.1:18080](http://127.0.0.1:18080), проверка состояния — [http://127.0.0.1:18080/actuator/health](http://127.0.0.1:18080/actuator/health). Ожидаемый статус — `UP`.

Nginx передаёт API-запросы через Service `backend:8080`. Backend подключается к PostgreSQL через `db:5432`. Обе части приложения используют DNS-имена сервисов внутри namespace `movie-journal`.

## Масштабирование и диагностика

```shell
kubectl scale -n movie-journal deployment/backend --replicas=2
kubectl rollout status -n movie-journal deployment/backend --timeout=240s
kubectl get deployment backend -n movie-journal
kubectl get pods -n movie-journal -l app=backend
```

Ожидается `2/2` готовых реплик backend с общей базой данных. В манифесте указана одна реплика; повторное применение `backend.yml` вернёт это значение.

Для диагностики:

```shell
kubectl get events -n movie-journal --sort-by=.metadata.creationTimestamp
kubectl logs -n movie-journal deployment/backend --tail=60
kubectl logs -n movie-journal job/migrate --tail=60
```

## Остановка

Завершите port-forward через `Ctrl+C`, затем выполните:

```shell
minikube stop -p minikube
```

Для повторного запуска используйте `minikube start -p minikube`, дождитесь готовности Pod и снова запустите port-forward. Остановка сохраняет кластер и данные. Удаление профиля Minikube или PVC может привести к потере данных.
