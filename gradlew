#!/bin/sh

#
# Gradle Wrapper起動スクリプト（Unix系）
#

# ベースディレクトリを設定
APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

# 現在のディレクトリを保存
SAVED="`pwd`"
cd "`dirname \"$0\"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

# Java実行可能ファイルを探す
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

# Gradleラッパーのクラスパス
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Gradleを実行
exec "$JAVACMD" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"



