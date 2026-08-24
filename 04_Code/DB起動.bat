@echo off
chcp 65001 >nul
setlocal

rem ============================================================
rem  Ensure the local PostgreSQL (Windows service) is running and
rem  accepting connections. Shared helper called by the batch bats.
rem
rem  - Starts any Windows service named postgresql* if not running.
rem    (Service name varies by version: postgresql-x64-16 etc., so
rem     match by wildcard instead of hard-coding the name.)
rem  - Waits until TCP port 5432 accepts connections via
rem    Test-NetConnection (no psql/pg_isready on PATH required).
rem
rem  Exit code: 0 = ready, 1 = not ready (timed out).
rem  Idempotent: safe to call repeatedly. With startup type
rem  Automatic the service is usually already up at logon and this
rem  returns almost immediately.
rem
rem  NOTE: messages inside the powershell -Command string are kept
rem  ASCII on purpose; non-ASCII there can be mangled by cmd parsing.
rem  Japanese status text is emitted by the batch 'echo' lines below.
rem ============================================================

echo [%date% %time%] PostgreSQL の起動を確認します...

rem 1) Start the service if not already running (single line on purpose).
powershell -NoProfile -ExecutionPolicy Bypass -Command "$svc = Get-Service -Name '*postgres*' -ErrorAction SilentlyContinue; if (-not $svc) { Write-Host '[!] no postgresql* service found - check the installed service name' } else { foreach ($s in $svc) { if ($s.Status -ne 'Running') { try { Start-Service $s.Name -ErrorAction Stop; Write-Host ('[i] started service: ' + $s.Name) } catch { Write-Host ('[!] could not start ' + $s.Name + ' status=' + $s.Status + ' (startup type may be Manual, or admin required)') } } else { Write-Host ('[i] already running: ' + $s.Name) } } }"

rem 2) Wait until port 5432 accepts TCP connections (max ~120s).
powershell -NoProfile -ExecutionPolicy Bypass -Command "for ($i=0; $i -lt 60; $i++) { try { $ok = (Test-NetConnection -ComputerName 'localhost' -Port 5432 -WarningAction SilentlyContinue).TcpTestSucceeded } catch { $ok = $false }; if ($ok) { Start-Sleep -Seconds 1; exit 0 }; Start-Sleep -Seconds 2 }; exit 1"

if errorlevel 1 (
    echo *** PostgreSQL に接続できません（localhost:5432）。 ***
    echo     対処: サービス「postgresql-x64-16」等のスタートアップの種類を「自動」にするか、
    echo           手動でDBを起動してから再実行してください。
    endlocal & exit /b 1
)

echo [%date% %time%] PostgreSQL は受付可能です。
endlocal & exit /b 0
