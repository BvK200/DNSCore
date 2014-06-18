#!/bin/bash
#The Contentbroker Start Skript

if [ "$(id -u)" == "0" ]; then
   echo "This script must not be run as root" 1>&2
   exit 1
fi

if [[ -f /tmp/cb.running ]] ; then
    echo "ContentBroker is already running"
    exit 1
fi
touch /tmp/cb.running
nohup $JAVA_HOME/bin/java  $JAVA_OPTS -jar ContentBroker.jar 1>log/stdout.log 2>log/stderr.log &

