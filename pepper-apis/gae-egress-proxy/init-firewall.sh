#!/usr/bin/env bash
#
# Creates firewall rule for GAE outbound traffic.

NAME=$(basename "$0")
if (( $# < 1 )); then
  echo "usage: $NAME <PROJECT_ID>"
  exit 1
fi

PROJECT_ID="$1"
RULE_NAME="gae-egress-proxy"
echo "using gcp project: $PROJECT_ID"
echo "using firewall rule name: $RULE_NAME"

echo ""
echo "=> reading vpc default network ip range..."
gcloud --project="$PROJECT_ID" compute networks vpc-access connectors list --region=us-central1
ip_range=$(gcloud --project="$PROJECT_ID" compute networks vpc-access connectors list \
  --region=us-central1 --format=json --filter='network:default' \
  | jq -r '.[0].ipCidrRange')

echo ""
echo "=> creating firewall rule..."
gcloud --project="$PROJECT_ID" compute firewall-rules create "$RULE_NAME" \
  --direction=INGRESS \
  --priority=1000 \
  --network=default \
  --action=ALLOW \
  --rules=tcp:3128 \
  --source-ranges="$ip_range" \
  --target-tags=gae-egress-proxy
gcloud --project="$PROJECT_ID" compute firewall-rules describe "$RULE_NAME"

echo ""
echo "=> done!"
