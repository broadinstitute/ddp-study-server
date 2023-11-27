#!/usr/bin/env bash
#
# Setup load-balancer (LB) for routing traffic to our backend and to our study
# apps. This handles putting together the DSS and DSM backends. For the various
# studies, use the `add-study-to-lb.sh` script to add them.

set -e

NAME="$(basename "$0")"
DIR="$(cd "$(dirname "$0")"; pwd)"
if (( $# < 2 )); then
  echo "usage: $NAME <GCP_PROJECT> <LB_NAME>"
  exit 1
fi

PROJECT_ID="$1"
ENV_NAME="${PROJECT_ID/broad-ddp-/}"
REGION='us-central1'
LB_NAME="$2"
if [[ "$ENV_NAME" == "prod" ]]; then
  DSS_DOMAIN='pepper.datadonationplatform.org'
  DSM_DOMAIN='dsm.datadonationplatform.org'
else
  DSS_DOMAIN="pepper-$ENV_NAME.datadonationplatform.org"
  DSM_DOMAIN="dsm-$ENV_NAME.datadonationplatform.org"
fi

echo "using gcp project: $PROJECT_ID"
echo "using region: $REGION"
echo "using lb name: $LB_NAME"
echo "using dss domain: $DSS_DOMAIN"
echo "using dsm domain: $DSM_DOMAIN"
echo ""

echo_run() {
  echo "+ $@"
  "$@"
  echo ""
}

echo "=> setting up backend services..."

# Notes about naming convention:
# - using `-gae` suffix so we know it's backed by GAE
services=(
  'pepper-backend:dss-backend-gae:dss-backend-service'
  'pepper-api-spec:dss-docs-gae:dss-docs-service'
  'study-manager-backend:dsm-backend-gae:dsm-backend-service'
  'study-manager-ui:dsm-ui-gae:dsm-ui-service'
)

for item in "${services[@]}"; do
  IFS=':' read -r gae_service neg_name name <<< "$item"

  echo_run gcloud --project="$PROJECT_ID" \
    compute network-endpoint-groups create "$neg_name" \
    --network-endpoint-type=serverless \
    --app-engine-service="$gae_service" \
    --region="$REGION"

  echo_run gcloud --project="$PROJECT_ID" \
    compute backend-services create $name --global

  echo_run gcloud --project="$PROJECT_ID" \
    compute backend-services add-backend $name --global \
    --network-endpoint-group="$neg_name" \
    --network-endpoint-group-region="$REGION"
done

echo "=> setting up url-map..."

# Note: the url-map is the core of the LB. It connects the frontends to the
# backend services. If frontend request doesn't match a predefined backend,
# then let's default to DSS service.
echo_run gcloud --project="$PROJECT_ID" \
  compute url-maps create "$LB_NAME" \
  --default-service=dss-backend-service

echo "=> adding mappings for dss..."

# Note: these rules are paths off of the given domain. Anything else that
# doesn't match these (e.g. /*) will fallback to the default service.
dss_rules='/docs=dss-docs-service'
dss_rules+=',/spec/*=dss-docs-service'

echo_run gcloud --project="$PROJECT_ID" \
  compute url-maps add-path-matcher "$LB_NAME" \
  --path-matcher-name=dss-matcher \
  --default-service=dss-backend-service \
  --backend-service-path-rules="$dss_rules" \
  --new-hosts="$DSS_DOMAIN"

echo "=> adding mappings for dsm..."

dsm_rules='/api/*=dsm-backend-service'
dsm_rules+=',/app/*=dsm-backend-service'
dsm_rules+=',/ddp/*=dsm-backend-service'
dsm_rules+=',/info/*=dsm-backend-service'
dsm_rules+=',/ui/*=dsm-backend-service'

echo_run gcloud --project="$PROJECT_ID" \
  compute url-maps add-path-matcher "$LB_NAME" \
  --path-matcher-name=dsm-matcher \
  --default-service=dsm-ui-service \
  --backend-service-path-rules="$dsm_rules" \
  --new-hosts="$DSM_DOMAIN"

echo "=> setting up second LB for http-to-https redirects..."

# Note: we use another LB to handle http-to-https redirects. The second LB
# mostly just contains frontends with port 80 and same IPs, and no backends.
# There doesn't seem to be a way to create this using cli interface, so we have
# to import a YAML file.
#
# We're only going to use this http LB for redirecting the study site and not
# our DSS and DSM backends. HTTP requests might contain credentials, so we want
# to reject those requests.
http_lb="$LB_NAME-http"

cat "$DIR/http-lb.tmpl.yaml" \
  | sed "s/{{name}}/$http_lb/g" \
  > "/tmp/$http_lb.yaml"
echo "created: /tmp/$http_lb.yaml"

echo_run gcloud --project="$PROJECT_ID" \
  compute url-maps validate --source="/tmp/$http_lb.yaml"

echo_run gcloud --project="$PROJECT_ID" \
  compute url-maps import "$http_lb" \
  --global --source="/tmp/$http_lb.yaml"

echo "=> setting up frontends..."

dss_ip_name="$LB_NAME-dss-ip"
dsm_ip_name="$LB_NAME-dsm-ip"

echo_run gcloud --project="$PROJECT_ID" \
  compute addresses create "$dss_ip_name" --global
dss_ip_addr="$(gcloud --project="$PROJECT_ID" \
  compute addresses describe "$dss_ip_name" \
  --global --format='get(address)')"

echo_run gcloud --project="$PROJECT_ID" \
  compute addresses create "$dsm_ip_name" --global
dsm_ip_addr="$(gcloud --project="$PROJECT_ID" \
  compute addresses describe "$dsm_ip_name" \
  --global --format='get(address)')"

dss_cert_name="dss-cert"
dsm_cert_name="dsm-cert"

echo_run gcloud --project="$PROJECT_ID" \
  compute ssl-certificates create "$dss_cert_name" \
  --global --domains="$DSS_DOMAIN"
echo_run gcloud --project="$PROJECT_ID" \
  compute ssl-certificates create "$dsm_cert_name" \
  --global --domains="$DSM_DOMAIN"

# Notes about naming convention:
# - using LB name as prefix so we know it's config for an LB
frontends=(
  "$dss_ip_name:$dss_cert_name:$LB_NAME-dss-proxy:$LB_NAME-dss-frontend"
  "$dsm_ip_name:$dsm_cert_name:$LB_NAME-dsm-proxy:$LB_NAME-dsm-frontend"
)

for item in "${frontends[@]}"; do
  IFS=':' read -r ip_name cert_name proxy_name name <<< "$item"

  echo_run gcloud --project="$PROJECT_ID" \
    compute target-https-proxies create "$proxy_name" \
    --ssl-certificates="$cert_name" \
    --url-map="$LB_NAME"

  echo_run gcloud --project="$PROJECT_ID" \
    compute forwarding-rules create "$name" \
    --address="$ip_name" \
    --target-https-proxy="$proxy_name" \
    --global --ports=443

  # No http frontends will be created for DSS or DSM.
done

echo "=> last step: please manually update DNS 'A' records and remove GAE custom domains when ready"
echo "[$DSS_DOMAIN] -> [$dss_ip_addr]"
echo "[$DSM_DOMAIN] -> [$dsm_ip_addr]"
