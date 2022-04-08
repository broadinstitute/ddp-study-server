#!/usr/bin/env bash
#
# Script to prepare and deploy function.
#
# Exit codes:
#   1: Bad usage (improper or missing arguments)
#   2: Missing cli tools
#   -1 or 255: subprocess failure- most likely gcloud

set -euo pipefail
IFS=$'\n\t'

if (( $# != 1 )); then
  echo 'usage: deploy.sh <GCP_PROJECT>'
  exit 1
fi

if ! command -v jq &> /dev/null; then
  echo "jq could not be found in PATH"
  exit 2
fi

if ! command -v gcloud &> /dev/null; then
  echo "gcloud could not be found in PATH"
  exit 2
fi

PROJECT_ID="${1:-""}"
# gsc -> Google Serverless Compute
# open to alternate naming schemes
SERVICE_NAME="${SERVICE_NAME:-"gsc-file-scanner"}"
CLOUDSDK_RUN_REGION="${CLOUDSDK_RUN_REGION:-"us-central1"}"

SECRET_ID="${SECRET_ID:-$SERVICE_NAME}"
SECRET_VERSION="latest"

##
# Topic name the service subscribes to listen for google.storage.object.finalize events
##
LISTEN_TOPIC_NAME="${LISTEN_TOPIC_NAME:-$SERVICE_NAME}"
DEAD_LETTER_TOPIC_NAME="${DEAD_LETTER_TOPIC_NAME:-"$RESULT_TOPIC_NAME-dead-letter"}"

RESULT_TOPIC_NAME="${RESULT_TOPIC_NAME:-"cf-file-scan-result"}"
CF_NAME="$SERVICE_NAME"
SA_ACCT="$SERVICE_NAME@$PROJECT_ID.iam.gserviceaccount.com"

##
# file-scanner container
##
CONTAINER_BASE="$CLOUDSDK_RUN_REGION-docker.pkg.dev/$PROJECT_ID/dss/$SERVICE_NAME"
CONTAINER_TAG="latest"
CONTAINER_NAME="$CONTAINER_BASE:$CONTAINER_TAG"


function check-for-image {
  gcloud container images describe "$CONTAINER_NAME" &> /dev/null
}

function check-for-service-account {
  gcloud iam service-accounts describe "$SA_ACCT" &> /dev/null
}

function create-service-account {
  gcloud iam service-accounts create "$SA_ACCT" \
    --display-name="gsc-$SERVICE_NAME" \
    --description="Service Account for the $SERVICE_NAME service"
}

function access-secret {
  gcloud secrets versions access "$SECRET_VERSION" \
    --project="$PROJECT_ID" \
    --secret="$SECRET_ID"
}

function check-for-subscription-topic {
  gcloud pubsub topics describe "$LISTEN_TOPIC_NAME" &> /dev/null
}

function add-subscriber-iam-binding {
  gcloud pubsub topics add-iam-policy-binding "$LISTEN_TOPIC_NAME" \
    --member "serviceAccount:$SA_NAME" \
    --role "roles/pubsub.subscriber"
}

function add-publisher-iam-binding {
  gcloud pubsub topics add-iam-policy-binding "$RESULT_TOPIC_NAME" \
    --member "serviceAccount:$SA_NAME" \
    --role "roles/pubsub.publisher";
}

function deploy-service {
  ##
  # Take a look at the performance once this image is deployed.
  # The mix of ClamAV and Java were running into OOM errors with
  # a fair degree of frequency when using 2Gi. I traced the bulk
  # of the issue to the ConcurrentDatabaseReload config value
  # in clamd.conf and disabled it, but haven't performed thorough
  # testing with the reduced memory amount.
  # 
  # Try reducing the 4Gi to 2Gi below and see if things are still happy.
  # This system may also be able to get by with only a single CPU.
  #
  ##
  gcloud run deploy "$SERVICE_NAME" \
    --service-account="$SA_NAME" \
    --region="$CLOUDSDK_RUN_REGION" \
    --platform="managed" \
    --cpu="2" \
    --memory="4Gi" \
    --cpu-throttling \
    --image="$CONTAINER_NAME" \
    --min-instances="1" \
    --max-instances="3" \
    --env-var-file="env.yaml" \
    --update-secrets=ENV="$SECRET_ID:$SECRET_VERSION" \
    --ingress="internal-and-cloud-load-balancing" \
    --no-allow-unauthenticated \
    --port="80"
}

function setup-dead-letter-topic {
  if ! gcloud pubsub topics describe "$DEAD_LETTER_TOPIC_NAME"; then
    gcloud pubsub topics create "$DEAD_LETTER_TOPIC_NAME" \
      --message-retention-duration=7d \
      --message-storage-policy-allowed-regions="$CLOUDSDK_RUN_REGION"
  fi

  ## Ensure the permissions are correct for the service account
  gcloud pubsub topics add-iam-policy-binding \
    --member "serviceAccount:$SA_NAME" \
    --role "roles/pubsub.publisher"
}

function setup-listen-subscription {
  local service_url=$1

  gcloud pubsub subscriptions create "gsc-file-scanner-trigger" \
    --topic="$LISTEN_TOPIC_NAME" \
    --topic-project="$PROJECT_ID" \
    --ack-deadline="60" \
    --enable-message-ordering \
    --expiration-period="never" \
    --push-auth-service-account="$SA_NAME" \
    --push-endpoint="$service_url" \
    --max-delivery-attempts="5" \
    --dead-letter-topic="$DEAD_LETTER_TOPIC_NAME" \
    --dead-letter-topic-project="$PROJECT_ID" \
    --min-retry-delay="10s"
    --max-retry-delay="5m"
}

function main {
  ##
  # Walk through some of the critical things we can check
  # which don't result in modifications to the project.
  ##

  if ! check-for-image; then
    echo "Failed to locate a valid image for $CONTAINER_NAME. Ensure the repository exists and the name is correct."
    exit -1
  fi
  
  ##
  # The topic the service will subscribe to, and listen for
  # bucket object finalization events.
  # Required the pubsub.subscriber role
  ##
  if ! check-for-subscription-topic; then
    echo "PubSub topic $LISTEN_TOPIC_NAME does not exist"
    exit -1
  fi

  ##
  # The topic the service will publish scan results to.
  # Requires the pubsub.publisher role
  ##
  if ! check-for-result-topic; then
    echo "Pubsub topic $RESULT_TOPIC_NAME does not exist"
    exit -1
  fi

  ##
  # The secret is less critical than it was before. Most
  # of the properties embedded in the secret are required
  # for the setup process, so it's entirely feasible to flip
  # this process on its head and update the secret given
  # the parameters set in the deploy script
  ##
  if ! access-secret > env.yaml; then
    echo "Failed to access the secret named $SECRET_ID in $PROJECT_ID. Ensure the secret exists and you have access to it."
    exit -1
  fi

  ##
  # This is where the modifications start. I've attempted to arrange
  # the various steps in the order from least side-effects to most side-effects
  # except where dependencies require a different order (for example,
  # subscribing to a topic requires that the service be successfully deployed
  # first).
  ##
  if ! check-for-service-account; then
    echo "Service account $SA_ACCT does not exist. Attempting to create it..."
    create-service-account
  else
    echo "Service account $SA_ACCT found"
  fi

  ##
  # I tested the two following IAM binding adds, and gcloud will
  # still exit with a success if the role already exists. This
  # has no other side effects if the binding does not need to be
  # modified, so it's safe to call repeatedly.
  ##
  if ! add-subscriber-iam-binding; then
    echo "failed to grant the pubsub.subscriber role to $SA_NAME on topic $LISTEN_TOPIC_NAME"
    exit -1
  fi

  if ! add-result-publisher-iam-binding; then
    echo "failed to grant the pubsub.publisher role to $SA_NAME on topic $RESULT_TOPIC_NAME"
    exit -1
  fi

  ##
  # Deploys a new version of a service. Keep an eye on this spot as
  # I'm currently unsure of how the deployment will response if
  # the same image is already deployed, and the invocation is identical
  # to the previous one
  ##
  echo "Deploying $CONTAINER_NAME to service $SERVICE_NAME."
  if ! deploy-service; then
    echo "Deploy of $SERVICE_NAME failed."
    exit -1
  fi

  ##
  # Used in setting up the subscription for triggering the service
  # below.
  #
  # It may make a bit more sense to merge it into the
  # setup-listen-subscription sometime later, but it works here
  # for now in the interests of time.
  ##
  if ! setup-dead-letter-topic; then 
    echo "failed to setup topic $DEAD_LETTER_TOPIC_NAME"
  fi

  local service_url="$(gcloud run services describe $SERVICE_NAME --format=json | jq '.status.address.url')"
  if ! setup-listen-subscription "$service_url"; then
    echo "failed to create a subscriber to topic $LISTEN_TOPIC_NAME"
    exit -1
  fi

  echo "$SERVICE_NAME deployment was successful"
  return 0
}

main "$@"