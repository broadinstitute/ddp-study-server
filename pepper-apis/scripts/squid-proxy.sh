#!/usr/bin/env bash
#
# Script to start/stop squid proxy within VM.

NAME=$(basename "$0")
if (( $# < 3 )); then
  echo "usage: $NAME <PROJECT_ID> <INSTANCE_NAME> <start | stop | status | tail-logs | tail-access>"
  exit 1
fi

PROJECT_ID="$1"
INSTANCE_NAME="$2"
CMD="$3"
echo "using gcp project: $PROJECT_ID"
echo "using instance name: $INSTANCE_NAME"

sudo_run() {
  gcloud --project="$PROJECT_ID" compute ssh "$INSTANCE_NAME" --zone=us-central1-a --command="echo '$1' | sudo su"
}

case "$CMD" in
  start)
    echo "=> starting..."
    sudo_run "docker-compose -f /app/squid-docker-compose.yaml -p ddp up -d && docker ps -a"
    ;;
  stop)
    echo "=> stopping..."
    sudo_run "docker-compose -f /app/squid-docker-compose.yaml -p ddp down"
    ;;
  status)
    echo "=> checking status..."
    sudo_run "docker ps -a"
    ;;
  tail-logs)
    echo "=> tailing logs..."
    sudo_run "docker-compose -f /app/squid-docker-compose.yaml -p ddp logs -f"
    ;;
  tail-access)
    echo "=> tailing access logs..."
    sudo_run "tail -f /var/log/squid/access.log"
    ;;
  *)
    echo "unrecognized command: $CMD"
    exit 1
    ;;
esac
