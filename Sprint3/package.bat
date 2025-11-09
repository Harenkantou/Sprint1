@echo off
echo Compilation du framework gigaspring...
call mvn clean install
if %ERRORLEVEL% NEQ 0 (
    echo ERREUR: La compilation a echoue
    exit /b 1
)
echo.
echo Compilation terminee avec succes !
echo Le fichier JAR est disponible dans target\gigaspring-1.0-SNAPSHOT.jar
