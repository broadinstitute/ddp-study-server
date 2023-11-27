#!/usr/bin/env bash
#
# Currently, gcloud console does not support configuring notifications on
# buckets, so have to use gsutil command-line.

if (( $# != 3 )); then
  echo 'usage: init-bucket-event.sh <GCP_PROJECT> <BUCKET> <TOPIC>'
  exit 1
fi

PROJECT_ID="$1"
BUCKET="$2"
TOPIC="$3"

# Check if the notification already exists
echo "Check if the notification already exists from $BUCKET to $TOPIC"

existing_notification=" $(gsutil notification list gs://$BUCKET | grep -w "/$TOPIC")"

if [[ "$existing_notification" = " " ]]; then
  gsutil notification create -f json -e OBJECT_FINALIZE -t "projects/$PROJECT_ID/topics/$TOPIC" "gs://$BUCKET";
  echo "Notification added successfully.";
else
  echo "Notification already exists.";
fi


gsutil notification list "gs://$BUCKET"
