#!/usr/bin/env bash

set -o errexit
set -o pipefail

# Configuration file used throughout script
CONFIG=''

# Variables used throughout script
INSTANCE_ID=''
BUCKET_NAME=''

confirm() {
  local msg="$1"
  read -r -p "$msg" reply
  reply=$(echo "$reply" | tr '[:upper:]' '[:lower:]')
  if [[ "$reply" == 'y' ]] || [[ "$reply" == 'yes' ]]; then
    return 0
  else
    return 1
  fi
}

pause() {
  read -r -p 'Press enter to continue... ' ignored
  return 0
}

set_google_project() {
  local name=$(jq -r '.gcp_project' "$CONFIG")
  gcloud config set project "$name"
}

async_create_instance() {
  local root_pw=$(jq -r '.root_password' "$CONFIG")
  local addl_flags=$(jq -r '.instance_create_flags[]' "$CONFIG" | tr '\n' ' ')

  echo 'Asynchronously creating new Cloud SQL instance'
  echo "Instance ID: $INSTANCE_ID"
  echo "Root password: $root_pw"
  echo "Additional creation flags: $addl_flags"
  echo ''

  set -o xtrace
  gcloud sql instances create "$INSTANCE_ID" \
    --root-password="$root_pw" --async \
    $addl_flags
  set +x
}

create_db_user() {
  local app="$1"
  local name=$(jq -r ".$app.username" "$CONFIG")
  local pass=$(jq -r ".$app.password" "$CONFIG")
  gcloud sql users create "$name" --host='%' --password="$pass" --instance="$INSTANCE_ID"
}

create_bucket() {
  if gsutil ls "gs://$BUCKET_NAME" >/dev/null 2>&1; then
    echo "Bucket with name '$BUCKET_NAME' already exist, reusing"
    return 0
  fi
  gsutil mb -c standard -l us-central1 "gs://$BUCKET_NAME"
}

grant_bucket_read() {
  local instance="$1"
  local description=$(gcloud sql instances describe "$instance" --format=json 2>/dev/null)
  local sa_email=$(echo "$description" | jq -r '.serviceAccountEmailAddress')
  echo "Granting read permission for instance '$instance' to bucket '$BUCKET_NAME'"
  gsutil acl ch -r -u "$sa_email:READ" "gs://$BUCKET_NAME"
}

grant_bucket_write() {
  local instance="$1"
  local description=$(gcloud sql instances describe "$instance" --format=json 2>/dev/null)
  local sa_email=$(echo "$description" | jq -r '.serviceAccountEmailAddress')
  echo "Granting write permission for instance '$instance' to bucket '$BUCKET_NAME'"
  gsutil acl ch -u "$sa_email:WRITE" "gs://$BUCKET_NAME"
}

cleanup_bucket() {
  local msg='Remove bucket '$BUCKET_NAME' and all its content? (y/n): '
  if confirm "$msg"; then
    gsutil rm -r "gs://$BUCKET_NAME"
  fi
}

async_export() {
  local app="$1"
  local schema=$(jq -r ".$app.schema" "$CONFIG")
  local old_instance_id=$(jq -r ".$app.old_instance_id" "$CONFIG")
  local bucket_file="gs://$BUCKET_NAME/$schema.sql"

  echo "Asynchronously exporting data"
  echo "Schema: $schema"
  echo "Instance: $old_instance_id"
  echo "Destination: $bucket_file"

  gcloud sql export sql "$old_instance_id" "$bucket_file" --database="$schema" --async
}

import_data() {
  local app="$1"
  local schema=$(jq -r ".$app.schema" "$CONFIG")
  local bucket_file="gs://$BUCKET_NAME/$schema.sql"
  echo "Importing data for schema '$schema'"
  gcloud sql import sql "$INSTANCE_ID" "$bucket_file"
}

main() {
  if (( $# < 1 )) || [[ ! -f "$1" ]]; then
    echo 'Missing configuration file'
    exit 1
  fi
  CONFIG="$1"

  echo "Using config file: $CONFIG"
  echo ''
  echo 'This script will create a new sql instance, export data from old instances,'
  echo 'and import them into the new instance.'
  echo ''
  echo 'Please make sure to have the `gcloud`, `gsutil`, and `jq` tools installed.'
  echo ''
  if confirm 'Continue? (y/n): '; then
    echo 'Moving on...'
  else
    echo 'Exiting...'
    exit 1
  fi

  INSTANCE_ID=$(jq -r '.instance_id' "$CONFIG")
  BUCKET_NAME=$(jq -r '.bucket' "$CONFIG")
  echo "Start time: $(date -u)"

  printf '\n=> Setting GCP project\n'
  set_google_project

  echo ''
  echo 'To ensure gcloud SDK has the sufficient permissions, the gcloud auth flow'
  echo 'will be triggered, which will open a browser window. Please authenticate'
  echo 'with a user that has the necessary permissions.'
  echo ''
  pause
  gcloud auth login

  printf '\n=> Creating bucket and granting permissions\n'
  pause

  create_bucket
  echo ''
  grant_bucket_write $(jq -r '.pepper.old_instance_id' "$CONFIG")
  grant_bucket_write $(jq -r '.housekeeping.old_instance_id' "$CONFIG")
  grant_bucket_write $(jq -r '.dsm.old_instance_id' "$CONFIG")

  printf '\n=> Creating sql instance and export dumps\n'
  pause

  if confirm "Create new instance '$INSTANCE_ID'? (y/n): "; then
    async_create_instance
  fi
  echo ''
  async_export 'pepper'
  echo ''
  async_export 'housekeeping'
  echo ''
  async_export 'dsm'

  echo ''
  echo 'Please open Google Cloud console to check sql instance is created.'
  echo 'And check that export dumps has landed in bucket before continuing.'
  pause

  printf '\n=> Creating database users\n'
  create_db_user 'pepper'
  echo ''
  create_db_user 'housekeeping'
  echo ''
  create_db_user 'dsm'

  printf '\n=> Granting permissions and importing data\n'
  pause

  grant_bucket_read "$INSTANCE_ID"
  echo ''
  echo "Start: $(date -u)"
  import_data 'pepper'
  echo "End: $(date -u)"
  echo ''
  echo "Start: $(date -u)"
  import_data 'housekeeping'
  echo "End: $(date -u)"
  echo ''
  echo "Start: $(date -u)"
  import_data 'dsm'
  echo "End: $(date -u)"

  printf '\n=> Cleaning up bucket\n'
  cleanup_bucket

  printf '\n=> Done!\n'
  echo "End time: $(date -u)"
  echo "Instance '$INSTANCE_ID' is ready to go!"
}

main "$@"
