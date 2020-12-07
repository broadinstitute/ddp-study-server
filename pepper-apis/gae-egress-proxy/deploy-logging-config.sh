#!/usr/bin/env bash
#
# Deploys logging configuration file to proxy VM.

NAME=$(basename "$0")
if (( $# < 1 )); then
  echo "usage: $NAME <PROJECT_ID>"
  exit 1
fi

PROJECT_ID="$1"
ENV_NAME="${PROJECT_ID/broad-ddp-/}"
INSTANCE_NAME="gae-egress-proxy-${ENV_NAME}101"

echo "using gcp project: $PROJECT_ID"
echo "using instance name: $INSTANCE_NAME"

echo ""
echo "=> copying fluentd config file to vm..."
gcloud --project="$PROJECT_ID" compute scp --zone=us-central1-a \
  fluentd-logging.conf "root@$INSTANCE_NAME:/etc/google-fluentd/config.d/squid.conf"

echo ""
echo "=> restarting logging agent..."
gcloud --project="$PROJECT_ID" compute ssh "$INSTANCE_NAME" --zone=us-central1-a \
  --command="sudo service google-fluentd restart && sudo service google-fluentd status"

echo ""
echo "=> done!"
