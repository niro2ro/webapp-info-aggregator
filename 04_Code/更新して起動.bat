@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo ============================================
echo   アグリゲーター : 更新して起動
echo ============================================

rem 秘密情報（LLM/LINEのキー）があれば読み込む（secrets.bat は git 管理外）
if exist "secrets.bat" (
    call "secrets.bat"
    echo [i] secrets.bat を読み込みました（LLM/LINE設定）
) else (
    echo [i] secrets.bat は未設定（LLM/LINEは無効のまま起動します）
)

rem ログの日本語表示のため UTF-8 出力にする
set "JAVA_TOOL_OPTIONS=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"

echo.
echo [1/3] 最新を取得します (git pull)...
git pull
if errorlevel 1 goto :error

echo.
echo [2/3] ビルドします（初回は数分かかります）...
call mvnw.cmd -pl aggregator-web -am install -DskipTests
if errorlevel 1 goto :error

echo.
echo [3/3] 起動します。ブラウザで http://localhost:8080/ を開いてください。
echo       （停止するには この画面で Ctrl + C を押します）
echo.
call mvnw.cmd -pl aggregator-web spring-boot:run
goto :end

:error
echo.
echo *** エラーが発生しました。上のログを確認してください。 ***
echo.
pause

:end
endlocal
