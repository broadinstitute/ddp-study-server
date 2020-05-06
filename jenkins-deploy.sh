#!/bin/bash

set -ex
#
# This script will fail if any of the environment variables
# referenced are not defined when this script starts up.
#


if [ -z "${SSHCMD}" ]; then
    echo "FATAL ERROR: SSHCMD undefined."
    exit 1
fi

if [ -z "${SSH_USER}" ]; then
    echo "FATAL ERROR: SSH_USER undefined."
    exit 2
fi

if [ -z "${SSH_HOST}" ]; then
    echo "FATAL ERROR: SSH_HOST undefined."
    exit 3
fi

if [ -z "${ENVIRONMENT}" ]; then
    echo "FATAL ERROR: ENV undefined."
    exit 4
fi

if [ -z "${VERSION}" ]; then
    echo "FATAL ERROR: VERSION undefined."
    exit 5
fi

if [ -z "${PROJECT}" ]; then
    echo "FATAL ERROR: PROJECT undefined."
    exit 6
fi
set -a

VAULT_TOKEN=$(cat /etc/vault-token-kdux)
OUTPUT_DIR=app
INPUT_DIR=$PROJECT/config
BUILD_CONTAINERS=false

# Custom compose for subprojects
if [ "$SUBPROJECT" = "housekeeping" ]; then
    COMPOSE_FILE="/$OUTPUT_DIR/housekeeping-docker-compose.yaml"
    MANIFEST=manifest.rb
else
    COMPOSE_FILE="/$OUTPUT_DIR/docker-compose.yaml"
    MANIFEST=manifest.rb
fi

# configure outside the kdux host, with retries in case vault times out
for i in {1..3}; do
  ruby pepper-apis/configure.rb -y && break || sleep 3
done

scp -r $SSHOPTS $OUTPUT_DIR/* $SSH_USER@$SSH_HOST:/$OUTPUT_DIR

#### Deploy ####

# Start new application container with the current version, pulling latest container images from the registry
$SSHCMD $SSH_USER@$SSH_HOST "docker-compose -p $PROJECT -f $COMPOSE_FILE pull"
$SSHCMD $SSH_USER@$SSH_HOST "docker-compose -p $PROJECT -f $COMPOSE_FILE stop"
$SSHCMD $SSH_USER@$SSH_HOST "docker-compose -p $PROJECT -f $COMPOSE_FILE rm -f"
$SSHCMD $SSH_USER@$SSH_HOST "docker-compose -p $PROJECT -f $COMPOSE_FILE up -d --remove-orphans"

# Remove any dangling images that might be hanging around
$SSHCMD $SSH_USER@$SSH_HOST "docker images -aq --no-trunc --filter dangling=true | xargs docker rmi || /bin/true"

if [[ $PROJECT = "pepper-apis" ]]; then
    if [[ "$SUBPROJECT" != "housekeeping" ]]; then
        # run the smoketest for pepper-apis deployments to ensure that the app didn't croak after docker-compose up
        echo "starting smoketest..."
        #docker pull docker.io/broadinstitute/pepper-api-backend:"$VERSION"_"$ENV"
        $SSHCMD $SSH_USER@$SSH_HOST "docker run --rm -v /app/application.conf:/app/config/application.conf -v /app/post_deploy_smoketest.sh:/app/post_deploy_smoketest.sh broadinstitute/pepper-api-backend:\"$VERSION\"_\"$ENVIRONMENT\" bash /app/post_deploy_smoketest.sh $SSH_HOST 60"
    fi
fi
