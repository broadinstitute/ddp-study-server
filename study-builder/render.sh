#!/usr/bin/env bash
#
# Script to render both app/study configurations for study-builder.

if (( $# < 3 )); then
  echo 'usage: render.sh VERSION ENVIRONMENT <STUDY_KEY | tenant>'
  exit 1
fi

if [[ "$3" == 'tenant' ]]; then
  INPUT_DIR=config OUTPUT_DIR=output-config NO_SYSLOG=true \
    VERSION="$1" ENVIRONMENT="$2" MANIFEST=manifest-tenant.rb \
    ruby ../pepper-apis/configure.rb -y
elif [[ "$3" == 'testboston' ]]; then
  INPUT_DIR=config OUTPUT_DIR=output-config NO_SYSLOG=true \
    VERSION="$1" ENVIRONMENT="$2" STUDY_KEY="$3" MANIFEST=manifest-testboston.rb \
    ruby ../pepper-apis/configure.rb -y
else
  INPUT_DIR=config OUTPUT_DIR=output-config NO_SYSLOG=true \
    VERSION="$1" ENVIRONMENT="$2" STUDY_KEY="$3" \
    ruby ../pepper-apis/configure.rb -y
fi
