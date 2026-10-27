# Логирование Movie Journal

Vector собирает логи Pod с метками `app=backend` и `app=frontend` в namespace `movie-journal` и отправляет их в VictoriaLogs. Сборщик работает как DaemonSet и читает журналы контейнеров на узле; менять код приложения для доставки логов не требуется.

В namespace `logging` запускаются VictoriaLogs `v1.53.0` и Vector `0.59.0`. VictoriaLogs хранит данные на PVC размером 2 ГиБ, срок хранения — 7 дней.

## Требования

- Приложение запущено по [инструкции Minikube](../README.md).
- Kubernetes версии 1.32 или новее, `kubectl`, работающий класс хранилища по умолчанию.
- Minikube с драйвером Docker: Docker Engine или Docker Desktop с Linux-контейнерами. Кластеру выделены как минимум 2 CPU и 4 ГБ RAM.

Команды выполняются из корня проекта и подходят для Bash и PowerShell. Используется существующий профиль `minikube`.

## Запуск

```shell
kubectl config use-context minikube
kubectl apply -f k8s/logging/namespace.yml
kubectl apply -f k8s/logging/victorialogs.yml
kubectl rollout status -n logging deployment/victorialogs --timeout=300s
kubectl apply -f k8s/logging/vector.yml
kubectl rollout status -n logging daemonset/vector --timeout=300s
kubectl get pods,services,pvc -n logging
```

Ожидаются готовые Pod VictoriaLogs и Vector, а также PVC `victorialogs-data` в состоянии `Bound`.

Обновите формат access-логов Nginx. ConfigMap смонтирован через `subPath`, поэтому для применения требуется перезапуск frontend. Пересборка образа не нужна.

```shell
kubectl apply -f k8s/frontend-config.yml
kubectl rollout restart -n movie-journal deployment/frontend
kubectl rollout status -n movie-journal deployment/frontend --timeout=180s
```

После готовности сборщика можно перезапустить backend, чтобы получить свежие JSON-события запуска:

```shell
kubectl rollout restart -n movie-journal deployment/backend
kubectl rollout status -n movie-journal deployment/backend --timeout=240s
```

## Grafana (необязательно)

Для просмотра логов через Grafana используется образ `grafana-victorialogs:1.0`: Grafana `12.1.0` и плагин VictoriaLogs `0.33.1`. Плагин устанавливается через Grafana CLI во время сборки; локальная папка с файлами плагина не нужна. При сборке необходим доступ к Docker Hub и каталогу плагинов Grafana.

После запуска VictoriaLogs соберите образ непосредственно в существующем Minikube. Команда подходит для Bash и PowerShell и не требует переключения `docker-env` или отдельной загрузки образа:

```shell
minikube -p minikube image build -t grafana-victorialogs:1.0 ./k8s/logging/grafana
```

Только после успешной сборки примените манифест. Перезапуск также обновит Pod, если этот тег образа уже использовался:

```shell
kubectl config use-context minikube
kubectl apply -f k8s/logging/grafana.yml
kubectl rollout restart -n logging deployment/grafana
kubectl rollout status -n logging deployment/grafana --timeout=300s
```

В манифесте стоит `imagePullPolicy: Never`: образ должен находиться в Minikube, из внешнего реестра Kubernetes его не скачивает.

В отдельном терминале:

```shell
kubectl port-forward -n logging service/grafana 3000:3000 --address=127.0.0.1
```

