#!/usr/bin/env bash
#
# Script to prepare and deploy function.
#
# Exit codes:
#   1: Bad usage (improper or missing arguments)
#   2: Missing cli tools
#   -1 or 255: subprocess failure- most likely gcloud
#
# Misc notes:
#   * The IAM policy updates & describe calls generally have their
#     stdout redirected to /dev/null as they can be quite noisy.
#

set -euo pipefail
IFS=$'\n\t'

if [ -z "$1" ]; then
  echo "Please provide the project name as an argument."
  echo "Usage: ./cf-deploy.sh <env>"
  exit 1
fi

# Set the input parameters
PROJECT_ID="broad-ddp-$1"

if ! command -v jq &> /dev/null; then
  echo "jq could not be found in PATH"
  exit 2
fi

if ! command -v gcloud &> /dev/null; then
  echo "gcloud could not be found in PATH"
  exit 2
fi


SERVICE_NAME="dsm-somatic-file-scanner"
CLOUDSDK_RUN_REGION="${CLOUDSDK_RUN_REGION:-"us-central1"}"

SECRET_ID="somatic-file-scanner"
SECRET_VERSION="latest"

##
# Topic name the service subscribes to listen for google.storage.object.finalize events
##
LISTEN_TOPIC_NAME="file-scanner-trigger"
LISTEN_DEAD_LETTER_TOPIC_NAME="${LISTEN_DEAD_LETTER_TOPIC_NAME:-"$LISTEN_TOPIC_NAME-dead-letter"}"
LISTEN_SUBSCRIPTION_NAME="file-scanner-trigger-sub"

##
# Topic name the service will publish scan results to
##
RESULT_TOPIC_NAME="dsm-file-scanner-results"

##
# The service account the service should run as
##
SA_NAME="cf-dsm-somatic-file-scanner"
SERVICE_ACCOUNT="$SA_NAME@$PROJECT_ID.iam.gserviceaccount.com"

##
# Used in the gcloudw function to provide global arguments
# for all gcloud invocations. Be aware that a number of gcloud
# command do redirect their output to /dev/null so errors
# in these flags may not be immediately clear.
##
OTHER_GCLOUD_FLAGS="--quiet"

##
# file-scanner container selection
#
# CONTAINER_NAME and CONTAINER_VERSION are sourced from the
#  current environment and can be overriden to provide
#  more control over the source image to deploy.
#
# CONTAINER_REGISTRY assumes that the Google Artifact Registry is going to be
# used to house the images, but there's no strong requirement for this.
# If an alternate registry is desired, the most straightforward path would
# be to allow the `CONTAINER_REGISTRY` pattern to be overridden.
#
# CONTAINER_FQ_NAME -> Container Fully-Qualified Name
##
CONTAINER_NAME="${CONTAINER_NAME:-$SERVICE_NAME}"
CONTAINER_VERSION="${CONTAINER_VERSION:-latest}"
CONTAINER_REGISTRY="$CLOUDSDK_RUN_REGION-docker.pkg.dev/$PROJECT_ID/dss"
CONTAINER_FQ_NAME="$CONTAINER_REGISTRY/$CONTAINER_NAME:$CONTAINER_VERSION"

function gcloudw {
  gcloud "$@" $OTHER_GCLOUD_FLAGS
}

function check-for-image {
  echo $CONTAINER_FQ_NAME
  gcloudw container images describe "$CONTAINER_FQ_NAME" &> /dev/null
}

function check-for-service-account {
  gcloudw iam service-accounts describe "$SERVICE_ACCOUNT" &> /dev/null
}

function create-service-account {
  gcloudw iam service-accounts create "$SERVICE_ACCOUNT" \
    --display-name="gsc-$SERVICE_NAME" \
    --description="Service Account for the $SERVICE_NAME service" 1>/dev/null

}

function access-secret {
  ##
  # Intentially not redirecting the output here as its going
  # to be the value of the secret and we want to be able to
  # pipe & manipulate the stdout
  ##
  gcloudw secrets versions access "$SECRET_VERSION" \
    --project="$PROJECT_ID" \
    --secret="$SECRET_ID"
}

function check-for-subscription-topic {
  gcloudw pubsub topics describe "$LISTEN_TOPIC_NAME"  &> /dev/null
}

function check-for-result-topic {
  gcloudw pubsub topics describe "$RESULT_TOPIC_NAME"  &> /dev/null
}

function add-subscriber-iam-binding {
  gcloudw pubsub topics add-iam-policy-binding "$LISTEN_TOPIC_NAME" \
    --member "serviceAccount:$SERVICE_ACCOUNT" \
    --role "roles/pubsub.subscriber" 1> /dev/null
}

function add-result-publisher-iam-binding {
  gcloudw pubsub topics add-iam-policy-binding "$RESULT_TOPIC_NAME" \
    --member "serviceAccount:$SERVICE_ACCOUNT" \
    --role "roles/pubsub.publisher" 1> /dev/null
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
  gcloudw run deploy "$SERVICE_NAME" \
    --service-account="$SERVICE_ACCOUNT" \
    --region="$CLOUDSDK_RUN_REGION" \
    --platform="managed" \
    --cpu="1" \
    --memory="4Gi" \
    --image="$CONTAINER_FQ_NAME" \
    --min-instances="1" \
    --max-instances="3" \
    --env-vars-file="env.yaml" \
    --update-secrets=ENV="$SECRET_ID:$SECRET_VERSION" \
    --ingress="internal-and-cloud-load-balancing" \
    --allow-unauthenticated \
    --no-cpu-throttling \
    --port="80"
}

