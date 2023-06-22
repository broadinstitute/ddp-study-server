#!/bin/bash


if [ -z "$1" ]; then
  echo "Please provide the project name and bucket name as arguments."
  echo "Usage: ./cors-config.sh <env>"
  exit 1
fi

# Set project and bucket names
env="$1"
lms_upload_bucket="cmi-lms-uploaded-files"
osteo2_upload_bucket="cmi-osteo2-uploaded-files"

dsm_url="https://dsm-$env.datadonationplatform.org/*"

echo "environment is set to $env"

if [[ env = "prod" ]]; then
  echo "setting CORS config for prod"
  dsm_url="https://dsm.datadonationplatform.org/*"
fi

upload_json="[{\"origin\": [\"$dsm_url\"],\"method\": [\"PUT\"],\"responseHeader\": [\"access-control-allow-credentials\",
\"access-control-allow-origin\",\"content-type\",\"withcredentials\"],\"maxAgeSeconds\": 3600}]"


if [[ $env != "dev" ]]; then
  echo $upload_json > "upload_json_$env.json"
fi

gsutil cors set upload_json_$env.json gs://"$lms_upload_bucket"
gsutil cors set upload_json_$env.json gs://"$osteo2_upload_bucket"

download_bucket="somatic-result-files"

download_json="[{\"origin\": [\"$dsm_url\"],\"method\": [\"GET\"],\"responseHeader\": [\"access-control-allow-credentials\",
\"access-control-allow-origin\",\"content-type\",\"withcredentials\"],\"maxAgeSeconds\": 3600}]"

echo $download_json > "download_json_$env.json"

gsutil cors set download_json_$env.json gs://"$download_bucket"