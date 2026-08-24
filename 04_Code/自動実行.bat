@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

rem ============================================================
rem  Auto-run at logon: collect then notify (one shot each).
rem  Registered via startup folder (no admin, no schtasks).
rem  Requires PostgreSQL running. Waits a bit so DB/app can start.
rem ============================================================

echo [%date% %time%] 自動実行を開始します。DBの起動を待ちます...
timeout /t 300 /nobreak >nul

call "%~dp0収集バッチ.bat"
timeout /t 120 /nobreak >nul
call "%~dp0通知バッチ.bat"

echo [%date% %time%] 自動実行を終了しました。
endlocal
