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

# Check if the results topic is already created
topic_exists=$(gcloud pubsub topics describe "$topic_name" --project "$project_name" 2>/dev/null)

# Check if the subscription exists
subscription_exists=$(gcloud pubsub subscriptions describe "$subscription_name" --project "$project_name" 2>/dev/null)

# Create the topic if it doesn't exist
if [ -z "$topic_exists" ]; then
  gcloud pubsub topics create "$topic_name" --project "$project_name"
else
  echo "Topic '$topic_name' already exists."
fi

# Create the subscription if it doesn't exist
if [ -z "$subscription_exists" ]; then
  gcloud pubsub subscriptions create "$subscription_name" --topic "$topic_name" --project "$project_name"
else
  echo "Subscription '$subscription_name' already exists."
fi
