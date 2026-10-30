[CmdletBinding()]
param(
    [string]$VmName = "Docker-Ubuntu",
    [string]$SshUser = "vladimir",
    [string]$SshHost = "127.0.0.1",

    [ValidateRange(1, 65535)]
    [int]$SshPort = 2222,

    [ValidateRange(30, 900)]
    [int]$VmStartupTimeoutSeconds = 180,

    [string]$SshKeyPath = (Join-Path $env:USERPROFILE ".ssh\movie-journal-vm-ed25519"),

    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$forwardedPorts = [ordered]@{
    "Movie Journal"   = 18080
    "Grafana"         = 3000
    "VictoriaMetrics" = 8428
    "VMAgent"         = 8429
}

function Get-VBoxManagePath {
    $candidates = @(
        (Join-Path $env:ProgramFiles "Oracle\VirtualBox\VBoxManage.exe"),
        (Join-Path $env:ProgramFiles "VirtualBox\VBoxManage.exe")
    )

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }

    throw "VBoxManage.exe was not found. Install Oracle VirtualBox or start the VM manually."
}

function Get-VmState {
    param(
        [Parameter(Mandatory)]
        [string]$VBoxManagePath
    )

    $machineInfo = & $VBoxManagePath showvminfo $VmName --machinereadable 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Virtual machine '$VmName' was not found. Available VMs: $(& $VBoxManagePath list vms)"
    }

    $stateLine = $machineInfo | Where-Object { $_ -match '^VMState="(?<state>[^"]+)"$' } | Select-Object -First 1
    if (-not $stateLine) {
        throw "Could not determine the state of virtual machine '$VmName'."
    }

    return $Matches.state
}

function Test-TcpPort {
    param(
        [Parameter(Mandatory)]
        [string]$HostName,

        [Parameter(Mandatory)]
        [int]$Port,

        [int]$TimeoutMilliseconds = 1000
    )

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connection = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $connection.AsyncWaitHandle.WaitOne($TimeoutMilliseconds, $false)) {
            return $false
        }

        $client.EndConnect($connection)
        return $true
    }
    catch {
        return $false
    }
    finally {
        $client.Dispose()
    }
}

function Wait-ForSsh {
    $deadline = (Get-Date).AddSeconds($VmStartupTimeoutSeconds)

    Write-Host "Waiting for SSH on ${SshHost}:$SshPort ..."
    while ((Get-Date) -lt $deadline) {
        if (Test-TcpPort -HostName $SshHost -Port $SshPort) {
            Write-Host "SSH is available."
            return
        }

        Start-Sleep -Seconds 2
    }

    throw "SSH did not become available within $VmStartupTimeoutSeconds seconds. Check the VirtualBox NAT forwarding rule for port $SshPort."
}

function Assert-LocalPortsAvailable {
    foreach ($entry in $forwardedPorts.GetEnumerator()) {
        $listener = Get-NetTCPConnection -State Listen -LocalPort $entry.Value -ErrorAction SilentlyContinue
        if ($listener) {
            throw "Local port $($entry.Value) for '$($entry.Key)' is already in use. Close the old SSH/port-forward window and try again."
        }
    }
}

$vboxManage = Get-VBoxManagePath
$vmState = Get-VmState -VBoxManagePath $vboxManage

Write-Host "VM: $VmName ($vmState)"
Write-Host "SSH: ${SshUser}@${SshHost}:$SshPort"

if ($DryRun) {
    Write-Host "Dry run: the VM and cluster were not changed. A normal run will expose:"
    foreach ($entry in $forwardedPorts.GetEnumerator()) {
        Write-Host "  $($entry.Key): http://127.0.0.1:$($entry.Value)"
    }
    exit 0
}

Assert-LocalPortsAvailable

if ($vmState -ne "running") {
    Write-Host "Starting virtual machine '$VmName' in headless mode..."
    & $vboxManage startvm $VmName --type headless
    if ($LASTEXITCODE -ne 0) {
        throw "VirtualBox could not start virtual machine '$VmName'."
    }
}

Wait-ForSsh

$remoteScript = @'
set -Eeuo pipefail

export PATH="$HOME/.local/bin:$PATH"

PF_PIDS=()

