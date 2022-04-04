#!/usr/bin/env bash
#
# Script to prepare and deploy function.

set -euo pipefail
IFS=$'\n\t'

if (( $# != 1 )); then
  echo 'usage: deploy.sh <GCP_PROJECT>'
  exit 1
fi

if ! command -v jq &> /dev/null; then
  echo "jq could not be found in PATH"
  exit -1
fi

if ! command -v gcloud &> /dev/null; then
  echo "gcloud could not be found in PATH"
  exit -1
fi

PROJECT_ID="${1:-""}"
SERVICE_NAME="${SERVICE_NAME:-"cf-file-scanner"}"
CLOUDSDK_RUN_REGION="${CLOUDSDK_RUN_REGION:-"us-central1"}"

SECRET_ID="$SERVICE_NAME"
LISTEN_TOPIC_NAME="$SERVICE_NAME"
CF_NAME="$SERVICE_NAME"
SA_ACCT="$SERVICE_NAME@$PROJECT_ID.iam.gserviceaccount.com"
CONTAINER_BASE="$CLOUDSDK_RUN_REGION-docker.pkg.dev/$PROJECT_ID/dss/$SERVICE_NAME"
CONTAINER_TAG="latest"
CONTAINER_NAME="$CONTAINER_BASE:$CONTAINER_TAG"

function check-for-image {
  gcloud container images describe "$CONTAINER_NAME" &> /dev/null
}

function check-for-service-account {
  gcloud iam service-accounts describe "$SA_ACCT" &> /dev/null
}

function create-service-account {
  gcloud iam service-accounts create "$SA_ACCT" \
    --display-name="gsc-$SERVICE_NAME" \
    --description="Service Account for the $SERVICE_NAME service"
}

function check-for-secret {
  gcloud --project="$PROJECT_ID" secrets versions describe latest --secret="$SECRET_ID" &> /dev/null
}

function access-secret {
  gcloud secrets versions access latest \
    --project="$PROJECT_ID" \
    --secret=$SECRET_ID
}

function check-for-subscription-topic {
  gcloud pubsub topics describe "$LISTEN_TOPIC_NAME" &> /dev/null
}

function check-for-subscriber-role {
  gcloud pubsub topics get-iam-policy "$PROJECT_ID"
    --flatten="bindings[].members"
    --filter="bindings.members=serviceAccount:<serviceAccountId>"
    --format="value(bindings.role)"
}

function main {
  if ! check-for-image; then
    echo "Failed to locate a valid image for $CONTAINER_NAME. Ensure the repository exists and the name is correct."
    exit 2
  fi

  if ! check-for-service-account; then
    echo "Service account $SA_ACCT does not exist. Attempting to create it..."
    create-service-account
  else
    echo "Service account $SA_ACCT exists, skipping account creation"
  fi

  if ! check-for-secret; then
    echo "Failed to find the secret named $SECRET_ID in $PROJECT_ID. Ensure the secret exists and you have access to it."
    exit 2
  else
    access-secret > env.yaml
  fi

  ## Deploys a new version of a service
  if ! gcloud run services describe "$SERVICE_NAME" &> /dev/null; then
    echo "$SERVICE_NAME does not exist. Attempt to create it..."
    gcloud run deploy "$SERVICE_NAME" \
      --region="$CLOUDSDK_RUN_REGION" \
      --platform="managed" \
      --cpu="2" \
      --memory="4Gi" \
      --cpu-throttling \
      --image="$CONTAINER_NAME" \
      --min-instances="1" \
      --max-instances="3" \
      --env-var-file="env.yaml" \
      --update-secrets=ENV="$SECRET_ID:latest" \
      --ingress="internal-and-cloud-load-balancing" \
      --port="80"
  fi

  ## Create the PubSub Topic to listen on
  # Needs roles/pubsub.subscriber
  if ! check-for-subscription-topic; then
    echo "PubSub topic $LISTEN_TOPIC_NAME does not exist"
    exit 2
  fi
  

  if ! check-for-

  ## Create a subscription between the service & the new topic


  echo 'Rendering env vars file...'
  gcloud --project=$PROJECT_ID \
    secrets versions access latest \
    --secret=$SECRET_ID > env.yaml

  echo 'Uploading function...'
  echo 'Note: it might take a few minutes to upload clamav binaries.'

  echo ''
  gcloud --project=$PROJECT_ID functions deploy $CF_NAME \
    --entry-point org.broadinstitute.ddp.cf.FileScanner \
    --trigger-topic $LISTEN_TOPIC_NAME \
    --service-account $SA_ACCT \
    --env-vars-file env.yaml \
    --region us-central1 \
    --runtime java11 \
    --memory 2048MB \
    --timeout 5m \
    --retry
}