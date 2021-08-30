#!/usr/bin/env bash
#
# Script to prepare and deploy function.

set -e

if (( $# != 1 )); then
  echo 'usage: deploy.sh <GCP_PROJECT>'
  exit 1
fi

PROJECT_ID="$1"
SECRET_ID='cf-atcp-emailer'
CF_NAME='cf-atcp-emailer'
SA_ACCT="cf-atcp-emailer@$PROJECT_ID.iam.gserviceaccount.com"
TOPIC='cf-file-scan-result'

echo 'Rendering env vars file...'
gcloud --project=$PROJECT_ID \
  secrets versions access latest \
  --secret=$SECRET_ID > env.yaml

gcloud --project=$PROJECT_ID functions deploy $CF_NAME \
  --entry-point org.broadinstitute.ddp.cf.ATCPContactEmailer \
  --trigger-topic $TOPIC \
  --service-account $SA_ACCT \
  --env-vars-file env.yaml \
  --region us-central1 \
  --max-instances 3 \
  --runtime java11 \
  --memory 256MB \
  --timeout 5m \