cleanup() {
    if ((${#PF_PIDS[@]})); then
        kill "${PF_PIDS[@]}" 2>/dev/null || true
        wait "${PF_PIDS[@]}" 2>/dev/null || true
    fi

    rm -f /tmp/start-movie-journal.sh
}

trap cleanup EXIT INT TERM

start_forward() {
    local namespace="$1"
    local resource="$2"
    local mapping="$3"
    local log_file="$4"

    kubectl port-forward -n "$namespace" "$resource" "$mapping" \
        --address=127.0.0.1 >"$log_file" 2>&1 &
    PF_PIDS+=("$!")
}

check_forward() {
    local index="$1"
    local name="$2"
    local log_file="$3"
    local pid="${PF_PIDS[$index]}"

    if ! kill -0 "$pid" 2>/dev/null; then
        echo "Port-forward '$name' failed:"
        cat "$log_file"
        return 1
    fi
}

wait_for_url() {
    local name="$1"
    local url="$2"
    local timeout_seconds="${3:-120}"
    local deadline=$((SECONDS + timeout_seconds))

    while ((SECONDS < deadline)); do
        if curl -fsS --max-time 15 "$url" >/dev/null 2>&1; then
            echo "  $name is ready."
            return 0
        fi

        sleep 3
    done

    echo "  $name did not become ready within ${timeout_seconds}s: $url" >&2
    return 1
}

echo "[1/5] Starting Docker..."
if ! systemctl is-active --quiet docker; then
    sudo systemctl start docker
fi

echo "[2/5] Starting Minikube..."
if ! minikube -p minikube status >/dev/null 2>&1; then
    minikube -p minikube start --driver=docker
fi
kubectl config use-context minikube >/dev/null

echo "[3/5] Waiting for application components..."
kubectl rollout status -n movie-journal statefulset/db --timeout=300s
kubectl rollout status -n movie-journal deployment/backend --timeout=300s
kubectl rollout status -n movie-journal deployment/frontend --timeout=300s
kubectl rollout status -n logging deployment/grafana --timeout=300s

if kubectl get pods -n monitoring --no-headers 2>/dev/null | grep -q .; then
    kubectl wait -n monitoring --for=condition=Ready pod --all --timeout=300s
fi

echo "[4/5] Starting port-forward processes..."
pkill -f '^kubectl port-forward' 2>/dev/null || true

VMSINGLE_SERVICE="$(kubectl get service -n monitoring \
    -l app.kubernetes.io/name=vmsingle \
    -o jsonpath='{.items[0].metadata.name}')"
VMAGENT_SERVICE="$(kubectl get service -n monitoring \
    -l app.kubernetes.io/name=vmagent \
    -o jsonpath='{.items[0].metadata.name}')"

start_forward movie-journal service/frontend 18080:80 /tmp/pf-frontend.log
start_forward logging service/grafana 3000:3000 /tmp/pf-grafana.log
start_forward monitoring "service/$VMSINGLE_SERVICE" 8428:8428 /tmp/pf-vmsingle.log
start_forward monitoring "service/$VMAGENT_SERVICE" 8429:8429 /tmp/pf-vmagent.log

sleep 3
check_forward 0 "frontend" /tmp/pf-frontend.log
check_forward 1 "grafana" /tmp/pf-grafana.log
check_forward 2 "vmsingle" /tmp/pf-vmsingle.log
check_forward 3 "vmagent" /tmp/pf-vmagent.log

echo "[5/5] Verifying endpoints..."
wait_for_url "Movie Journal" http://127.0.0.1:18080/actuator/health 120
wait_for_url "Grafana" http://127.0.0.1:3000/api/health 120
wait_for_url "VictoriaMetrics" http://127.0.0.1:8428/-/healthy 120

cat <<'MESSAGE'

Movie Journal is ready:
  Application:     http://127.0.0.1:18080
  Grafana:         http://127.0.0.1:3000
  VictoriaMetrics: http://127.0.0.1:8428
  VMAgent:         http://127.0.0.1:8429/targets

Keep this window open. Type 'exit' to stop port forwarding and close SSH.
MESSAGE

bash -l
'@

$remoteBytes = [System.Text.Encoding]::UTF8.GetBytes($remoteScript)
$remoteBase64 = [Convert]::ToBase64String($remoteBytes)
$remoteCommand = "printf '%s' '$remoteBase64' | base64 -d > /tmp/start-movie-journal.sh && chmod 700 /tmp/start-movie-journal.sh && /tmp/start-movie-journal.sh"

$sshArguments = @(
    "-p", $SshPort,
    "-o", "ExitOnForwardFailure=yes",
    "-o", "ServerAliveInterval=30",
    "-o", "ServerAliveCountMax=3",
    "-t"
)

foreach ($port in $forwardedPorts.Values) {
    $sshArguments += @("-L", "127.0.0.1:${port}:127.0.0.1:${port}")
}

if (Test-Path -LiteralPath $SshKeyPath) {
    Write-Host "Using SSH key: $SshKeyPath"
    $sshArguments += @("-i", $SshKeyPath, "-o", "IdentitiesOnly=yes")
}
else {
    Write-Host "SSH key was not found. Password authentication will be used."
    Write-Host "Run scripts\setup-vm-access.ps1 once to configure passwordless SSH."
}

$sshArguments += @("${SshUser}@${SshHost}", $remoteCommand)

Write-Host "Connecting to the VM. A password is requested only when key access or Docker autostart is not configured."
& ssh.exe @sshArguments

if ($LASTEXITCODE -ne 0) {
    throw "The SSH session exited with code $LASTEXITCODE."
}
