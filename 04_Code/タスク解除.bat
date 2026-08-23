@echo off
chcp 65001 >nul
setlocal

rem ============================================================
rem  収集バッチのタスクスケジューラ登録を解除する。
rem ============================================================

echo 収集・通知の全タスクを解除します...
schtasks /Delete /TN "Aggregator収集" /F
schtasks /Delete /TN "Aggregator収集_日次" /F
schtasks /Delete /TN "Aggregator通知" /F
schtasks /Delete /TN "Aggregator通知_日次" /F

echo.
echo [OK] 解除しました（無かった場合はその旨のメッセージが出ます）。
echo.
pause
endlocal
