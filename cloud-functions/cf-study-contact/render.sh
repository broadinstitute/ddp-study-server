#!/usr/bin/env bash
#
# Script to render configuration for cf-study-contact cloud function.

if (( $# < 3 )); then
  echo 'usage: render.sh VERSION ENVIRONMENT STUDY_KEY'
  exit 1
fi
INPUT_DIR=config OUTPUT_DIR=output-config NO_SYSLOG=true \
  VERSION="$1" ENVIRONMENT="$2" STUDY_KEY="$3" \
  ruby ../../pepper-apis/configure.rb -y