function setup-dead-letter-topic {
  if ! gcloudw pubsub topics describe "$LISTEN_DEAD_LETTER_TOPIC_NAME"  &> /dev/null; then
    gcloudw pubsub topics create "$LISTEN_DEAD_LETTER_TOPIC_NAME" \
      --message-retention-duration=7d \
      --message-storage-policy-allowed-regions="$CLOUDSDK_RUN_REGION"
  fi
}

function check-for-listen-subscription {
  gcloudw pubsub subscriptions describe "$LISTEN_SUBSCRIPTION_NAME" &> /dev/null
}

function setup-listen-subscription {
  local service_url=$1

  gcloudw pubsub subscriptions create "$LISTEN_SUBSCRIPTION_NAME" \
    --topic="$LISTEN_TOPIC_NAME" \
    --topic-project="$PROJECT_ID" \
    --ack-deadline="60" \
    --enable-message-ordering \
    --expiration-period="never" \
    --push-endpoint="$service_url" \
    --max-delivery-attempts="5" \
    --dead-letter-topic="$LISTEN_DEAD_LETTER_TOPIC_NAME" \
    --dead-letter-topic-project="$PROJECT_ID" \
    --min-retry-delay="10s" \
    --max-retry-delay="5m"
}

function add-dead-letter-topic-roles {
  ##
  # In order to Acknowledge and Publish messages from the listen subscription
  # to the dead letter topic, the project pubsub service accounts needs
  # the pubsub.subscriber role on the listen subscription, and the
  # pubsub.publisher role on the dead lotter topic
  ##
  local project_number="$(gcloudw projects describe $PROJECT_ID --format=json | jq -r .projectNumber)"
  local project_sa="service-${project_number}@gcp-sa-pubsub.iam.gserviceaccount.com"

  gcloudw pubsub subscriptions add-iam-policy-binding "$LISTEN_SUBSCRIPTION_NAME" \
    --member "serviceAccount:$project_sa" \
    --role "roles/pubsub.subscriber" 1> /dev/null

  gcloudw pubsub topics add-iam-policy-binding "$LISTEN_DEAD_LETTER_TOPIC_NAME" \
    --member "serviceAccount:$project_sa" \
    --role "roles/pubsub.publisher" 1> /dev/null
}

function main {
  ##
  # Walk through some of the critical things we can check
  # which don't result in modifications to the project.
  ##

  ./dsm-setup.sh $1 $RESULT_TOPIC_NAME

  if ! check-for-image; then
    echo "Failed to locate a valid image for $CONTAINER_FQ_NAME. Ensure the repository exists and the name is correct."
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
    echo "Service account $SERVICE_ACCOUNT does not exist. Attempting to create it..."
    create-service-account
  else
    echo "Service account $SERVICE_ACCOUNT found"
  fi

  ##
  # I tested the two following IAM binding adds, and gcloud will
  # still exit with a success if the role already exists. This
  # has no other side effects if the binding does not need to be
  # modified, so it's safe to call repeatedly.
  ##
  if ! add-subscriber-iam-binding; then
    echo "failed to grant the pubsub.subscriber role to $SERVICE_ACCOUNT on topic $LISTEN_TOPIC_NAME"
    exit -1
  fi

  if ! add-result-publisher-iam-binding; then
    echo "failed to grant the pubsub.publisher role to $SERVICE_ACCOUNT on topic $RESULT_TOPIC_NAME"
    exit -1
  fi

  ##
  # Deploys a new version of a service. Keep an eye on this spot as
  # I'm currently unsure of how the deployment will response if
  # the same image is already deployed, and the invocation is identical
  # to the previous one
  ##
  echo "Deploying $CONTAINER_FQ_NAME to service $SERVICE_NAME."
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
    echo "failed to setup topic $LISTEN_DEAD_LETTER_TOPIC_NAME"
    exit -1
  fi

  ##
  # This needs a check-then-create pattern since attempting to create a subscription
  # when one already exists with the same name results in an error.
  #
  # This section assumes that once a subscription is created, it won't need other changes
  # after the fact. If this ends up not being the case, this should likely be changed to
  # a delete-then-create flow.
  ##
  if ! check-for-listen-subscription; then
    local service_url="$(gcloud run services describe $SERVICE_NAME --format=json --region=$CLOUDSDK_RUN_REGION | jq -r '.status.address.url')"
    echo "Service URL: $service_url"
    if ! setup-listen-subscription "$service_url"; then
      echo "failed to create a subscriber to topic $LISTEN_TOPIC_NAME"
      exit -1
    fi
  else
    echo "subscription named $LISTEN_SUBSCRIPTION_NAME already exists, skipping creation"
  fi

  echo "Updating roles for the topic $LISTEN_DEAD_LETTER_TOPIC_NAME"
  add-dead-letter-topic-roles

  echo "$SERVICE_NAME deployment was successful"
  return 0
}

main "$@"