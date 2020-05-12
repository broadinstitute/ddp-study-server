#!/bin/bash

gcloud --project broad-ddp-dev compute networks vpc-access connectors create appengine-default-connector \
--network https://www.googleapis.com/compute/v1/projects/broad-ddp-dev/global/networks/default \
--region us-central \
--range 10.8.0.0/28

# don't forget to change dsm's internal IP from ephemeral to static and then update application.conf.ctmpl
# and add roles Compute Network User,  Serverless VPC Access User to the SA


# add firewall rule allowing vcp IP range to access DSM