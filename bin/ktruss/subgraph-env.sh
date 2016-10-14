#!/usr/bin/env bash

heapSize="5g"
newRatio=2
maxPause=1
export BASEDIR=$(dirname "$0")
debug="-XX:+PrintGC -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -Xloggc:$BASEDIR/gc.log"
export GC_OPTIONS="-XX:+UseParallelGC  -Xmx$heapSize -Xms$heapSize -XX:NewRatio=$newRatio -XX:MaxGCPauseMillis=$maxPause $debug"
export JAR_PATH="$PWD/target/subgraph-mining-1.0-jar-with-dependencies.jar"
