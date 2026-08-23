@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

rem ============================================================
rem  収集バッチを Windows タスクスケジューラに登録する。
rem  ・ログオン時に起動（PC を付けてログインしたら動く）
rem  ・起動から5分の遅延（DB や常駐アプリが立ち上がる余裕）
rem  ・毎日 正午(12:00) にも実行（日中の新着を拾う）
rem  同名タスクがあれば上書き（/F）。管理者権限は不要（LIMITED）。
rem ============================================================

set "TASK=Aggregator収集"
set "RUN=cmd /c \"%~dp0収集バッチ.bat\""

echo タスク「%TASK%」を登録します...

rem ① ログオン時（起動5分後）
schtasks /Create /TN "%TASK%" /TR "%RUN%" /SC ONLOGON /DELAY 0005:00 /RL LIMITED /F
if errorlevel 1 goto :err

rem ② 毎日 12:00 にも実行（別名タスク）
schtasks /Create /TN "%TASK%_日次" /TR "%RUN%" /SC DAILY /ST 12:00 /RL LIMITED /F
if errorlevel 1 goto :err

echo.
echo [OK] 登録しました。
echo   - 「%TASK%」        : ログオン5分後に収集
echo   - 「%TASK%_日次」   : 毎日12:00に収集
echo.
echo 今すぐ試すには:  schtasks /Run /TN "%TASK%"
echo 解除するには  :  タスク解除.bat
echo （前提: 収集時に PostgreSQL が起動していること）
goto :end

:err
echo.
echo *** 登録に失敗しました。上のメッセージを確認してください。 ***

:end
echo.
pause
endlocal
