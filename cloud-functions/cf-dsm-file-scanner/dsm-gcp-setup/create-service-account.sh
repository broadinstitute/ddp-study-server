#!/bin/bash

if [ -z "$1" ] || [ -z "$2" ]; then
  echo "Please provide the project name and study name as an argument."
  echo "Usage: ./create-service-account.sh <project_name> <study_name>"
  exit 1
fi

# Set the input parameters
PROJECT_ID="$1"
STUDY="$2"
SERVICE_ACCOUNT_NAME="cf-dsm-somatic-file-scanner"
SECRET_NAME="$SERVICE_ACCOUNT_NAME-key"

# Check if the service account already exists
EXISTING_ACCOUNT=$(gcloud iam service-accounts list \
    --project="$PROJECT_ID" \
    --filter="email:$SERVICE_ACCOUNT_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
    --format="value(email)")

if [[ -n "$EXISTING_ACCOUNT" ]]; then
  echo "Service account already exists: $EXISTING_ACCOUNT"
else
  # Create the service account
  gcloud iam service-accounts create "$SERVICE_ACCOUNT_NAME" \
      --project="$PROJECT_ID" \
      --display-name="DSM Somatic File Scanner"

  # Assign necessary roles to the service account
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
      --member="serviceAccount:$SERVICE_ACCOUNT_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
      --role="roles/cloudfunctions.admin"

  # Generate the service account key
  KEY=$(gcloud iam service-accounts keys create "$SECRET_NAME.json" \
      --iam-account="$SERVICE_ACCOUNT_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
      --project="$PROJECT_ID" \
      --format="json")

  gcloud secrets describe "$SECRET_NAME" --project="$PROJECT_ID" &>/dev/null

    if [[ $? -eq 0 ]]; then
      echo "Updating existing Secret Manager secret: $SECRET_NAME"
      echo "$KEY" | gcloud secrets versions add "$SECRET_NAME" \
          --data-file="$SECRET_NAME.json" \
          --project="$PROJECT_ID"
    else
      echo "Creating new Secret Manager secret: $SECRET_NAME"
      echo "$KEY" | gcloud secrets create "$SECRET_NAME" \
          --data-file="$SECRET_NAME.json" \
          --project="$PROJECT_ID"
    fi
fi
