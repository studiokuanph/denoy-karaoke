@echo off
set DIRNAME=%~dp0
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
"%JAVA_HOME%\bin\java.exe" -Dorg.gradle.appname=gradlew -classpath "%DIRNAME%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
