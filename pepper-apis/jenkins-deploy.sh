#!/bin/bash

set -eux
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

if [ -z "${ENV}" ]; then
    echo "FATAL ERROR: ENV undefined."
    exit 4
fi

if [ -z "${VERSION}" ]; then
    echo "FATAL ERROR: VERSION undefined."
    exit 5
fi

## Configure ###

set -a
VAULT_TOKEN=$(cat /etc/vault-token-kdux)
OUTPUT_DIR=app
NGINX_PROXIED_HOST=backend
DOCS_PROXIED_HOST=documentation
INPUT_DIR=config
COMPOSE_FILE=/app/docker-compose.yaml
PROJECT=pepper

# configure outside the kdux host
ruby configure.rb -y
scp -r $SSHOPTS app/* $SSH_USER@$SSH_HOST:/app

#### Deploy ####

# Start new application container with the current version, pulling latest container images from the registry
$SSHCMD $SSH_USER@$SSH_HOST "docker-compose -p $PROJECT -f $COMPOSE_FILE pull"
$SSHCMD $SSH_USER@$SSH_HOST "docker-compose -p $PROJECT -f $COMPOSE_FILE stop"
$SSHCMD $SSH_USER@$SSH_HOST "docker-compose -p $PROJECT -f $COMPOSE_FILE rm -f"
$SSHCMD $SSH_USER@$SSH_HOST "docker-compose -p $PROJECT -f $COMPOSE_FILE up -d"

# Remove any dangling images that might be hanging around
$SSHCMD $SSH_USER@$SSH_HOST "docker images -aq --no-trunc --filter dangling=true | xargs docker rmi || /bin/true"

echo "starting smoketest..."
docker run --rm -v app/application.conf:/app/application.conf -v app/post_deploy_smoketest.sh:/app/post_deploy_smoketest.sh broadinstitute/pepper-api-backend:$VERSION_$ENV /app/post_deploy_smoketest.sh $SSH_HOST 30"
