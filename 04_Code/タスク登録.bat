@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

rem ============================================================
rem  収集バッチ＋通知バッチを Windows タスクスケジューラに登録する。
rem  ・収集: ログオン5分後 ＋ 毎日12:00
rem  ・通知: ログオン10分後 ＋ 毎日12:15（収集の少し後＝新着が入ってから送る）
rem  DB や常駐アプリの立ち上げ余裕をとって遅延。同名タスクは上書き（/F）。
rem  管理者権限は不要（LIMITED）。通知の実送信には secrets.bat の LINE 設定が必要
rem  （未設定なら NoOp でログのみ）。
rem ============================================================

set "COLLECT=Aggregator収集"
set "NOTIFY=Aggregator通知"
set "RUN_C=cmd /c \"%~dp0収集バッチ.bat\""
set "RUN_N=cmd /c \"%~dp0通知バッチ.bat\""

echo タスクを登録します...

rem ① 収集: ログオン5分後
schtasks /Create /TN "%COLLECT%" /TR "%RUN_C%" /SC ONLOGON /DELAY 0005:00 /RL LIMITED /F
if errorlevel 1 goto :err
rem ② 収集: 毎日 12:00
schtasks /Create /TN "%COLLECT%_日次" /TR "%RUN_C%" /SC DAILY /ST 12:00 /RL LIMITED /F
if errorlevel 1 goto :err
rem ③ 通知: ログオン10分後（収集の後）
schtasks /Create /TN "%NOTIFY%" /TR "%RUN_N%" /SC ONLOGON /DELAY 0010:00 /RL LIMITED /F
if errorlevel 1 goto :err
rem ④ 通知: 毎日 12:15（収集の後）
schtasks /Create /TN "%NOTIFY%_日次" /TR "%RUN_N%" /SC DAILY /ST 12:15 /RL LIMITED /F
if errorlevel 1 goto :err

echo.
echo [OK] 登録しました。
echo   - 「%COLLECT%」/「%COLLECT%_日次」 : ログオン5分後・毎日12:00 に収集
echo   - 「%NOTIFY%」/「%NOTIFY%_日次」   : ログオン10分後・毎日12:15 に通知
echo.
echo 今すぐ試すには:  schtasks /Run /TN "%COLLECT%"   （収集）
echo                  schtasks /Run /TN "%NOTIFY%"    （通知）
echo 解除するには  :  タスク解除.bat
echo （前提: PostgreSQL 起動。通知の実送信は secrets.bat の LINE 設定が必要）
goto :end

:err
echo.
echo *** 登録に失敗しました。上のメッセージを確認してください。 ***

:end
echo.
pause
endlocal
