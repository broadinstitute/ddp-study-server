#!/usr/bin/env bash
#
# Script to render secrets for elasticsearch.

if (( $# < 2 )); then
  echo 'usage: render.sh VERSION ENV'
  exit 1
fi

INPUT_DIR=config OUTPUT_DIR=output-config NO_SYSLOG=true \
  VERSION="$1" ENV="$2" \
  ruby ../pepper-apis/configure.rb -y
