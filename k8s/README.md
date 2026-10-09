# Movie Journal: проверка на другом ноутбуке

Инструкция для Windows 11 x64 (или совместимой с Docker Desktop Windows 10), PowerShell и Docker Desktop с Linux-контейнерами. Команды выполняются в PowerShell на новом ноутбуке. Переходите к следующему блоку после успешного завершения предыдущего.

Порядок работы: установить инструменты → перенести проект → запустить Minikube → собрать образы → запустить базу и миграцию → запустить приложение → проверить интерфейс и масштабирование. После проверки используйте [отдельный сценарий видео](Сценарий-видео.md).

## 1. Что установить

| Инструмент | Для чего нужен |
| --- | --- |
| [Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install/) с WSL 2 и Linux-контейнерами | Запускает контейнер Minikube и собирает образы |
| [Minikube](https://minikube.sigs.k8s.io/docs/start/) | Создаёт локальный одноузловой Kubernetes |
| [kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl-windows/) | Применяет манифесты и проверяет ресурсы |
| PowerShell и браузер | Команды и интерфейс приложения |
| [OBS Studio](https://obsproject.com/download/) или уже установленная программа записи экрана | Запись терминала и браузера со звуком |

Java, Maven, Node.js и PostgreSQL отдельно не нужны: они находятся в образах. Git для запуска из переносимого ZIP тоже не нужен.

Практический ориентир для этого проекта: 16 ГБ RAM на ноутбуке, около 30 ГБ свободного места и доступ в Интернет. Кластеру ниже выделяется 4 ГБ RAM и 2 CPU. Минимальные требования Docker Desktop и поддерживаемую Windows проверьте на странице установки выше.

Если WSL ещё не установлен, откройте PowerShell от администратора. Установка без отдельного Linux-дистрибутива поддерживается [командами WSL](https://learn.microsoft.com/en-us/windows/wsl/basic-commands):

```powershell
wsl --install --no-distribution
```

Перезагрузите Windows при запросе. Если WSL уже есть, достаточно проверить и при необходимости обновить его:

```powershell
wsl --version
wsl --update
```

Установите и запустите Docker Desktop, выберите WSL 2 и Linux-контейнеры. Встроенный Kubernetes Docker Desktop для этой инструкции включать не требуется: кластер создаст Minikube.

Установите Minikube; kubectl устанавливайте, если его ещё нет или его версия не подходит кластеру:

```powershell
winget install -e --id Kubernetes.minikube
winget install -e --id Kubernetes.kubectl
```

Если `winget` отсутствует, используйте установщики из официальных ссылок в таблице. Закройте терминал и откройте новый, чтобы обновился PATH. Проверьте:

```powershell
docker version
docker info --format '{{.OSType}}'
minikube version
kubectl version --client
```

У Docker должны быть разделы Client и Server; тип ОС — `linux`. Docker Desktop иногда поставляет собственный kubectl: команда `Get-Command kubectl -All` покажет найденные версии. Допустимое различие kubectl и Kubernetes — не более одной минорной версии; это описано в [документации kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl-windows/).

## 2. Перенести проект

Распакуйте архив проекта или склонируйте репозиторий так, чтобы корень проекта находился, например, в `C:\Projects\movie-journal`. В нём должны быть папки `backend`, `frontend`, `k8s` и файл `README.md`. Если переносите папку вручную, обязательно включите скрытую папку `backend\.mvn` и все файлы `k8s`.

Архив и репозиторий содержат исходники и манифесты, но не Docker-образы и не данные PostgreSQL. На новом ноутбуке база первоначально будет пустой. Папка `k8s-practice` содержит пройденные упражнения; для запуска приложения она не нужна.

```powershell
Set-Location C:\Projects\movie-journal
Get-ChildItem .\k8s
```

Для Kubernetes используются настройки из `k8s\config.yml`: ConfigMap и Secret с учебным паролем. Создавать `.env` или запускать Docker Compose здесь не нужно.

## 3. Создать кластер

Docker Desktop должен работать. Выполните в новом окне PowerShell:

```powershell
minikube start -p minikube --driver=docker --container-runtime=docker --cpus=2 --memory=4096
kubectl config use-context minikube
minikube status -p minikube
kubectl get nodes
kubectl get storageclass
```

Ожидается один узел `minikube` со статусом `Ready`, а у класса хранилища `standard` — отметка `default`. Если класса нет, включите стандартные дополнения и проверьте повторно:

```powershell
minikube -p minikube addons enable storage-provisioner
minikube -p minikube addons enable default-storageclass
kubectl get storageclass
```

Драйвер Docker запускает сам узел Kubernetes, а `--container-runtime=docker` задаёт среду контейнеров внутри узла и позволяет использовать `docker-env` на следующем шаге.

## 4. Собрать и подготовить образы

Переключите Docker CLI этого окна на Docker внутри Minikube. Это PowerShell-эквивалент `eval $(minikube docker-env)` из задания. [Описание docker-env](https://minikube.sigs.k8s.io/docs/handbook/pushing/#1-pushing-directly-to-the-in-cluster-docker-daemon-docker-env).

```powershell
& minikube -p minikube docker-env --shell powershell | Invoke-Expression
$env:MINIKUBE_ACTIVE_DOCKERD
```

Последняя команда должна вывести `minikube`. В этом же окне, из корня проекта, выполните команды **по одной**:

```powershell
docker build --progress=plain -t movie-journal-backend:0.2.0 ./backend
```

Дождитесь успешной сборки: она включает Maven-тесты. При `BUILD FAILURE` или `ERROR` сначала разберите ошибку; наличие приглашения терминала ещё не означает успех.

```powershell
docker build --progress=plain -t movie-journal-frontend:0.2.0 ./frontend
```

```powershell
docker pull postgres:18-alpine
docker image ls --filter 'reference=movie-journal-*'
```

Первый запуск может долго скачивать базовые образы и зависимости. Всё это сделайте до записи видео.

После сборки через `docker-env` образы уже доступны кластеру. Чтобы отдельно выполнить предусмотренную заданием загрузку через `minikube image load`, сохраните оба образа приложения в архив, верните CLI к Docker Desktop и загрузите архив:

```powershell
$journalImageArchive = Join-Path $env:TEMP 'movie-journal-images.tar'
docker image save -o $journalImageArchive movie-journal-backend:0.2.0 movie-journal-frontend:0.2.0
```

После успешного сохранения:

```powershell
& minikube -p minikube docker-env --unset --shell powershell | Invoke-Expression
minikube -p minikube image load $journalImageArchive
minikube -p minikube image ls | Select-String 'movie-journal|postgres'
```

Должны присутствовать backend и frontend с тегом `0.2.0`, а также `postgres:18-alpine`. Для последующих запусков без изменения исходников повторная сборка не нужна. Команда `image load` поддерживает архивы; см. [справку Minikube](https://minikube.sigs.k8s.io/docs/commands/image/).

Если сборка внутри Minikube упирается в сетевую ошибку, доступен короткий запасной путь: в новом PowerShell собрать те же два образа обычным Docker Desktop, затем выполнить `minikube -p minikube image load --daemon movie-journal-backend:0.2.0` и такую же команду для frontend. Это перенос готовых образов; ошибку сборки или провал тестов он сам по себе не исправляет.

## 5. Запустить PostgreSQL

```powershell
kubectl config use-context minikube
kubectl apply -f .\k8s\namespace.yml
kubectl apply -f .\k8s\config.yml
kubectl apply -f .\k8s\postgres.yml
kubectl rollout status -n movie-journal statefulset/db --timeout=180s
kubectl get pods,services,pvc -n movie-journal
```

Ожидается `db-0` — `1/1 Running`, PVC `data-db-0` — `Bound`. Service `db` с `CLUSTER-IP: None` — ожидаемый headless Service для StatefulSet.

## 6. Выполнить миграцию базы

```powershell
kubectl apply -f .\k8s\migrate.yml
kubectl wait -n movie-journal --for=condition=Complete job/migrate --timeout=240s
kubectl logs -n movie-journal job/migrate --tail=20
```

Ожидается успешное выполнение Flyway, Job — `Complete`, его Pod — `Completed`. Для завершённого Pod значение `0/1` нормально. Backend запускайте только после успешной миграции: он сам её не выполняет.

Повторный `apply` уже завершённого Job не запускает его ещё раз. Если нужен повтор, сначала выполните `kubectl delete job migrate -n movie-journal --ignore-not-found`, затем этот блок. Flyway применит только отсутствующие миграции.

## 7. Запустить backend и frontend

```powershell
kubectl apply -f .\k8s\backend.yml
kubectl rollout status -n movie-journal deployment/backend --timeout=240s
```

После готовности backend:

```powershell
kubectl apply -f .\k8s\frontend-config.yml
kubectl apply -f .\k8s\frontend.yml
kubectl rollout status -n movie-journal deployment/frontend --timeout=180s
kubectl get pods,services,jobs,pvc -n movie-journal
```

Должны работать `db-0`, backend и frontend; миграция завершена. Файл `frontend-config.yml` передаёт Nginx настройки для Kubernetes. Не заменяйте его конфигурацией Nginx из папки `frontend`, которая предназначена для Compose.

## 8. Открыть приложение и проверить операции

Откройте **второе окно PowerShell**:

```powershell
kubectl --context=minikube port-forward -n movie-journal service/frontend 18080:80 --address=127.0.0.1
```

Оставьте это окно открытым. Сообщение `Forwarding from 127.0.0.1:18080 -> 80` означает, что можно открыть [http://127.0.0.1:18080](http://127.0.0.1:18080) в браузере на этом же ноутбуке. Команда продолжает работать, пока её не остановить `Ctrl+C`.

В первом окне проверьте состояние и API:

```powershell
Invoke-RestMethod http://127.0.0.1:18080/actuator/health
Invoke-RestMethod http://127.0.0.1:18080/api/diary-entries | ConvertTo-Json -Depth 5
```

В health ожидается `status: UP`. Пустой список API в PowerShell может не вывести ничего — это нормально для новой базы.

В интерфейсе:

1. Добавьте фильм «Проверка Kubernetes», год 2024, статус «Планирую посмотреть».
2. Отредактируйте его: статус «Просмотрен», оценка 9, заметка «Запуск в Minikube».
3. Обновите страницу и проверьте, что запись сохранилась.
4. Переключите фильтр статуса. Оставьте запись для следующей проверки.

Запрос проходит через frontend/Nginx, Service `backend:8080` и один из backend Pod; backend обращается к PostgreSQL через `db:5432`.

## 9. Проверить масштабирование

```powershell
kubectl scale -n movie-journal deployment/backend --replicas=2
kubectl rollout status -n movie-journal deployment/backend --timeout=240s
kubectl get deployment backend -n movie-journal
kubectl get pods -n movie-journal -l app=backend -o wide
kubectl get endpointslices -n movie-journal -l kubernetes.io/service-name=backend -o wide
```

Ожидается `2/2` готовых реплик и два адреса Pod в EndpointSlice. Обновите интерфейс: запись должна остаться доступной. Обе реплики используют одну базу.

В `backend.yml` намеренно оставлено `replicas: 1`, чтобы на видео можно было показать увеличение до двух. Повторный `apply` этого файла вернёт желаемое число реплик к одному.

## 10. Проверить сохранение данных после пересоздания Pod базы

Проверка вызывает короткую недоступность базы. Удаляется только Pod; PVC и запись должны сохраниться.

```powershell
kubectl delete pod db-0 -n movie-journal
kubectl wait -n movie-journal --for=create pod/db-0 --timeout=60s
kubectl wait -n movie-journal --for=condition=Ready pod/db-0 --timeout=180s
kubectl wait -n movie-journal --for=condition=Ready pod -l app=backend --timeout=180s
kubectl get pods,pvc -n movie-journal
```

Обновите браузер и убедитесь, что фильм сохранился. Это проверка хранения на PVC при замене Pod, а не резервного копирования при потере всего кластера.

## 11. Если что-то не запустилось

Сначала сохраните фактическую ошибку и выполните:

```powershell
kubectl get pods -n movie-journal
kubectl get events -n movie-journal --sort-by=.metadata.creationTimestamp
kubectl logs -n movie-journal deployment/backend --tail=60
kubectl logs -n movie-journal job/migrate --tail=60
```

| Симптом | Что проверить |
| --- | --- |
| `ImagePullBackOff` | Есть ли нужный образ и тег в `minikube image ls`; загрузить недостающий локальный образ |
| `Pending` у db | `kubectl get pvc -n movie-journal` и наличие default StorageClass |
| Job миграции завершился ошибкой | Логи Job; готова ли БД; настройки ConfigMap/Secret |
| Backend не готов | Логи backend; завершилась ли миграция; доступна ли БД |
| Браузер не открывает страницу | Работает ли port-forward; использовать именно порт 18080 |
| После пересоздания frontend исчез доступ | Снова запустить port-forward |

Изменение пароля в Secret после создания базы не меняет пароль пользователя PostgreSQL автоматически. Для повторной проверки используйте прежние согласованные настройки.

## 12. Остановка и повторный запуск

Остановите port-forward через `Ctrl+C`, затем:

```powershell
minikube stop -p minikube
```

Для продолжения запустите Docker Desktop и выполните:

```powershell
minikube start -p minikube
kubectl config use-context minikube
kubectl get pods -n movie-journal
```

Дождитесь готовности приложения и снова включите port-forward. `minikube stop` сохраняет кластер; удаление профиля Minikube, namespace или PVC для обычной остановки не требуется.
