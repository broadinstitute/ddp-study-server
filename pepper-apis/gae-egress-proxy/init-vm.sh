#!/usr/bin/env bash
#
# Creates a VM to act as a proxy for GAE outbound traffic.

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
echo "=> reserving static external ip..."
gcloud --project="$PROJECT_ID" compute addresses create "$INSTANCE_NAME" --region=us-central1
gcloud --project="$PROJECT_ID" compute addresses describe "$INSTANCE_NAME" --region=us-central1

echo ""
echo "=> creating vm instance..."
gcloud --project="$PROJECT_ID" compute instances create "$INSTANCE_NAME" \
  --zone=us-central1-a \
  --machine-type=g1-small \
  --image-project=ubuntu-os-cloud \
  --image-family=ubuntu-minimal-2004-lts \
  --tags=gae-egress-proxy \
  --network=default \
  --address="$INSTANCE_NAME"
gcloud --project="$PROJECT_ID" compute instances describe "$INSTANCE_NAME" --zone=us-central1-a

echo ""
echo "=> setting up docker in vm..."
gcloud --project="$PROJECT_ID" compute ssh "$INSTANCE_NAME" --zone=us-central1-a \
  --command="apt-get update && apt-get install -y docker.io && apt-get install -y docker-compose && mkdir /app' | sudo su"

echo ""
echo "=> done!"
