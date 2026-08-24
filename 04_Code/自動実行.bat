@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

rem ============================================================
rem  Auto-run at logon: ensure DB is up, then collect, then notify.
rem  Registered via the Startup folder (no admin, no schtasks).
rem
rem  Instead of a blind fixed wait, we now actively start/await the
rem  PostgreSQL Windows service (DB起動.bat) so the run does not fail
rem  just because the DB was not ready yet. Outcome is appended to
rem  logs\auto.log for later inspection (the window may auto-close).
rem ============================================================

set "LOGDIR=%~dp0logs"
if not exist "%LOGDIR%" mkdir "%LOGDIR%"
set "AUTOLOG=%LOGDIR%\auto.log"

echo ==== %date% %time% 自動実行開始 ==== >> "%AUTOLOG%"
echo [%date% %time%] 自動実行を開始します。DBの起動を確認します...

rem Small initial grace so other logon items settle (DB起動.bat then
rem actively waits for port 5432, so this stays short).
timeout /t 30 /nobreak >nul

call "%~dp0DB起動.bat"
if errorlevel 1 (
    echo [%date% %time%] DB未起動のため中断しました。 >> "%AUTOLOG%"
    echo *** DB が起動しないため中断します。logs\auto.log を確認してください。 ***
    endlocal & exit /b 1
)
echo [%date% %time%] DB ready。収集→通知を実行します。 >> "%AUTOLOG%"

call "%~dp0収集バッチ.bat"
timeout /t 120 /nobreak >nul
call "%~dp0通知バッチ.bat"

echo ==== %date% %time% 自動実行終了 ==== >> "%AUTOLOG%"
echo [%date% %time%] 自動実行を終了しました。
endlocal
