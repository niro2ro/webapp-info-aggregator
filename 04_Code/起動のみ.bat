@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo ============================================
echo   アグリゲーター : 起動のみ（pull/ビルドなし）
echo ============================================
echo   コードを変えていない時の素早い再起動用です。
echo   コードを更新したら「更新して起動.bat」を使ってください。
echo.

if exist "secrets.bat" call "secrets.bat"
set "JAVA_TOOL_OPTIONS=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"

echo 起動します。ブラウザで http://localhost:8080/ を開いてください。
echo （停止するには Ctrl + C）
echo.
call mvnw.cmd -pl aggregator-web spring-boot:run

endlocal
