## cf-atcp-contact

This Cloud Function provides the public API for A-T study's DAR feature (Data
Access Request). A-T study's web client is expected to call this API to submit
the visitor's form responses.

The main responsibility of this CF is to check the submission and authorize an
upload URL for the file attachment. The form responses will be persisted as
metadata in Google Buckets alongside the authorized file upload. Web client is
expected to follow through with completion of file upload.

Once file upload is complete, it should automatically trigger a malware scan
via our `cf-file-scanner` service. There will be another CF that listens for
the file scan result from pubsub and act accordingly. See the `cf-atcp-emailer`
function for more details on this part of the process.

## Project layout

```
config/         - Put your config file in here. Ignored during deploy.
src/            - Project source.
env.yaml        - Env vars file. This will be rendered during deploy.
pom.xml         - Project POM file.
```

## Configuration

This CF is configured via environment variables.

* See `config/example.env` for specification of the env vars.
* For local development, you may want to create your own `config/local.env`.
  * And then you can use it like so: `$ source config/local.env`.
* For deployment, env vars file is rendered as `env.yaml` from Secret Manager.
  * Deploys use YAML format for simplicity.

## Setup and deployment

* Create new `cf-atcp-contact` secret in Secret Manager.
* Create new `cf-atcp-contact` service account.
* Create new `atcp-$env-dar-form` bucket (replace `$env` accordingly).
* Grant SA the `iam.serviceAccounts.signBlob` permission.
  * Can be done by assigning the `DDP Storage URL Signer` role in IAM.
* Grant SA the `storage.objects.create` permission to bucket.
  * Assign this at the resource-level on the bucket itself.
  * Can use a role such as `DDP Storage Objects Create`.
* Deploy using `deploy.sh`.

## How to run locally

We can use maven plugin to run CF locally, which will start a server running at
`localhost:8080` that internally runs the function. You can then call it by
using `curl`. See [docs][call-cf] for more details.

You'll also need Google application default credentials on your local
environment. You can get this by using `gcloud auth` commands.

1. Source your `config/local.env` configuration.
2. Run `mvn function:run`.
5. In a separate terminal, send a request. For example:

```
$ curl -X POST localhost:8080 \
  -H 'content-type: application/json' \
  -d "{
    \"domain\": \"$AUTH0_DOMAIN\",
    \"clientId\": \"$AUTH0_CLIENT_ID\",
    \"g-recaptcha-response\": \"...\",
    \"data\": {
      ...
    },
    \"attachment\": {
      \"name\": \"...\",
      \"size\": ...
    }
  }"
```

[call-cf]: https://cloud.google.com/functions/docs/calling/http
