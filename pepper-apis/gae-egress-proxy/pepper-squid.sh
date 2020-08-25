#!/usr/bin/env bash
#
# Script for controlling squid proxy within VM.

NAME=$(basename "$0")
if (( $# < 3 )); then
  echo "usage: $NAME <PROJECT_ID> <INSTANCE_NAME> <deploy | start | stop | status | tail-logs | tail-access>"
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
  deploy)
    echo "=> uploading files to vm..."
    gcloud --project="$PROJECT_ID" compute scp --zone=us-central1-a \
      docker-compose.yaml "root@$INSTANCE_NAME:/app"
    sudo_run "docker-compose -f /app/docker-compose.yaml -p pepper pull
      && docker-compose -f /app/docker-compose.yaml -p pepper down
      && docker-compose -f /app/docker-compose.yaml -p pepper up -d"
    ;;
  start)
    echo "=> starting..."
    sudo_run "docker-compose -f /app/docker-compose.yaml -p pepper up -d && docker ps -a"
    ;;
  stop)
    echo "=> stopping..."
    sudo_run "docker-compose -f /app/docker-compose.yaml -p pepper down"
    ;;
  status)
    echo "=> checking status..."
    sudo_run "docker ps -a"
    ;;
  tail-logs)
    echo "=> tailing logs..."
    sudo_run "docker-compose -f /app/docker-compose.yaml -p pepper logs -f"
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
