# Метрики Movie Journal

Backend публикует OpenMetrics на отдельном management-порту `9090`. Этот порт доступен внутри кластера через ClusterIP Service, но Nginx не передаёт на него `/actuator/prometheus`. PostgreSQL exporter работает sidecar-контейнером рядом с PostgreSQL и публикует метрики на `9187`. `vmagent` находит оба endpoint по аннотациям Pod и записывает данные в VictoriaMetrics.

## Состав

- Spring Boot Actuator и Micrometer Prometheus registry: HTTP, JVM, процесс и пул соединений.
- Бизнес-метрики регистрации, входа, изменений дневника и запросов к OMDb.
- `postgres_exporter` `0.20.1`: состояние PostgreSQL, соединения, транзакции и таблицы.
- Blackbox Exporter `0.28.0`: синтетическая end-to-end проверка пользовательского HTTP-маршрута.
- VictoriaMetrics K8s Stack `0.95.0`: VMSingle, VMAgent и VictoriaMetrics Operator.
- Grafana: datasource VictoriaMetrics, dashboard `Movie Journal metrics` и отдельный dashboard `Movie Journal — SLI, SLO и SLA`.

Названия пользователей, фильмов, поисковые строки и другие неограниченные значения не используются как labels.

## Бизнес-метрики

| Метрика | Тип | Labels | Назначение |
| --- | --- | --- | --- |
| `movie_journal_user_registrations_total` | Counter | `outcome`, `reason` | Попытки регистрации и причины отказа |
| `movie_journal_user_logins_total` | Counter | `outcome` | Успешные и неуспешные попытки входа |
| `movie_journal_diary_entries_added_total` | Counter | `source`, `status` | Добавленные записи дневника из OMDb и вручную |
| `movie_journal_diary_catalog_imports_rejected_total` | Counter | `provider`, `reason` | Отклонённые импорты фильма, включая повторное добавление |
| `movie_journal_diary_entries_updated_total` | Counter | `outcome`, `status` | Результаты изменения записей дневника |
| `movie_journal_diary_entries_deleted_total` | Counter | `outcome` | Результаты удаления записей дневника |
| `movie_journal_diary_searches_total` | Counter | `scope`, `outcome` | Поиски и фильтрации в личной коллекции |
| `movie_journal_diary_search_results_movies` | DistributionSummary | `scope`, `outcome` | Количество фильмов в результатах поиска по коллекции |
| `movie_journal_catalog_searches_total` | Counter | `provider`, `outcome` | Результаты поисковых запросов к каталогу |
| `movie_journal_catalog_details_total` | Counter | `provider`, `outcome` | Результаты загрузки подробной информации о фильме |
| `movie_journal_catalog_search_results_movies` | DistributionSummary | `provider`, `outcome` | Количество фильмов в результатах поиска |
| `movie_journal_catalog_request_duration_seconds` | Histogram | `provider`, `operation`, `outcome` | Длительность запросов к внешнему каталогу |

Набор labels ограничен заранее известными значениями. Это сохраняет контролируемую кардинальность временных рядов.

## Установка VictoriaMetrics

Требуются Helm 3, kubectl и работающий кластер Minikube. Команды выполняются из корня проекта после запуска приложения:

```shell
helm repo add vm https://victoriametrics.github.io/helm-charts/
helm repo update
kubectl create namespace monitoring --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f k8s/monitoring/blackbox-exporter.yml
kubectl rollout status -n monitoring deployment/blackbox-exporter --timeout=240s
helm upgrade --install movie-journal-metrics vm/victoria-metrics-k8s-stack --version 0.95.0 --namespace monitoring --values k8s/monitoring/values.yml
kubectl rollout status -n monitoring deployment/blackbox-probe-pusher --timeout=240s
```

Проверьте компоненты и дождитесь готовности VMSingle и VMAgent:

```shell
helm list -n monitoring
kubectl get pods,pvc -n monitoring
kubectl wait -n monitoring --for=condition=Ready pod -l app.kubernetes.io/name=vmsingle --timeout=300s
kubectl wait -n monitoring --for=condition=Ready pod -l app.kubernetes.io/name=vmagent --timeout=300s
```

## Обновление приложения и exporter

После сборки нового backend-образа примените манифесты:

```shell
kubectl apply -f k8s/backend.yml
kubectl rollout status -n movie-journal deployment/backend --timeout=240s
kubectl apply -f k8s/postgres.yml
kubectl rollout status -n movie-journal statefulset/db --timeout=240s
kubectl get pods -n movie-journal
```

Pod backend должен быть `1/1 Running`, а `db-0` — `2/2 Running`: PostgreSQL и exporter.

Проверка endpoint без публикации порта наружу:

```shell
kubectl exec -n movie-journal deployment/backend -- curl -fsS http://127.0.0.1:9090/actuator/prometheus
kubectl port-forward -n movie-journal pod/db-0 9187:9187 --address=127.0.0.1
```

Во втором терминале для exporter:

```shell
curl http://127.0.0.1:9187/metrics
```

## Проверка сбора

