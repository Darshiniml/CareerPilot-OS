# Shared helpers for the local (no-Docker) CareerPilot scripts. Windows PowerShell 5.1 compatible.
# 'Continue': native tools (java, npm, pip, pg_ctl) write progress to stderr, which Windows PowerShell
# would otherwise treat as fatal. Failures are detected through exit codes (Assert-Exit).
$ErrorActionPreference = 'Continue'

$Script:RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$Script:CpHome = Join-Path $env:LOCALAPPDATA 'careerpilot'
$Script:PgHome = Join-Path $CpHome 'pgsql'
$Script:PgData = Join-Path $CpHome 'pgdata'
$Script:PgBin = Join-Path $PgHome 'bin'
$Script:VenvDir = Join-Path $CpHome 'venv-ai'
$Script:BuildRoot = Join-Path $CpHome 'build'
$Script:LogDir = Join-Path $CpHome 'logs'
$Script:PidFile = Join-Path $CpHome 'pids.json'
$Script:EnvFile = Join-Path $RepoRoot '.env.local'
$Script:BackendJar = Join-Path $BuildRoot '_apps_backend\libs\backend.jar'

function New-Secret([int]$Bytes = 48) {
    $buffer = New-Object byte[] $Bytes
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($buffer)
    return [Convert]::ToBase64String($buffer)
}

function Import-EnvFile {
    if (-not (Test-Path $EnvFile)) { throw ".env.local not found. Run scripts\local\setup.ps1 first." }
    foreach ($line in Get-Content $EnvFile) {
        $trimmed = $line.Trim()
        if ($trimmed -eq '' -or $trimmed.StartsWith('#') -or -not $trimmed.Contains('=')) { continue }
        $idx = $trimmed.IndexOf('=')
        $name = $trimmed.Substring(0, $idx).Trim()
        $value = $trimmed.Substring($idx + 1).Trim()
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
}

function Test-PortOpen([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        $ok = $async.AsyncWaitHandle.WaitOne(500)
        if ($ok) { $client.EndConnect($async) }
        return $ok
    } catch { return $false } finally { $client.Close() }
}

function Wait-ForUrl([string]$Url, [int]$TimeoutSeconds = 180) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($r.StatusCode -lt 500) { return $true }
        } catch { Start-Sleep -Seconds 2 }
    }
    return $false
}

function Find-Python {
    foreach ($v in @('3.12', '3.13', '3.14', '3.11')) {
        try {
            $out = & py "-$v" -c "import sys; print(sys.executable)" 2>$null
            if ($LASTEXITCODE -eq 0 -and $out) { return @('py', "-$v") }
        } catch { }
    }
    throw 'Python 3.11+ not found (install it from python.org and enable the "py" launcher).'
}

function Assert-Exit([string]$What) {
    if ($LASTEXITCODE -ne 0) { throw "$What failed (exit code $LASTEXITCODE)" }
}

# pg_ctl's server process inherits the console handles, so piping pg_ctl output would block forever.
# Run it as a separate hidden process and wait only for pg_ctl itself.
function Start-PostgresServer {
    $pgArgs = @('-D', "`"$PgData`"", '-l', "`"$(Join-Path $LogDir 'postgres.log')`"", '-w', 'start')
    # -Wait would also wait for the server (a child process); wait for pg_ctl only.
    $p = Start-Process -FilePath (Join-Path $PgBin 'pg_ctl.exe') -ArgumentList $pgArgs -WindowStyle Hidden -PassThru
    $p.WaitForExit()
    if ($p.ExitCode -ne 0) { throw "Starting PostgreSQL failed (see $(Join-Path $LogDir 'postgres.log'))" }
}
