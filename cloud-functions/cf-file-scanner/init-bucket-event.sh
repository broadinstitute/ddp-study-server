#!/usr/bin/env bash
#
# Currently, gcloud console does not support configuring notifications on
# buckets, so have to use gsutil command-line.

set -e

if (( $# != 3 )); then
  echo 'usage: init-bucket-event.sh <GCP_PROJECT> <BUCKET> <TOPIC>'
  exit 1
fi

PROJECT_ID="$1"
BUCKET="$2"
TOPIC="$3"

gsutil notification create \
  -f json \
  -e OBJECT_FINALIZE \
  -t "projects/$PROJECT_ID/topics/$TOPIC" \
  "gs://$BUCKET"

gsutil notification list "gs://$BUCKET"
