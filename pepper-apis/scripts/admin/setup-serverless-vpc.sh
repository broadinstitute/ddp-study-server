#!/bin/bash

gcloud compute networks vpc-access connectors create appengine-connector \
--network managed \
--region us-central1 \
--range 10.8.0.0/28

# don't forget to change dsm's internal IP from ephemeral to static and then update application.conf.ctmpl