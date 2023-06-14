#!/bin/sh

set -o errexit
set -o nounset
IFS=$(printf '\n\t')

export TINI_SUBREAPER=

echo "Starting cf-file-scanner..."
java \
    -jar java-function-invoker-1.1.0.jar \
    --classpath cf-file-scanner-1.0-SNAPSHOT.jar \
    --target org.broadinstitute.ddp.cf.FileScanner \
    --port "$PORT" &

if [ -x "/init" ]; then
    echo "Starting clamav daemon..."
    exec /init
fi

trap : TERM INT;
sleep 9999999999d & wait