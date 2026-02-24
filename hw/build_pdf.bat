@echo off
setlocal EnableExtensions EnableDelayedExpansion


if "%~1"=="" (
  echo Usage:
  echo   %~nx0 1.1
  echo   %~nx0 2.6
  echo   %~nx0 block1 1.2
  echo   %~nx0 block1\2.4
  exit /b 1
)

set "BLOCK="
set "TASK="

REM
if /i "%~1"=="block1" (
  set "BLOCK=block1"
  set "TASK=%~2"
) else if /i "%~1"=="block2" (
  set "BLOCK=block2"
  set "TASK=%~2"

REM
) else (
  echo %~1 | findstr /i /c:"block1\" /c:"block1/" /c:"block2\" /c:"block2/" >nul
  if not errorlevel 1 (
    for /f "tokens=1,2 delims=/\" %%a in ("%~1") do (
      set "BLOCK=%%a"
      set "TASK=%%b"
    )
  ) else (
    set "BLOCK=block1"
    set "TASK=%~1"
  )
)

if "%TASK%"=="" (
  echo Error: task is missing.
  echo Examples:
  echo   %~nx0 1.1
  echo   %~nx0 block1 2.6
  exit /b 2
)

set "OUTDIR=%BLOCK%\%TASK%\solution\explanation"
set "TEX=%OUTDIR%\explanation.tex"
set "TEMPDIR=%OUTDIR%\_temp"

if not exist "%TEX%" (
  echo Error: TeX file not found:
  echo   %TEX%
  exit /b 3
)

if exist "%TEMPDIR%" rmdir /s /q "%TEMPDIR%"
mkdir "%TEMPDIR%" >nul

echo Compiling: %TEX%

pdflatex -interaction=nonstopmode -halt-on-error -output-directory="%TEMPDIR%" "%TEX%"
if errorlevel 1 (
  echo.
  echo Build failed. Temp folder kept for logs:
  echo   %TEMPDIR%
  exit /b 4
)

move /y "%TEMPDIR%\explanation.pdf" "%OUTDIR%\explanation.pdf" >nul

rmdir /s /q "%TEMPDIR%"

echo Done: %OUTDIR%\explanation.pdf
exit /b 0