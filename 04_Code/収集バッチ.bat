@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

rem ============================================================
rem  Run the collection batch once and exit (one shot).
rem  Called by the logon auto-run, or run manually to test.
rem  Requires PostgreSQL running. LLM/LINE keys come from secrets.bat.
rem ============================================================

if exist "secrets.bat" call "secrets.bat"
set "JAVA_TOOL_OPTIONS=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dfile.encoding=UTF-8"

set "JAVACMD=java"
if defined JAVA_HOME set "JAVACMD=%JAVA_HOME%\bin\java"

set "JAR=aggregator-batch\target\aggregator-batch-0.1.0-SNAPSHOT-collection.jar"

if not exist "%JAR%" (
    echo [i] 収集バッチのjarが無いのでビルドします。初回のみ・数分...
    call mvnw.cmd -q -pl aggregator-batch -am package -DskipTests
    if errorlevel 1 (
        echo *** ビルドに失敗しました。 ***
        exit /b 1
    )
)

echo [%date% %time%] 収集バッチを実行します...
"%JAVACMD%" -jar "%JAR%"
set "RC=%errorlevel%"
echo [%date% %time%] 収集バッチ終了 code=%RC%
endlocal & exit /b %RC%
