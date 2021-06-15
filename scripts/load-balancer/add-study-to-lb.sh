#!/usr/bin/env bash
#
# Setup the frontend, backend, and url mappings for a new study to the LB.

set -e

NAME="$(basename "$0")"
if (( $# < 5 )); then
  echo "usage: $NAME <GCP_PROJECT> <LB_NAME> <GAE_SERVICE> <STUDY_DOMAIN> <STUDY_IDENTIFIER>"
  echo ""
  echo "For STUDY_IDENTIFIER, choose a short concise name for the study."
  echo "This identifier will be used to name various LB resources for the study."
  exit 1
fi

PROJECT_ID="$1"
REGION='us-central1'
LB_NAME="$2"
GAE_SERVICE="$3"
STUDY_DOMAIN="$4"
STUDY="$5"

echo "using gcp project: $PROJECT_ID"
echo "using region: $REGION"
echo "using lb name: $LB_NAME"
echo "using gae service: $GAE_SERVICE"
echo "using study domain: $STUDY_DOMAIN"
echo "using study identifier: $STUDY"
echo ""

echo_run() {
  echo "+ $@"
  "$@"
  echo ""
}

echo "=> setting up backend..."

neg_name="$STUDY-gae"
service_name="$STUDY-service"

echo_run gcloud --project="$PROJECT_ID" \
  compute network-endpoint-groups create "$neg_name" \
  --network-endpoint-type=serverless \
  --app-engine-service="$GAE_SERVICE" \
  --region="$REGION"

echo_run gcloud --project="$PROJECT_ID" \
  compute backend-services create "$service_name" --global

echo_run gcloud --project="$PROJECT_ID" \
  compute backend-services add-backend "$service_name" --global \
  --network-endpoint-group="$neg_name" \
  --network-endpoint-group-region="$REGION"

echo "=> setting up frontend..."

ip_name="$LB_NAME-$STUDY-ip"
cert_name="$STUDY-cert"
proxy_name="$LB_NAME-$STUDY-proxy"
frontend_name="$LB_NAME-$STUDY-frontend"

# Check if IP address is already reserved or not. Since this can fail if it
# doesn't exit, we temporary turn off `-e` and redirect fd 2 so we minimize
# noise on script output.
set +e
ip_addr="$(gcloud --project="$PROJECT_ID" \
  compute addresses describe "$ip_name" \
  --global --format='get(address)' 2>/dev/null)"
set -e

if [[ -z "$ip_addr" ]]; then
  echo_run gcloud --project="$PROJECT_ID" \
    compute addresses create "$ip_name" --global
  ip_addr="$(gcloud --project="$PROJECT_ID" \
    compute addresses describe "$ip_name" \
    --global --format='get(address)')"
else
  echo "=> IP address is already reserved for name [$ip_name]"
  echo ""
fi

echo_run gcloud --project="$PROJECT_ID" \
  compute ssl-certificates create "$cert_name" \
  --global --domains="$STUDY_DOMAIN"

echo_run gcloud --project="$PROJECT_ID" \
  compute target-https-proxies create "$proxy_name" \
  --ssl-certificates="$cert_name" \
  --url-map="$LB_NAME"

echo_run gcloud --project="$PROJECT_ID" \
  compute forwarding-rules create "$frontend_name" \
  --address="$ip_name" \
  --target-https-proxy="$proxy_name" \
  --global --ports=443

echo "=> adding mappings for study..."

matcher_name="$STUDY-matcher"
echo_run gcloud --project="$PROJECT_ID" \
  compute url-maps add-path-matcher "$LB_NAME" \
  --path-matcher-name="$matcher_name" \
  --default-service="$service_name" \
  --new-hosts="$STUDY_DOMAIN"

echo "=> adding http-to-https redirect..."

echo_run gcloud --project="$PROJECT_ID" \
  compute target-http-proxies create "$proxy_name-http" \
  --global --url-map="$LB_NAME-http"

echo_run gcloud --project="$PROJECT_ID" \
  compute forwarding-rules create "$frontend_name-http" \
  --address="$ip_name" \
  --target-http-proxy="$proxy_name-http" \
  --global --ports=80

echo "=> last step: please manually update DNS 'A' record and remove GAE custom domain when ready"
echo "[$STUDY_DOMAIN] -> [$ip_addr]"
