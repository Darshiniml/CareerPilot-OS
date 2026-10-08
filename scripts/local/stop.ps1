# Stop the locally started CareerPilot processes and PostgreSQL.
. (Join-Path $PSScriptRoot 'common.ps1')
if (Test-Path $PidFile) {
    (Get-Content $PidFile | ConvertFrom-Json).PSObject.Properties | ForEach-Object {
        try {
            # /T stops child processes too (npm -> node, uvicorn workers).
            & (Join-Path $env:SystemRoot 'System32\taskkill.exe') /PID $_.Value /T /F 2>$null | Out-Null
            Write-Host "Stopped $($_.Name) (pid $($_.Value))"
        } catch { }
    }
    Remove-Item $PidFile
}
if (Test-Path (Join-Path $PgData 'postmaster.pid')) {
    & (Join-Path $PgBin 'pg_ctl.exe') -D $PgData -m fast stop | Out-Null
    Write-Host 'Stopped PostgreSQL'
}
