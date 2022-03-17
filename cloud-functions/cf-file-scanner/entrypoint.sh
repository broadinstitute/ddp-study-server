#!/bin/sh

set -eu

DATABASE_DIR="/var/lib/clamav"
RUNTIME_DIR="/var/run/clamav"
CLAMD_SOCKET="$RUNTIME_DIR/clamav.ctl"

if [ ! -d "${RUNTIME_DIR}" ]; then
	install -d -g "clamav" -m 775 -o "clamav" "${RUNTIME_DIR}"
fi

if [ ! -d "${DATABASE_DIR}" ]; then
    install -d -g "clamav" -m 775 -o "clamav" "${DATABASE_DIR}"
else
    chown -R clamav:clamav "${DATABASE_DIR}"
fi

# If a database doesn't exist, clamd won't start
if [ ! -f "${DATABASE_DIR}/main.cvd" ]; then
    echo "Updating clamd database"
    freshclam \
        --foreground \
        --stdout
fi

echo -n "Starting clamd"
clamd --foreground &

while [ ! -S "${CLAMD_SOCKET}" ]; do
    if [ "${_counter:=0}" -lt "${_timeout:=300}" ]; then
        echo -n "."
        sleep 1
        _counter=$((_counter + 1))
    fi
done

if [ ! -S "${CLAMD_SOCKET}" ]; then
    echo "Failed to start clamd"
    exit -1
fi

echo -e "\nclamd started"
echo "Starting freshclam"
freshclam \
    --checks="1" \
    --daemon \
    --foreground \
    --stdout \
    --user="clamav" &

echo "Starting cf-file-scanner"
java \
    -jar java-function-invoker-1.1.0.jar \
    --classpath cf-file-scanner-1.0-SNAPSHOT.jar \
    --target org.broadinstitute.ddp.cf.FileScanner

wait
exit $?