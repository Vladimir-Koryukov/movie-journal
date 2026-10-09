# Учебные упражнения Kubernetes

Манифесты для знакомства с Pod, Deployment, Service, ConfigMap, Secret, Job, CronJob и DaemonSet. Эти ресурсы используются отдельно от приложения Movie Journal.

Для упражнений создаётся отдельное пространство имён:

```shell
kubectl create namespace k8s-practice
```

Манифесты применяются по одному из корня репозитория. Например:

```shell
kubectl apply -n k8s-practice -f k8s-practice/pod.yml
kubectl wait -n k8s-practice --for=condition=Ready pod/my-pod --timeout=120s
kubectl get pods -n k8s-practice
```

Перед `deployment.yml` нужно применить `configmap.yml` и `secret.yml`. Service `my-service` выбирает Pod Deployment по метке `app: my-app`. Конфигурация Nginx возвращает имя обслужившего запрос Pod.

`job.yml` создаёт успешную задачу, а `job-fail.yml` намеренно завершает контейнер с ошибкой для проверки повторных попыток. `cronjob.yml` запускает задачу раз в минуту. `daemonset.yml` запускает Pod на каждом подходящем Linux-узле. После проверки CronJob и DaemonSet удаляются командой `kubectl delete -n k8s-practice -f` с соответствующим файлом.

Значение Secret в примере учебное.