Откройте [Grafana](http://127.0.0.1:3000/). Для первого входа в текущем манифесте заданы `admin` / `admin`. Источник `VictoriaLogs` создаётся автоматически через ConfigMap; выберите его в Explore и выполните запрос из `queries.logsql`. Доступ через localhost относится к машине, на которой запущен `port-forward`.

VictoriaLogs хранит логи на своём PVC. Для самой Grafana PVC в этом манифесте не задан: созданные через интерфейс настройки и панели не сохраняются после замены Pod.

Если Grafana не запускается:

```shell
kubectl logs -n logging deployment/grafana --tail=80
kubectl get pods -n logging -l app=grafana
```

Установка плагинов в образ описана в [документации Grafana](https://grafana.com/docs/grafana/latest/setup-grafana/configure-docker/), версия плагина — в [каталоге VictoriaLogs](https://grafana.com/grafana/plugins/victoriametrics-logs-datasource/installation/).

### Сборка Grafana без скачивания плагина внутри контейнера

Если обращение к каталогу плагинов при сборке завершается тайм-аутом, используйте `Dockerfile.offline`. Заранее скачайте [официальный архив VictoriaLogs 0.33.1](https://grafana.com/api/plugins/victoriametrics-logs-datasource/versions/0.33.1/download) на машине с доступом к Grafana и поместите его в `k8s/logging/grafana/victoriametrics-logs-datasource-0.33.1.zip` рядом с Dockerfile. При переносе проекта перенесите также этот архив.

ZIP исключён из Git через `.gitignore`. Содержимое плагина распаковывается в образ без запросов к каталогу Grafana. Базовый образ `grafana/grafana:12.1.0` должен быть доступен локально или скачан из Docker Hub.

Из корня проекта:

```shell
cd k8s/logging/grafana
minikube -p minikube image build -f Dockerfile.offline -t grafana-victorialogs:1.0 .
cd ../../..
```

После успешной сборки примените `grafana.yml` и перезапустите Deployment по командам выше. Ошибка скачивания не обновляет уже работающий Pod Grafana.

## Формат событий

Backend выводит ECS JSON с полями `@timestamp`, `log.level`, `message` и `service.name`. При регистрации, входе, изменениях и поиске в личной коллекции, а также обращениях к OMDb он добавляет `event_name`, `outcome` и безопасные технические признаки. Пароли, ключ OMDb, заметки и поисковые запросы не журналируются. Для поиска сохраняются только область (`all` или `filtered`) и количество результатов. Access-логи Nginx содержат `timestamp`, `level`, `message`, `service.name`, HTTP-метод, путь, статус и время обработки запроса. Для HTTP 4xx Nginx записывает уровень `warn`, для 5xx — `error`.

Vector приводит время к полю `timestamp`, а уровень — к `level` в нижнем регистре, сохраняя сведения о Pod и контейнере. В VictoriaLogs время доступно как `_time`, сообщение — как `_msg`, сервис — как `service.name`. Backend использует имя `movie-journal`, frontend — `movie-journal-frontend`.

Поле `structured:true` обозначает исходные JSON-события приложения. Текстовые служебные сообщения и stderr тоже сохраняются, с `structured:false`; текстовым сообщениям stderr сборщик назначает `warn`. Полные тела запросов и заголовки авторизации в access-логи не включаются.

## Проверка поступления логов

Следующие запросы читают список записей, выполняют HEAD-запрос и обращаются к отсутствующему пути. Данные приложения не изменяются:

```shell
kubectl exec -n movie-journal deployment/backend -- curl -fsS --max-time 10 http://frontend/api/diary-entries
kubectl exec -n movie-journal deployment/backend -- curl -sS -I --max-time 10 http://frontend/
kubectl exec -n movie-journal deployment/backend -- curl -sS --max-time 10 -o /dev/null -w "HTTP=%{http_code}\n" http://frontend/logging-check-not-found
```

Последняя команда должна показать `HTTP=404`: это событие попадёт в выборку `level:warn`. `curl` здесь выполняется внутри Linux-контейнера, поэтому команды одинаковы для Bash и PowerShell.

В отдельном терминале откройте доступ к VictoriaLogs:

```shell
kubectl port-forward -n logging service/victorialogs 9428:9428 --address=127.0.0.1
```

Оставьте терминал открытым и перейдите в [vmui](http://127.0.0.1:9428/select/vmui/). Выберите последний час и выполняйте запросы по одному. Если события старше, расширьте и интервал интерфейса, и `_time` в запросе.

## Запросы LogsQL

Все четыре запроса также сохранены в [queries.logsql](queries.logsql).

**1. Последние JSON-события.** До 100 записей за 60 минут, новые сверху. Раскрытие записи показывает поля события и метаданные Kubernetes.

```logsql
_time:60m structured:true | sort by (_time) desc | limit 100
```

**2. Предупреждения frontend.** Фильтр по сервису и уровню, включая запрос к отсутствующему пути с HTTP 404.

```logsql
_time:60m structured:true service.name:movie-journal-frontend level:warn | sort by (_time) desc | limit 100 | fields _time, service.name, level, status, method, path, _msg
```

**3. Число событий по сервисам и уровням.** Позволяет сравнить объём логов backend и frontend и увидеть предупреждения.

```logsql
_time:60m structured:true | stats by (service.name, level) count() as logs
```

**4. Самые частые HTTP-методы.** До пяти методов по числу запросов frontend. После проверки выше ожидаются как минимум GET и HEAD.

```logsql
_time:60m structured:true service.name:movie-journal-frontend | top 5 by (method)
```

Для агрегатов удобно выбрать вкладку `JSON`; в режиме `Table` при необходимости добавьте столбцы результата в настройках таблицы.

Дополнительные конструкции и функции запросов описаны в [документации LogsQL](https://docs.victoriametrics.com/victorialogs/logsql/).

## Диагностика и обновление

```shell
kubectl logs -n logging deployment/victorialogs --tail=50
kubectl logs -n logging -l app=vector --tail=50 --prefix=true
kubectl get events -n logging --sort-by=.metadata.creationTimestamp
```

Если результат пустой, сначала повторите HTTP-запросы и проверьте выбранный интервал. Затем проверьте готовность Vector и ошибки отправки в его журнале. Для просмотра текстовых сообщений используйте `_time:60m structured:false`.

После изменения конфигурации Vector:

```shell
kubectl apply -f k8s/logging/vector.yml
kubectl rollout restart -n logging daemonset/vector
kubectl rollout status -n logging daemonset/vector --timeout=180s
```

Для завершения локального доступа нажмите `Ctrl+C` в терминале port-forward. При остановке Minikube PVC сохраняется; удаление PVC или профиля удалит данные логирования.
