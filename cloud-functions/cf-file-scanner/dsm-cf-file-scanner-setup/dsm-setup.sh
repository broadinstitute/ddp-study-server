#!/bin/bash

# Check if project name and study name is provided
if [ -z "$1" ] || [ -z "$2" ]; then
  echo "Please provide the project name and study name as an argument."
  echo "Usage: ./dsm-setup.sh <project_name> <study_name>"
  exit 1
fi

# Set project name
project_name="$1"
study_name="$2"


# check study name is valid
if [ "$study_name" != "cmi-lms" ] && [ "$study_name" != "cmi-osteo2" ]; then
  echo "Please provide a valid study_name," $2 "is not valid"
  echo "this script only works with cmi-lms or cmi-osteo2"
  exit 1
fi


echo "project name is $project_name"
echo "study name is $study_name"

# Call the create_bucket.sh script to create the bucket
echo "setting the initial bucket for uploaded files"
$study_name/create-bucket.sh "$project_name" "$study_name-uploaded-files"

# Call the create_bucket.sh script to create the bucket
echo "setting the final bucket for scanned files"
$study_name/create-bucket.sh "$project_name" "$study_name-files"
