@echo off
chcp 65001 >nul
setlocal

rem ============================================================
rem  Remove the auto-run registration.
rem  - Deletes the Startup-folder shortcut.
rem  - Also removes old schtasks tasks if a previous version created them.
rem ============================================================

set "LNK=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\Aggregator自動実行.lnk"

echo 自動実行の登録を解除します...
if exist "%LNK%" ( del /f /q "%LNK%" & echo [OK] スタートアップ登録を削除しました。) else ( echo [i] スタートアップ登録はありませんでした。)

rem 旧バージョンで schtasks に登録していた場合の後始末（無ければ無視されます）
schtasks /Delete /TN "Aggregator収集" /F >nul 2>nul
schtasks /Delete /TN "Aggregator収集_日次" /F >nul 2>nul
schtasks /Delete /TN "Aggregator通知" /F >nul 2>nul
schtasks /Delete /TN "Aggregator通知_日次" /F >nul 2>nul

echo.
echo [OK] 解除しました。
echo.
pause
endlocal
