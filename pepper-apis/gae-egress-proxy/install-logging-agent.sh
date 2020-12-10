#!/usr/bin/env bash
#
# Installs the Google Cloud logging agent (fluentd).

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
gcloud --project="$PROJECT_ID" compute ssh "$INSTANCE_NAME" --zone=us-central1-a \
  --command="echo '=> adding logging agent repo...' \
    && curl -sSO https://dl.google.com/cloudagents/add-logging-agent-repo.sh \
    && sudo bash add-logging-agent-repo.sh \
    && rm add-logging-agent-repo.sh \
    && sudo apt-get update \
    && echo '' \
    && echo '=> adding logging agent...' \
    && sudo apt-get install -y 'google-fluentd=1.*' \
    && sudo apt-get install -y google-fluentd-catch-all-config \
    && echo '' \
    && echo '=> starting logging agent...' \
    && sudo service google-fluentd start \
    && sudo service google-fluentd status"

echo ""
echo "=> done!"
