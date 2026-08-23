@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

rem ============================================================
rem  収集バッチ＋通知バッチを Windows タスクスケジューラに登録する。
rem  ・PC起動（ログオン）時のみ実行:
rem      収集 = ログオン5分後 / 通知 = ログオン10分後（収集の少し後＝新着が入ってから送る）
rem  DB や常駐アプリの立ち上げ余裕をとって遅延。同名タスクは上書き（/F）。
rem  管理者権限は不要（LIMITED）。通知の実送信には secrets.bat の LINE 設定が必要
rem  （未設定なら NoOp でログのみ）。
rem ============================================================

set "COLLECT=Aggregator収集"
set "NOTIFY=Aggregator通知"
set "RUN_C=cmd /c \"%~dp0収集バッチ.bat\""
set "RUN_N=cmd /c \"%~dp0通知バッチ.bat\""

echo タスクを登録します...

rem 念のため旧・日次タスクがあれば掃除（前バージョンで登録した場合の後始末）
schtasks /Delete /TN "%COLLECT%_日次" /F >nul 2>nul
schtasks /Delete /TN "%NOTIFY%_日次" /F >nul 2>nul

rem ① 収集: ログオン5分後
schtasks /Create /TN "%COLLECT%" /TR "%RUN_C%" /SC ONLOGON /DELAY 0005:00 /RL LIMITED /F
if errorlevel 1 goto :err
rem ② 通知: ログオン10分後（収集の後）
schtasks /Create /TN "%NOTIFY%" /TR "%RUN_N%" /SC ONLOGON /DELAY 0010:00 /RL LIMITED /F
if errorlevel 1 goto :err

echo.
echo [OK] 登録しました（PC起動時のみ）。
echo   - 「%COLLECT%」 : ログオン5分後に収集
echo   - 「%NOTIFY%」  : ログオン10分後に通知
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
