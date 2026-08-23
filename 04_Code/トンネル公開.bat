@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

rem ============================================================
rem  ローカル(http://localhost:8080)を一時的に公開HTTPSにする。
rem  LINE の Webhook を自宅PCで受けるため（合言葉連携のテスト用）。
rem  Cloudflare Tunnel の「クイックトンネル」を使う（アカウント不要・無料）。
rem  cloudflared が無ければ、このフォルダに cloudflared.exe を自動ダウンロードする。
rem  実行すると https://xxxx.trycloudflare.com のURLが表示される。
rem  → そのURL＋"/line/webhook" を LINE Developers Console の Webhook URL に設定する。
rem  ※このURLは起動ごとに変わる（テスト用途）。恒久運用は Phase 6 の VPS。
rem ============================================================

rem 使う cloudflared を決める（このフォルダの exe を最優先→PATH の cloudflared）
set "CF=%~dp0cloudflared.exe"
if not exist "%CF%" (
    where cloudflared >nul 2>nul && set "CF=cloudflared"
)

rem どちらも無ければ公式リリースから自動ダウンロード（このフォルダへ）
if not exist "%~dp0cloudflared.exe" if not "%CF%"=="cloudflared" (
    echo [i] cloudflared が見つかりません。公式リリースからダウンロードします...
    powershell -NoProfile -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; $ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri 'https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe' -OutFile '%~dp0cloudflared.exe'"
    if errorlevel 1 (
        echo *** ダウンロードに失敗しました。手動で入手してください:
        echo     https://github.com/cloudflare/cloudflared/releases/latest
        echo   の cloudflared-windows-amd64.exe を cloudflared.exe という名前でこのフォルダに置く。
        pause
        exit /b 1
    )
    set "CF=%~dp0cloudflared.exe"
    echo [i] ダウンロード完了。
)

echo ============================================================
echo  アプリ(localhost:8080)を公開します。別ウィンドウで
echo  「更新して起動.bat」等でアプリを起動しておいてください。
echo ============================================================
echo.
echo 表示される https://xxxx.trycloudflare.com を控えて、
echo   LINE Developers Console の「Messaging API設定」タブ → Webhook URL に
echo   「そのURL + /line/webhook」を設定して Webhook を ON にしてください。
echo （このウィンドウを閉じると公開は止まります）
echo.

"%CF%" tunnel --url http://localhost:8080

endlocal
