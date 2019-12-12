#!/usr/bin/env bash
#
# Script to render both app/study configurations for study-builder.

if (( $# < 3 )); then
  echo 'usage: render.sh VERSION ENV STUDY'
  exit 1
fi

INPUT_DIR=config OUTPUT_DIR=output-config NO_SYSLOG=true \
  VERSION="$1" ENV="$2" STUDY_KEY="$3" \
  ruby ../pepper-apis/configure.rb -y
