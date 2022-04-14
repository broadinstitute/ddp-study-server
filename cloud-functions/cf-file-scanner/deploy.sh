#!/usr/bin/env bash
#
# Script to prepare and deploy function.

set -e

if (( $# != 1 )); then
  echo 'usage: deploy.sh <GCP_PROJECT>'
  exit 1
fi

PROJECT_ID="$1"
SECRET_ID='cf-file-scanner'
TOPIC_NAME='cf-file-scanner'
CF_NAME='cf-file-scanner'
SA_ACCT="cf-file-scanner@$PROJECT_ID.iam.gserviceaccount.com"

./build-clamav.sh

echo 'Rendering env vars file...'
gcloud --project=$PROJECT_ID \
  secrets versions access latest \
  --secret=$SECRET_ID > env.yaml

echo 'Uploading function...'
echo 'Note: it might take a few minutes to upload clamav binaries.'

echo ''
gcloud --project=$PROJECT_ID functions deploy $CF_NAME \
  --entry-point org.broadinstitute.ddp.cf.FileScanner \
  --trigger-topic $TOPIC_NAME \
  --service-account $SA_ACCT \
  --env-vars-file env.yaml \
  --region us-central1 \
  --runtime java11 \
  --memory 2048MB \
  --timeout 5m \
  --retry
