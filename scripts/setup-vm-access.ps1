[CmdletBinding()]
param(
    [string]$VmName = "Docker-Ubuntu",
    [string]$SshUser = "vladimir",
    [string]$SshHost = "127.0.0.1",

    [ValidateRange(1, 65535)]
    [int]$SshPort = 2222,

    [ValidateRange(30, 900)]
    [int]$VmStartupTimeoutSeconds = 180,

    [string]$SshKeyPath = (Join-Path $env:USERPROFILE ".ssh\movie-journal-vm-ed25519")
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$sshDirectory = Split-Path -Parent $SshKeyPath
$publicKeyPath = "$SshKeyPath.pub"

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

function Test-TcpPort {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connection = $client.BeginConnect($SshHost, $SshPort, $null, $null)
        if (-not $connection.AsyncWaitHandle.WaitOne(1000, $false)) {
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

$vboxManage = Get-VBoxManagePath
$machineInfo = & $vboxManage showvminfo $VmName --machinereadable 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "Virtual machine '$VmName' was not found."
}

$stateLine = $machineInfo | Where-Object { $_ -match '^VMState="(?<state>[^"]+)"$' } | Select-Object -First 1
if (-not $stateLine) {
    throw "Could not determine the state of virtual machine '$VmName'."
}

if ($Matches.state -ne "running") {
    Write-Host "Starting virtual machine '$VmName' in headless mode..."
    & $vboxManage startvm $VmName --type headless
    if ($LASTEXITCODE -ne 0) {
        throw "VirtualBox could not start virtual machine '$VmName'."
    }
}

$deadline = (Get-Date).AddSeconds($VmStartupTimeoutSeconds)
Write-Host "Waiting for SSH on ${SshHost}:$SshPort ..."
while (-not (Test-TcpPort)) {
    if ((Get-Date) -ge $deadline) {
        throw "SSH did not become available within $VmStartupTimeoutSeconds seconds."
    }

    Start-Sleep -Seconds 2
}
Write-Host "SSH is available."

if (-not (Test-Path -LiteralPath $sshDirectory)) {
    New-Item -ItemType Directory -Path $sshDirectory | Out-Null
}

if (-not (Test-Path -LiteralPath $SshKeyPath)) {
    Write-Host "Creating a dedicated SSH key for Movie Journal."
    Write-Host "When asked for a passphrase, press Enter twice to keep startup passwordless."

    & ssh-keygen.exe -t ed25519 -f $SshKeyPath -C "movie-journal-vm"
    if ($LASTEXITCODE -ne 0) {
        throw "ssh-keygen exited with code $LASTEXITCODE."
    }
}

if (-not (Test-Path -LiteralPath $publicKeyPath)) {
    throw "Public key '$publicKeyPath' was not found."
}

$publicKey = (Get-Content -LiteralPath $publicKeyPath -Raw).Trim()
$publicKeyBase64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($publicKey))

$remoteScript = @'
set -eu
umask 077
mkdir -p "$HOME/.ssh"
touch "$HOME/.ssh/authorized_keys"
PUBLIC_KEY="$(printf '%s' '__PUBLIC_KEY_BASE64__' | base64 -d)"
if ! grep -qxF "$PUBLIC_KEY" "$HOME/.ssh/authorized_keys"; then
    printf '%s\n' "$PUBLIC_KEY" >> "$HOME/.ssh/authorized_keys"
fi
chmod 700 "$HOME/.ssh"
chmod 600 "$HOME/.ssh/authorized_keys"
sudo systemctl enable --now docker
'@

$remoteScript = $remoteScript.Replace("__PUBLIC_KEY_BASE64__", $publicKeyBase64)
$remoteScriptBytes = [System.Text.Encoding]::UTF8.GetBytes($remoteScript)
$remoteScriptBase64 = [Convert]::ToBase64String($remoteScriptBytes)
$remoteCommand = "printf '%s' '$remoteScriptBase64' | base64 -d | bash"

Write-Host "Installing the public key and enabling Docker autostart."
Write-Host "Enter the VM password for SSH and sudo. This is the last required password-based setup."

& ssh.exe -t -p $SshPort "${SshUser}@${SshHost}" $remoteCommand
if ($LASTEXITCODE -ne 0) {
    throw "SSH setup exited with code $LASTEXITCODE."
}

Write-Host "Checking key authentication..."
& ssh.exe `
    -p $SshPort `
    -i $SshKeyPath `
    -o "IdentitiesOnly=yes" `
    -o "BatchMode=yes" `
    "${SshUser}@${SshHost}" `
    "printf 'SSH key authentication: OK\n'"

if ($LASTEXITCODE -ne 0) {
    throw "The key was installed, but passwordless authentication could not be verified."
}

Write-Host "Setup is complete. Future application starts will use the SSH key."
