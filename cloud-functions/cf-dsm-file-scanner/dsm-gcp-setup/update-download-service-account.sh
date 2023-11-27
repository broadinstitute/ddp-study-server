#!/bin/bash

if [ -z "$1" ]; then
  echo "Please provide the project name as an argument."
  echo "Usage: ./update-download-service-account <project_name>"
  exit 1
fi

# Set the input parameters
PROJECT_ID="$1"
SA_NAME="ddp-file-downloader"

gcloud config set project "$PROJECT_ID" &>/dev/null

EXISTING_ACCOUNT=$(gcloud iam service-accounts list \
    --project="$PROJECT_ID" \
    --filter="email:$SA_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
    --format="value(email)")

if [[ -n "$EXISTING_ACCOUNT" ]]; then
  echo "Service Account found!"
else
  # Create the service account
  echo "Service Account not found! YOU NEED TO CREATE IT!"
  exit -1
fi

echo "updating the service account's IAM policy"
# Assign necessary roles to the service account
# This outputs a list of all the users/members and their roles as auditconfigs
gcloud projects add-iam-policy-binding  "$PROJECT_ID" \
    --member="serviceAccount:$SA_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/storage.objectAdmin"

gcloud projects add-iam-policy-binding  "$PROJECT_ID" \
    --member="serviceAccount:$SA_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/storage.objectAdmin"


echo "Roles assigned to the service account:  $SA_NAME@$PROJECT_ID.iam.gserviceaccount.com"

