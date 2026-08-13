@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo ============================================
echo   アグリゲーター : 更新して起動
echo ============================================

rem 秘密情報（LLM/LINEのキー）があれば読み込む（secrets.bat は git 管理外）。
rem secrets.bat が無くても、setx 等で環境変数を設定済みなら、それがそのまま使われます。
if exist "secrets.bat" (
    call "secrets.bat"
    echo [i] secrets.bat を読み込みました（LLM/LINE設定）
) else (
    echo [i] secrets.bat は無し。環境変数の設定（setx 済みなど）があればそれを使います。
)
if defined ANTHROPIC_API_KEY (
    echo [i] ANTHROPIC_API_KEY を検出（LLM_ENABLED=%LLM_ENABLED%）
) else (
    echo [i] ANTHROPIC_API_KEY 未設定 → LLM無効で起動します。
)
if defined LINE_CHANNEL_TOKEN (
    echo [i] LINE_CHANNEL_TOKEN を検出（LINE_ENABLED=%LINE_ENABLED%）
) else (
    echo [i] LINE_CHANNEL_TOKEN 未設定 → LINE無効で起動します。
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
