#!/bin/bash
# see https://broadinstitute.atlassian.net/browse/DDP-4236 for more details

# create one policy with multiple rules, each with <=5 IPs as per cloud armor quota limits
gcloud compute security-policies create register-user-from-auth0 --description "only allow auth0 whitelist to hit register route"

# create high priority rules that allow /register from whitelisted IPs
gcloud beta compute security-policies rules create 1001 --description "only allow /register from auth0 IPs batch 1" --security-policy register-user-from-auth0 --action allow --expression "request.path.matches('/pepper/v1/register') && (inIpRange(origin.ip, '35.167.74.121/32') || inIpRange(origin.ip, '35.166.202.113/32') || inIpRange(origin.ip, '35.160.3.103/32') || inIpRange(origin.ip, '54.183.64.135/32'))"

gcloud beta compute security-policies rules create 1002 --description "only allow /register from auth0 IPs batch 2" --security-policy register-user-from-auth0 --action allow --expression "request.path.matches('/pepper/v1/register') && (inIpRange(origin.ip, '54.67.77.38/32') || inIpRange(origin.ip, '54.67.15.170/32') || inIpRange(origin.ip, '54.183.204.205/32') || inIpRange(origin.ip, '35.171.156.124/32'))"

gcloud beta compute security-policies rules create 1003 --description "only allow /register from auth0 IPs batch 3" --security-policy register-user-from-auth0 --action allow --expression "request.path.matches('/pepper/v1/register') && (inIpRange(origin.ip, '18.233.90.226/32') || inIpRange(origin.ip, '3.211.189.167/32'))"

# create lower priority deny-all rules for /register
gcloud beta compute security-policies rules create 2000 --description "deny /register" --security-policy register-user-from-auth0 --expression "request.path.matches('/pepper/v1/register')" --action deny-404

#  list all the load balancer backends, and set the policy for each to the policy we just made
gcloud compute backend-services list --uri | xargs -I {} gcloud compute backend-services update '{}' --security-policy register-user-from-auth0


