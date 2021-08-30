## cf-atcp-email

This Cloud Function is responsible for listening for file scan results from the
cf-file-scanner service. Upon getting a clean result, it will proceed to gather
up the metadata and file attachment and send the DAR request to A-T study staff
email. After email is sent, both metadata and PDF file will be deleted from
storage.

## Configuration

This CF is configured via environment variables. See `config/example.env` file.

## Setup and deployment

* Create new `cf-atcp-emailer` secret in Secret Manager.
* Create new `cf-atcp-emailer` service account.
* Setup file scan bucket notifications.
  * Use the `cf-file-scanner/init-bucket-event.sh` script.
  * ATCP upload bucket -> cf-file-scanner pubsub topic.
* Grant `cf-file-scanner` SA access to the bucket.
  * Role `Storage Object Viewer`
* Grant SA access to ATCP upload bucket.
  * Role `DDP Storage Objects Get`
  * Role `DDP Storage Objects Delete`
* Deploy using `deploy.sh`.

## How to run locally

* Use `gcloud auth` commands to setup application default credentials locally.
* Source your `config/local.env` for local development.
* Start up function locally with `mvn function:run`.
* Send a message using `curl`. See [docs][call-cf] for more details.

Note: You may want to use a free service such as [mailinator][mail] as the "to"
email inbox for local testing. However, Mailinator's inboxes are public and the
free-tier does not support attachments.

[call-cf]: https://cloud.google.com/functions/docs/running/calling#background_functions
[mail]: https://www.mailinator.com/
