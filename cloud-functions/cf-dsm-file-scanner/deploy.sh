#!/bin/bash

if [ -z "$1" ] || [ -z "$2" ]; then
  echo "Please provide the project name and study name as an argument."
  echo "Usage: ./deploy.sh <env> <study_name>"
  exit 1
fi

# Set the input parameters
PROJECT_ID="broad-ddp-$1"
STUDY="$2"
FUNCTION_NAME="$STUDY-scanner-test"
REGION="us-central1"
RUNTIME="java11"
TRIGGER_TOPIC="$STUDY-file-scanner-trigger"
SERVICE_ACCOUNT="cf-dsm-somatic-file-scanner"
SECRET_NAME="$STUDY-somatic-file-scanner"

# Retrieve the configuration from Secret Manager
CONFIG=$(gcloud secrets versions access latest --secret="$SECRET_NAME")

# Define the Cloud Function deployment
FUNCTION_BODY='{
  "name": "'"$FUNCTION_NAME"'",
  "runtime": "'"$RUNTIME"'",
  "entryPoint": "your.entry.point.ClassName",
  "availableMemoryMb": 256,
  "serviceAccountEmail": "'$SERVICE_ACCOUNT'@'$PROJECT_ID'.iam.gserviceaccount.com",
  "eventTrigger": {
    "eventType": "google.pubsub.topic.publish",
    "resource": "projects/'$PROJECT_ID'/topics/'$TRIGGER_TOPIC'"
  },
  "sourceArchiveUrl": "gs://your-bucket-name/your-cloud-function.jar",
  "environmentVariables": '$CONFIG'
}'

# Deploy the Cloud Function
echo "gcloud functions deploy "$FUNCTION_NAME" \
  --project="$PROJECT_ID" \
  --region="$REGION" \
  --source="$SOURCE_DIRECTORY" \
  --entry-point="org.broadinstitute.ddp.cf.DSMSomaticFileScanner" \
  --runtime="$RUNTIME" \
  --memory="256MB" \
  --service-account="$SERVICE_ACCOUNT@$PROJECT_ID.iam.gserviceaccount.com" \
  --trigger-topic="$TRIGGER_TOPIC" \
  --set-env-vars="$CONFIG""
