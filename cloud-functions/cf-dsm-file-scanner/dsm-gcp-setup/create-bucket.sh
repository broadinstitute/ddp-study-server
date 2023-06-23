#!/bin/bash

# Check if project name and bucket name are provided
if [ -z "$1" ] || [ -z "$2" ]; then
  echo "Please provide the project name and bucket name as arguments."
  echo "Usage: ./create_bucket.sh <project_name> <bucket_name>"
  exit 1
fi

# Set project and bucket names
project_name="$1"
bucket_name="$2"

# Set the project
gcloud config set project "$project_name"

# Check if the bucket exists
echo "Checking if the bucket $bucket_name exists"
bucket_exists=$(gsutil ls -p "$project_name" | grep -c "gs://$bucket_name")


if [[ $bucket_exists -ne 0 ]]; then
  echo "Bucket $bucket_name already exists"
  exit 0
fi

# Create the bucket
echo "creating the bucket $bucket_name"
gsutil mb -p "$project_name" -c regional -l us-central1 gs://"$bucket_name"

# Enable public access prevention
echo "Enabling public access prevention"
gcloud storage buckets update gs://"$bucket_name" --public-access-prevention

# Check if the bucket creation and access prevention were successful
if [ $? -eq 0 ]; then
  echo "Bucket $bucket_name created successfully with public access prevention."
else
  echo "Failed to create bucket $bucket_name or enable public access prevention."
fi
