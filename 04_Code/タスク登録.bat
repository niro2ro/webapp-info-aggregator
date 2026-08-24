@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

rem ============================================================
rem  Register auto-run at logon WITHOUT admin or schtasks.
rem  Puts a shortcut to 自動実行.bat into the user's Startup folder.
rem  自動実行.bat waits, then runs 収集バッチ.bat and 通知バッチ.bat.
rem ============================================================

set "STARTUP=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
set "LNK=%STARTUP%\Aggregator自動実行.lnk"
set "TARGET=%~dp0自動実行.bat"

echo スタートアップに登録します...

powershell -NoProfile -ExecutionPolicy Bypass -Command "$s=(New-Object -ComObject WScript.Shell).CreateShortcut('%LNK%'); $s.TargetPath='%TARGET%'; $s.WorkingDirectory='%~dp0'; $s.Save()"
if errorlevel 1 goto :err
if not exist "%LNK%" goto :err

echo.
echo [OK] 登録しました。次回ログオン時に自動で「収集 → 通知」が実行されます。
echo   登録先: %LNK%
echo.
echo 今すぐ手動で試すには、この2つを順に実行:
echo    収集バッチ.bat
echo    通知バッチ.bat
echo 解除するには:  タスク解除.bat
echo （前提: 実行時に PostgreSQL が起動していること。通知の実送信には secrets.bat の LINE 設定）
goto :end

:err
echo.
echo *** 登録に失敗しました。上のメッセージを確認してください。 ***

:end
echo.
pause
endlocal
