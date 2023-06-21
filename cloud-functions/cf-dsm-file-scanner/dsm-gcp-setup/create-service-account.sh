#!/bin/bash

if [ -z "$1" ]; then
  echo "Please provide the project name as an argument."
  echo "Usage: ./create-service-account.sh <project_name>"
  exit 1
fi

# Set the input parameters
PROJECT_ID="$1"

SERVICE_ACCOUNT_NAME="cf-dsm-somatic-file-scanner"
SECRET_NAME="$SERVICE_ACCOUNT_NAME-key"

gcloud config set project "$PROJECT_ID" &>/dev/null

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

      echo "updating the service account's IAM policy"
      # Assign necessary roles to the service account
      # This outputs a list of all the users/members and their roles as auditconfigs
      gcloud projects add-iam-policy-binding --quiet "$PROJECT_ID" \
          --member="serviceAccount:$SERVICE_ACCOUNT_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
          --role="roles/cloudfunctions.admin"

      gcloud projects add-iam-policy-binding  "$PROJECT_ID" \
          --member="serviceAccount:$SERVICE_ACCOUNT_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
          --role="roles/secretmanager.secretAccessor"

      gcloud projects add-iam-policy-binding "$PROJECT_ID" \
          --member="serviceAccount:$SERVICE_ACCOUNT_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
          --role="roles/pubsub.publisher"

      gcloud projects add-iam-policy-binding "$PROJECT_ID" \
          --member="serviceAccount:$SERVICE_ACCOUNT_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
          --role="roles/pubsub.subscriber"

      gcloud projects add-iam-policy-binding $PROJECT_ID\
        --member="serviceAccount:$SERVICE_ACCOUNT_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
        --role=roles/storage.objectAdmin

      echo "Roles assigned to the service account: $SERVICE_ACCOUNT_NAME@$PROJECT_ID.iam.gserviceaccount.com"

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
