#!/usr/bin/env bash
set -e

GIT_SHA="${GIT_SHA:-$(git rev-parse --verify HEAD)}"
GIT_SHA_SHORT=${GIT_SHA:0:12}
IMAGE_NAME="broadinstitute/pepper-squid:$GIT_SHA_SHORT"
IMAGE_LATEST="broadinstitute/pepper-squid:latest"

echo "=> building image..."
docker build --tag="$IMAGE_NAME" \
  --build-arg "GIT_SHA=$GIT_SHA" \
  --file Dockerfile .

echo "=> tagging image as latest..."
docker tag "$IMAGE_NAME" "$IMAGE_LATEST"

echo "=> pushing images..."
docker push "$IMAGE_NAME"
docker push "$IMAGE_LATEST"

echo "=> done!"
