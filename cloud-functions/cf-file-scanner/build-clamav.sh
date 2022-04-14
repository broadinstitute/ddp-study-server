#!/usr/bin/env bash
#
# This is a wrapper script that invokes docker to build clamav bundle and copy
# it to the appropriate place for the function.

set -e

ARTIFACT='build/clamav.tar.gz'

if [[ -f "$ARTIFACT" ]]; then
  echo "clamav artifact already exists: $ARTIFACT"
else
  if [[ ! -d build ]]; then
    mkdir build
  fi
  echo 'Starting clamav build...'
  docker run -it --rm \
    -v "$(pwd)/build-clamav-inside-docker.sh:/build-clamav.sh" \
    -v "$(pwd)/build:/build" \
    ubuntu:18.04 \
    bash /build-clamav.sh
fi

if [[ -d clamav ]]; then
  rm -rf clamav
fi
mkdir clamav
tar xzf $ARTIFACT --directory clamav
