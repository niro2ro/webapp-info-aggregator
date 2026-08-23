@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

rem ============================================================
rem  通知バッチを1回だけ実行して終了する（ワンショット）。
rem  未通知×お気に入りの新着を LINE に1通で送り、冪等記録する。
rem  タスクスケジューラから収集の少し後に呼ぶ想定。手動確認にも使える。
rem  前提: PostgreSQL 起動 ＋ secrets.bat に LINE_ENABLED/トークン設定。
rem       LINE未設定なら NoOp（ログのみ・実送信なし）で動く。
rem ============================================================

if exist "secrets.bat" call "secrets.bat"
set "JAVA_TOOL_OPTIONS=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dfile.encoding=UTF-8"

set "JAVACMD=java"
if defined JAVA_HOME set "JAVACMD=%JAVA_HOME%\bin\java"

set "JAR=aggregator-batch\target\aggregator-batch-0.1.0-SNAPSHOT-notification.jar"

if not exist "%JAR%" (
    echo [i] 通知バッチのjarが無いのでビルドします（初回のみ・数分）...
    call mvnw.cmd -q -pl aggregator-batch -am package -DskipTests
    if errorlevel 1 (
        echo *** ビルドに失敗しました。 ***
        exit /b 1
    )
)

echo [%date% %time%] 通知バッチを実行します...
"%JAVACMD%" -jar "%JAR%"
set "RC=%errorlevel%"
echo [%date% %time%] 通知バッチ終了（コード=%RC%）
endlocal & exit /b %RC%
