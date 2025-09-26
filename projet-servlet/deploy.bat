@echo off
SETLOCAL

:: Configuration
SET PROJECT_NAME=projet-servlet
SET JAVA_HOME=C:\Program Files\Java\jdk-21
SET TOMCAT_HOME=D:\apache-tomcat-10.1.28
SET OUTPUT_DIR=build
SET WEBAPP_DIR=%TOMCAT_HOME%\webapps

:: Créer le dossier de build
mkdir %OUTPUT_DIR%\WEB-INF\classes
mkdir %OUTPUT_DIR%\WEB-INF\lib

:: Copier les fichiers web
xcopy web\* %OUTPUT_DIR% /E /Y

:: Copier les dépendances (si besoin)
copy /Y lib\*.jar %OUTPUT_DIR%\WEB-INF\lib\ >nul 2>&1

:: Préparer le classpath (tous les JAR du projet + Tomcat)
setlocal EnableDelayedExpansion

:: Ajouter les JAR du dossier lib du projet au classpath
set CP="%TOMCAT_HOME%\lib\jakarta.servlet-api.jar"
for %%f in (%OUTPUT_DIR%\WEB-INF\lib\*.jar) do set CP=!CP!;"%%f"
for %%f in (lib\*.jar) do set CP=!CP!;"%%f"
endlocal & set CP=%CP%


:: Lister tous les fichiers .java à compiler
dir /S /B src\main\java\*.java > sources.txt

:: Compiler les fichiers Java
echo Compilation des fichiers Java...
"%JAVA_HOME%\bin\javac" -cp "%CP%" -d "%OUTPUT_DIR%\WEB-INF\classes" @sources.txt

:: Nettoyer le fichier temporaire
del sources.txt

:: Vérifier si la compilation a réussi
IF %ERRORLEVEL% NEQ 0 (
    echo Erreur lors de la compilation.
    exit /b %ERRORLEVEL%
)

:: Créer le fichier WAR
echo Création du fichier WAR...
cd %OUTPUT_DIR%
"%JAVA_HOME%\bin\jar" -cvf %PROJECT_NAME%.war .
cd ..

:: Déployer sur Tomcat
echo Déploiement sur Tomcat...
copy "%OUTPUT_DIR%\%PROJECT_NAME%.war" "%WEBAPP_DIR%" 

:: Démarrer Tomcat (optionnel)
:: "%TOMCAT_HOME%\bin\startup.bat"

echo Déploiement terminé. Accédez à http://localhost:8080/%PROJECT_NAME%/
ENDLOCAL