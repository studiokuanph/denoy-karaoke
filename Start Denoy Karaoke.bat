@echo off
title Denoy Karaoke - PC Bridge
cd /d "%~dp0"

echo Starting MegaOke...
start "" "C:\Program Files\MegaOke\megaoke.exe"
timeout /t 5 /nobreak >nul

echo Starting Bridge Server...
python pc_bridge.py
pause
