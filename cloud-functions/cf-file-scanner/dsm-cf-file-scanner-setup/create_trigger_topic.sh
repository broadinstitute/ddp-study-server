#!/bin/bash

# Check if project name, topic name, and subscription name are provided as command-line arguments
if [ $# -lt 3 ]; then
  echo "Usage: $0 <project_name> <topic_name> <subscription_name>"
  exit 1
fi

# Extract project name, topic name, and subscription name from command-line arguments
project_name=$1
topic_name=$2
subscription_name=$3

# Create new Pub/Sub topic
gcloud pubsub topics create "$topic_name" --project "$project_name"

# Create new pull subscription for the topic
gcloud pubsub subscriptions create "$subscription_name" --topic "$topic_name" --project "$project_name"
