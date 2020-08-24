#!/usr/bin/env bash
#
# Upload files for Squid to gae-egress-proxy VM.

NAME=$(basename "$0")
if (( $# < 3 )); then
  echo "usage: $NAME <PROJECT_ID> <INSTANCE_NAME> <CONFIG_DIR>"
  exit 1
fi

PROJECT_ID="$1"
INSTANCE_NAME="$2"
CONFIG_DIR="$3"
echo "using gcp project: $PROJECT_ID"
echo "using instance name: $NAME"
echo "using config dir: $CONFIG_DIR"

echo ""
gcloud --project="$PROJECT_ID" compute ssh "$INSTANCE_NAME" \
  --zone=us-central1-a \
  --command="echo 'mkdir -p /app' | sudo su"
gcloud --project="$PROJECT_ID" compute scp \
  --zone=us-central1-a \
  "$CONFIG_DIR/squid-docker-compose.yaml" \
  "$CONFIG_DIR/squid.conf" \
  "root@$INSTANCE_NAME:/app"

echo ""
echo "=> done!"
