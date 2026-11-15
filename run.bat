@echo off
setlocal
cd /d "%~dp0"
if not exist out mkdir out
javac -encoding UTF-8 -source 8 -target 8 -d out src\main\java\lab\db\*.java
if errorlevel 1 exit /b 1
java -cp out lab.db.Main
