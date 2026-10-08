# Start PostgreSQL, the AI service, the backend and the frontend locally (no Docker).
#   powershell -ExecutionPolicy Bypass -File scripts\local\start.ps1
. (Join-Path $PSScriptRoot 'common.ps1')
Import-EnvFile
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$pids = @{}

if (-not (Test-PortOpen 5432)) {
    Write-Host 'Starting PostgreSQL...'
    Start-PostgresServer
}

if (-not (Test-PortOpen 8000)) {
    Write-Host 'Starting AI service on http://localhost:8000 ...'
    $env:QDRANT_PATH = Join-Path $CpHome 'qdrant'
    $p = Start-Process -FilePath (Join-Path $VenvDir 'Scripts\python.exe') `
        -ArgumentList '-m', 'uvicorn', 'app.main:app', '--host', '127.0.0.1', '--port', '8000' `
        -WorkingDirectory (Join-Path $RepoRoot 'apps\ai-service') -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $LogDir 'ai-service.log') -RedirectStandardError (Join-Path $LogDir 'ai-service.err.log')
    $pids['ai-service'] = $p.Id
}

if (-not (Test-PortOpen 8080)) {
    Write-Host 'Starting backend on http://localhost:8080 ...'
    $env:SPRING_PROFILES_ACTIVE = 'local'
    $env:CAREERPILOT_AI_SERVICE_URL = 'http://localhost:8000'
    $env:CAREERPILOT_STORAGE_DIR = Join-Path $CpHome 'storage'
    $env:CAREERPILOT_FRONTEND_URL = 'http://localhost:5173'
    $env:CAREERPILOT_CORS_ALLOWED_ORIGINS = 'http://localhost:5173'
    $p = Start-Process -FilePath 'java' -ArgumentList '-jar', "`"$BackendJar`"" -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $LogDir 'backend.log') -RedirectStandardError (Join-Path $LogDir 'backend.err.log')
    $pids['backend'] = $p.Id
}

if (-not (Test-PortOpen 5173)) {
    Write-Host 'Starting frontend on http://localhost:5173 ...'
    $p = Start-Process -FilePath $env:ComSpec -ArgumentList '/c', 'npm run dev -- --host 127.0.0.1 --port 5173' `
        -WorkingDirectory (Join-Path $RepoRoot 'apps\frontend') -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $LogDir 'frontend.log') -RedirectStandardError (Join-Path $LogDir 'frontend.err.log')
    $pids['frontend'] = $p.Id
}

if ($pids.Count -gt 0) {
    $existing = @{}
    if (Test-Path $PidFile) { (Get-Content $PidFile | ConvertFrom-Json).PSObject.Properties | ForEach-Object { $existing[$_.Name] = $_.Value } }
    foreach ($k in $pids.Keys) { $existing[$k] = $pids[$k] }
    $existing | ConvertTo-Json | Set-Content $PidFile
}

Write-Host 'Waiting for services...'
$ai = Wait-ForUrl 'http://localhost:8000/api/v1/ai/health' 120
$backend = Wait-ForUrl 'http://localhost:8080/actuator/health' 240
$frontend = Wait-ForUrl 'http://localhost:5173' 120
Write-Host ("AI service: {0} | Backend: {1} | Frontend: {2}" -f $(if ($ai) {'UP'} else {'NOT RESPONDING'}), $(if ($backend) {'UP'} else {'NOT RESPONDING'}), $(if ($frontend) {'UP'} else {'NOT RESPONDING'}))
Write-Host "Open http://localhost:5173   (logs: $LogDir)"
