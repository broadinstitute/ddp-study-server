#!/bin/bash

# Check if project name and study name is provided
if [ -z "$1" ] || [ -z "$2" ] ; then
  echo "Please provide the environment and RESULT_TOPIC_NAME as an argument."
  echo "Usage: ./dsm-setup.sh <env> <RESULT_TOPIC_NAME>"
  exit 1
fi

# Set project name
deployment_environment=$1
project_name="broad-ddp-$deployment_environment"
lms_study_name="cmi-lms"
os2_study_name="cmi-osteo2"
RESULT_TOPIC_NAME=$2


gcloud config set project $project_name

echo "project name is $project_name"


## Call the create_bucket.sh script to create the bucket
echo "setting the initial bucket for uploaded files for $lms_study_name"
./create-bucket.sh "$project_name" "$lms_study_name-uploaded-files-$deployment_environment"

## Call the create_bucket.sh script to create the bucket
echo "setting the initial bucket for uploaded files for $os2_study_name"
./create-bucket.sh "$project_name" "$os2_study_name-uploaded-files-$deployment_environment"

## Call the create_bucket.sh script to create the bucket
echo "setting the final bucket for scanned files"

./create-bucket.sh "$project_name" "somatic-result-files-$deployment_environment"

trigger_topic_name="file-scanner-trigger"

# Call the create_topic_and_sub.sh script to create the pubsub topic that triggers the FileScanner
echo "creating the $trigger_topic_name pubsub topic that triggers the FileScanner"
./create_topic_and_sub.sh $project_name $trigger_topic_name ""


#set the event for the OBJECT_FINALIZE event in the bucket
echo "creating the event from bucket $lms_study_name-uploaded-files-$deployment_environment to this topic for OBJECT_FINALIZE"
./init-bucket-event.sh $project_name $lms_study_name-uploaded-files-$deployment_environment $trigger_topic_name

#set the event for the OBJECT_FINALIZE event in the bucket
echo "creating the event from bucket $os2_study_name-uploaded-files-$deployment_environment to this topic for OBJECT_FINALIZE"
./init-bucket-event.sh $project_name $os2_study_name-uploaded-files-$deployment_environment $trigger_topic_name

subscription_name="$RESULT_TOPIC_NAME"-sub

#create the results topic and subscription if they don't exist
echo "creating the results topic and subscription if they don't exist"
./create_topic_and_sub.sh $project_name $RESULT_TOPIC_NAME $subscription_name

echo "setting up the service account if it doesn't exist"
./create-service-account.sh $project_name

echo "Setting the cors config"
./cors-config.sh $1

echo "Setting permissions for the ddp-downloader Service Account"
./update-download-service-account.sh $project_name

docker build --platform linux/amd64 --tag "dsm-somatic-file-scanner-$deployment_environment" ..

docker tag "dsm-somatic-file-scanner-$deployment_environment:latest" "us-central1-docker.pkg.dev/broad-ddp-$deployment_environment/dss/dsm-somatic-file-scanner-$deployment_environment:latest"

gcloud auth print-access-token | docker login -u oauth2accesstoken --password-stdin https://us-central1-docker.pkg.dev

docker push "us-central1-docker.pkg.dev/broad-ddp-$deployment_environment/dss/dsm-somatic-file-scanner-$deployment_environment:latest"