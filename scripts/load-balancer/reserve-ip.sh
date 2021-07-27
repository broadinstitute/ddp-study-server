#!/usr/bin/env bash
#
# Script to help reserve an IP for an upcoming study.

set -e

NAME="$(basename "$0")"
if (( $# < 3 )); then
  echo "usage: $NAME <GCP_PROJECT> <LB_NAME> <STUDY_IDENTIFIER>"
  echo ""
  echo "For STUDY_IDENTIFIER, it should be same one as will be passed to"
  echo "add-study-to-lb.sh script when we add the study for real."
  exit 1
fi

PROJECT_ID="$1"
LB_NAME="$2"
STUDY="$3"

echo "using gcp project: $PROJECT_ID"
echo "using lb name: $LB_NAME"
echo "using study identifier: $STUDY"
echo ""

echo_run() {
  echo "+ $@"
  "$@"
  echo ""
}

ip_name="$LB_NAME-$STUDY-ip"
echo_run gcloud --project="$PROJECT_ID" \
  compute addresses create "$ip_name" --global
ip_addr="$(gcloud --project="$PROJECT_ID" \
  compute addresses describe "$ip_name" \
  --global --format='get(address)')"

echo "=> IP address has been reserved!"
echo "[$ip_name] -> [$ip_addr]"
