#!/bin/bash

# Check if project name and study name is provided
if [ -z "$1" ] || [ -z "$2" ]; then
  echo "Please provide the project name and study name as an argument."
  echo "Usage: ./dsm-setup.sh <env> <study_name>"
  exit 1
fi

# Set project name
project_name="broad-ddp-$1"
study_name="$2"


# check study name is valid
if [ "$study_name" != "cmi-lms" ] && [ "$study_name" != "cmi-osteo2" ]; then
  echo "Please provide a valid study_name," $2 "is not valid"
  echo "this script only works with cmi-lms or cmi-osteo2"
  exit 1
fi


echo "project name is $project_name"
echo "study name is $study_name"

## Call the create_bucket.sh script to create the bucket
echo "setting the initial bucket for uploaded files"
./create-bucket.sh "$project_name" "$study_name-uploaded-files"

## Call the create_bucket.sh script to create the bucket
echo "setting the final bucket for scanned files"
./create-bucket.sh "$project_name" "$study_name-files"

trigger_topic_name="$study_name"-file-scanner-trigger
subscription_name="$study_name"-file-scanner-trigger-sub

# Call the create_topic_and_sub.sh script to create the pubsub topic that triggers the FileScanner
echo "creating the $trigger_topic_name pubsub topic that triggers the FileScanner"
./create_topic_and_sub.sh $project_name $trigger_topic_name $subscription_name

scan_result_topic_name="dsm-file-antivirus-result"
scan_result_subscription_name="dsm-file-antivirus-result-sub"

# Call the create_topic_and_sub.sh script to create the pubsub topic that has the scanning result
echo "creating the $scan_result_topic_name pubsub topic that triggers the FileScanner"
./create_topic_and_sub.sh $project_name $scan_result_topic_name $scan_result_subscription_name

#set the event for the OBJECT_FINALIZE event in the bucket
echo "creating the event from bucket $study_name-uploaded-files to this topic for OBJECT_FINALIZE"
./init-bucket-event.sh $project_name $study_name-uploaded-files $trigger_topic_name

results_topic_name="dsm-file-scanner-results"
subscription_name="$results_topic_name"-sub

#create the results topic and subscription if they don't exist
echo "creating the results topic and subscription if they don't exist"
./create_topic_and_sub.sh $project_name $results_topic_name $subscription_name

echo "setting up the service account if it doesn't exist"
./create-service-account.sh $project_name $study_name