Определите Service VMSingle и оставьте port-forward запущенным:

```bash
VMSINGLE_SERVICE=$(kubectl get service -n monitoring -l app.kubernetes.io/name=vmsingle -o jsonpath='{.items[0].metadata.name}')
kubectl port-forward -n monitoring "service/$VMSINGLE_SERVICE" 8428:8428 --address=127.0.0.1
```

В другом терминале:

```shell
curl --get http://127.0.0.1:8428/api/v1/query --data-urlencode "query=up{job=~\"backend|db\"}"
curl --get http://127.0.0.1:8428/api/v1/query --data-urlencode "query=movie_journal_user_logins_total"
curl --get http://127.0.0.1:8428/api/v1/query --data-urlencode "query=pg_up"
curl http://127.0.0.1:8428/api/v1/label/__name__/values
```

Дополнительные выражения находятся в `queries.promql`.

Для проверки обнаруженных целей определите Service VMAgent и временно откройте его служебный порт:

```bash
VMAGENT_SERVICE=$(kubectl get service -n monitoring -l app.kubernetes.io/name=vmagent -o jsonpath='{.items[0].metadata.name}')
kubectl port-forward -n monitoring "service/$VMAGENT_SERVICE" 8429:8429 --address=127.0.0.1
```

В другом терминале запросите состояние целей:

```shell
curl http://127.0.0.1:8429/targets
```

В списке должны присутствовать готовые цели `backend` на порту `9090` и `db` на порту `9187`.

## SLI, SLO и SLA

Три основных SLI относятся к пользовательским бизнес-операциям. Формальное окно расчёта — скользящие 30 дней. В dashboard можно выбрать короткое окно `5m` для демонстрации реакции графиков.

| Индикатор | Формула SLI | SLO | SLA |
| --- | --- | --- | --- |
| Доступность личной коллекции | доля поисков коллекции с `outcome != error` | не менее 99,5% | не менее 99% |
| Доступность авторизации | доля попыток входа с `outcome != error` | не менее 99% | не менее 98% |
| Доступность поиска OMDb | доля поисков каталога с `outcome != error` | не менее 95% | не менее 90% |

Пустой результат поиска является корректным бизнес-ответом и не уменьшает доступность коллекции или OMDb. Неверные учётные данные также являются корректно обработанным результатом авторизации. SLI уменьшается только при `outcome="error"`, когда пользовательская операция не может быть оказана из-за технической ошибки.

При SLO 99,5% бюджет ошибок личной коллекции равен 0,5% операций за 30-дневное окно. Более мягкий SLA 99% допускает до 1% неуспешных операций. Готовые формулы находятся в [`slo.promql`](slo.promql).

Blackbox Exporter остаётся дополнительным техническим сигналом и не входит в три обязательных бизнес-SLI. Небольшой контейнер `blackbox-probe-pusher` раз в 15 секунд запрашивает probe и передаёт OpenMetrics-ответ через Prometheus import endpoint VMAgent. Проверяется маршрут `frontend -> /actuator/health -> backend`, то есть независимо обнаруживается полный отказ, при котором приложение уже не способно опубликовать собственные бизнес-метрики. Kubernetes liveness/readiness probes решают другую задачу: они управляют жизненным циклом Pod и не заменяют независимую синтетическую проверку.

Проверка prober напрямую из кластера:

```shell
kubectl exec -n movie-journal deployment/backend -- curl -fsS --get \
  --data-urlencode 'module=movie_journal_health' \
  --data-urlencode 'target=http://frontend.movie-journal.svc.cluster.local/actuator/health' \
  http://blackbox-exporter.monitoring.svc.cluster.local:9115/probe
```

После запуска подождите 30–45 секунд: VictoriaMetrics использует небольшой latency offset для незавершённого временного интервала. Затем должны появиться `probe_success`, `probe_duration_seconds` и `probe_http_status_code` с `job="movie-journal-http-probe"`.

## Grafana

Обновите существующую Grafana из набора централизованных логов:

```shell
kubectl apply -f k8s/monitoring/grafana-slo-dashboard.yml
kubectl apply -f k8s/logging/grafana.yml
kubectl rollout restart -n logging deployment/grafana
kubectl rollout status -n logging deployment/grafana --timeout=300s
kubectl port-forward -n logging service/grafana 3000:3000 --address=127.0.0.1
```

Откройте <http://127.0.0.1:3000>, войдите как `admin` / `admin` и выберите dashboard `Movie Journal metrics` или `Movie Journal — SLI, SLO и SLA` в папке `Movie Journal`. На SLO-dashboard линии фактических значений сопоставлены с порогами SLO и SLA, а переменная `Расчётное окно` переключает период наблюдения. Datasource `VictoriaLogs` остаётся доступен для логов, а `VictoriaMetrics` используется для метрик и SLI.

## Удаление

```shell
helm uninstall movie-journal-metrics -n monitoring
kubectl delete -f k8s/monitoring/blackbox-exporter.yml
```

PVC с метриками и CRD оператора удаляйте отдельно только тогда, когда накопленные данные больше не нужны.
