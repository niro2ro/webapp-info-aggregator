@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

rem ============================================================
rem  Run the notification batch once and exit (one shot).
rem  Sends unnotified favorited articles to LINE (idempotent).
rem  Requires PostgreSQL running and LINE settings in secrets.bat.
rem  Without LINE settings it runs as NoOp (log only, no real send).
rem ============================================================

if exist "secrets.bat" call "secrets.bat"
set "JAVA_TOOL_OPTIONS=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dfile.encoding=UTF-8"

set "JAVACMD=java"
if defined JAVA_HOME set "JAVACMD=%JAVA_HOME%\bin\java"

set "JAR=aggregator-batch\target\aggregator-batch-0.1.0-SNAPSHOT-notification.jar"

if not exist "%JAR%" (
    echo [i] 通知バッチのjarが無いのでビルドします。初回のみ・数分...
    call mvnw.cmd -q -pl aggregator-batch -am package -DskipTests
    if errorlevel 1 (
        echo *** ビルドに失敗しました。 ***
        exit /b 1
    )
)

echo [%date% %time%] 通知バッチを実行します...
"%JAVACMD%" -jar "%JAR%"
set "RC=%errorlevel%"
echo [%date% %time%] 通知バッチ終了 code=%RC%
endlocal & exit /b %RC%
