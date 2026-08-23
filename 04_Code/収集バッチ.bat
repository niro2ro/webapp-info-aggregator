@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

rem ============================================================
rem  収集バッチを1回だけ実行して終了する（ワンショット）。
rem  タスクスケジューラから呼ぶ用。手動で叩いて動作確認にも使える。
rem  前提: PostgreSQL（Docker Compose 等）が起動していること。
rem ============================================================

rem 秘密情報（LLM/LINEのキー）があれば読み込む（無くてもRSS収集は動く）。
if exist "secrets.bat" call "secrets.bat"

rem ログの日本語表示のため UTF-8 出力にする
set "JAVA_TOOL_OPTIONS=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dfile.encoding=UTF-8"

rem java の場所（JAVA_HOME があれば優先、無ければ PATH の java）
set "JAVACMD=java"
if defined JAVA_HOME set "JAVACMD=%JAVA_HOME%\bin\java"

set "JAR=aggregator-batch\target\aggregator-batch-0.1.0-SNAPSHOT-collection.jar"

rem 実行可能jarが無ければ初回だけビルドする（以降は再利用＝速い）
if not exist "%JAR%" (
    echo [i] 収集バッチのjarが無いのでビルドします（初回のみ・数分）...
    call mvnw.cmd -q -pl aggregator-batch -am package -DskipTests
    if errorlevel 1 (
        echo *** ビルドに失敗しました。 ***
        exit /b 1
    )
)

echo [%date% %time%] 収集バッチを実行します...
"%JAVACMD%" -jar "%JAR%"
set "RC=%errorlevel%"
echo [%date% %time%] 収集バッチ終了（コード=%RC%）
endlocal & exit /b %RC%
