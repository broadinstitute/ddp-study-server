#!/usr/bin/env bash
set -e
docker build -t broadinstitute/study-server-build:pubsub-1 .
docker push broadinstitute/study-server-build:pubsub-1
