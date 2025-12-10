#!/bin/sh

##############################################################################
#
#   Gradle start up script for POSIX
#
##############################################################################

# Attempt to set APP_HOME
app_path=$0
while [ -h "$app_path" ] ; do
    ls=$( ls -ld "$app_path" )
    link=${ls#*' -> '}
    case $link in
      /*) app_path=$link ;;
      *) app_path=$( dirname "$app_path" )/$link ;;
    esac
done
APP_HOME=$( cd "$( dirname "$app_path" )" && pwd )

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD=$JAVA_HOME/bin/java
else
    JAVACMD=java
fi

exec "$JAVACMD" -Xmx64m -Xms64m -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
