@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

rem ============================================================
rem  ローカル(http://localhost:8080)を一時的に公開HTTPSにする。
rem  LINE の Webhook を自宅PCで受けるため（合言葉連携のテスト用）。
rem  Cloudflare Tunnel の「クイックトンネル」を使う（アカウント不要・無料）。
rem  実行すると https://xxxx.trycloudflare.com のURLが表示される。
rem  → そのURL＋"/line/webhook" を LINE Developers Console の Webhook URL に設定する。
rem  ※このURLは起動ごとに変わる（テスト用途）。恒久運用は Phase 6 の VPS。
rem ============================================================

rem cloudflared があるか確認
where cloudflared >nul 2>nul
if errorlevel 1 (
    echo [!] cloudflared が見つかりません。先にインストールしてください:
    echo.
    echo     winget install --id Cloudflare.cloudflared
    echo.
    echo   もしくは https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/
    echo   から cloudflared.exe を入手して PATH に置いてください。
    echo.
    pause
    exit /b 1
)

echo ============================================================
echo  アプリ(localhost:8080)を公開します。別ウィンドウで
echo  「更新して起動.bat」等でアプリを起動しておいてください。
echo ============================================================
echo.
echo 表示される https://xxxx.trycloudflare.com を控えて、
echo   LINE Developers Console の Webhook URL に
echo   「そのURL + /line/webhook」を設定して Webhook を ON にしてください。
echo （このウィンドウを閉じると公開は止まります）
echo.

cloudflared tunnel --url http://localhost:8080

endlocal
