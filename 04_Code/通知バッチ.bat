@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

rem ============================================================
rem  Run the notification batch once and exit (one shot).
rem  Sends unnotified favorited articles to LINE (idempotent).
rem  Output is saved to logs\notify.log so scheduled/closed-window
rem  runs can be inspected later. Requires PostgreSQL running and
rem  LINE settings in secrets.bat (without them it runs as NoOp).
rem ============================================================

if exist "secrets.bat" call "secrets.bat"
set "JAVA_TOOL_OPTIONS=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dfile.encoding=UTF-8"

set "JAVACMD=java"
if defined JAVA_HOME set "JAVACMD=%JAVA_HOME%\bin\java"

set "JAR=aggregator-batch\target\aggregator-batch-0.1.0-SNAPSHOT-notification.jar"
set "LOGDIR=%~dp0logs"
if not exist "%LOGDIR%" mkdir "%LOGDIR%"
set "RUNLOG=%LOGDIR%\_last_notify.log"
set "LOG=%LOGDIR%\notify.log"

if not exist "%JAR%" (
    echo [i] 通知バッチのjarが無いのでビルドします。初回のみ・数分...
    call mvnw.cmd -q -pl aggregator-batch -am package -DskipTests
    if errorlevel 1 (
        echo *** ビルドに失敗しました。 ***
        exit /b 1
    )
)

echo [%date% %time%] 通知バッチを実行します...（ログ: %LOG%）
"%JAVACMD%" -jar "%JAR%" > "%RUNLOG%" 2>&1
set "RC=%errorlevel%"
echo ==== %date% %time% (code=%RC%) ==== >> "%LOG%"
type "%RUNLOG%" >> "%LOG%"

echo.
echo ---- 実行結果（要点）----
findstr /C:"通知バッチ" /C:"[通知]" "%RUNLOG%"
echo ------------------------
echo 全ログ: %LOG%
echo [%date% %time%] 通知バッチ終了 code=%RC%
endlocal & exit /b %RC%
