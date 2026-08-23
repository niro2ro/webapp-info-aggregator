@echo off
chcp 65001 >nul
setlocal

rem ============================================================
rem  収集バッチのタスクスケジューラ登録を解除する。
rem ============================================================

set "TASK=Aggregator収集"

echo タスク「%TASK%」「%TASK%_日次」を解除します...
schtasks /Delete /TN "%TASK%" /F
schtasks /Delete /TN "%TASK%_日次" /F

echo.
echo [OK] 解除しました（無かった場合はその旨のメッセージが出ます）。
echo.
pause
endlocal
