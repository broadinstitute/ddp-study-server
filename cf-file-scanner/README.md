## cf-file-scanner

This Cloud Function provides a service to scan bucket files for malicious
infections. Main mode of communication with this service is via pubsub. File
scan requests are submitted to a pubsub topic, and scan results are published
to another pubsub topic.

Request pubsub messages are expected to follow GCS's [notification
format][gcs-fmt], however, only `bucketId` and `objectId` attributes are
necessary. The request message is passed back in the result message so
consumers may leverage this as needed, along with a `scanResult` attribute with
a value of either `CLEAN` or `INFECTED`.

There can be various callers requesting file scans. And since this CF only
publishes to one result topic, subscribers might receive result messages for
all of those scans. One tip here is to create your subscription with a
[filter][pubsub-filter] to match on your desired bucket.

Internally, the scanner uses the [ClamAV][clamav] open-source antivirus engine.
The virus signature database files are loaded at runtime on every scan using
`freshclam`. Files are scanned using the `clamscan` command-line, invoked via
the CF. ClamAV binaries are packaged with the CF, see `build-clamav.sh` script
as a starting point to see how that bundle is built.

[gcs-fmt]: https://cloud.google.com/storage/docs/pubsub-notifications#format
[pubsub-filter]: https://cloud.google.com/pubsub/docs/filtering
[clamav]: https://www.clamav.net/

## Project layout

```
build/          - This is where the clamav tarball go. Ignored during deploy.
clamav/         - This is where clamav binaries go.
config/         - Put your config file in here. Ignored during deploy.
src/            - Project source.
env.yaml        - Env vars file. This will be rendered during deploy.
freshclam.conf  - Config file that freshclam needs in order to run.
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

* Create `cf-file-scanner` secret in Secret Manager.
* Create new `cf-file-scanner` service account.
* Set up the upload bucket and inbound/outbound pubsub topics.
* Assign `Storage Object Viewer` for the SA on the upload bucket.
* Assign `Pub/Sub Publisher` for the SA on the result topic.
* Use the `init-bucket-event.sh` to setup GCS notification.
* Deploy using `deploy.sh`.

## How to run locally

We can use the Cloud Function Maven plugin to run the CF locally. However, it
will run the CF as a HTTP function even if it was implemented as a background
function. This is so we can trigger the function locally easily using something
like `curl`. The HTTP request payload follows a certain format. See the
[docs][call-cf] for more info.

You'll also need Google application default credentials on your local
environment. You can get this by using the `gcloud auth` commands.

1. Install ClamAV on your local machine.
2. Create subdirectory named `clamav`.
3. Copy `clamscan` and `freshclam` binaries FROM YOUR LOCAL MACHINE to the `clamav` directory.
4. Source your `config/local.env`.
5. Run `mvn function:run`.
6. In a separate terminal, post a message. For example:

```
$ curl -X POST localhost:8080 \
  -H 'content-type: application/json' \
  -d '{
    "context": {
      "eventId": "1144231683168617",
      "timestamp": "2020-05-06T07:33:34.556Z",
      "eventType": "google.pubsub.topic.publish",
      "resource": {
        "service": "pubsub.googleapis.com",
        "name": "projects/broad-ddp-dev/topics/cf-file-scanner",
        "type": "type.googleapis.com/google.pubsub.v1.PubsubMessage"
      }
    },
    "data": {
      "@type": "type.googleapis.com/google.pubsub.v1.PubsubMessage",
      "attributes": {
        "bucketId": "ddp-dev-file-uploads",
        "objectId": "file.pdf",
        "other": "attributes"
      },
      "data": ""
    }
  }'
```

[call-cf]: https://cloud.google.com/functions/docs/running/calling#background_functions

## References

* File scanning solution built for [AWS][aws-av]
* Another file scan solution but for [GCP][aws-av]
* Google's file scanning [tutorial][gcp-tut]

[aws-av]: https://github.com/upsidetravel/bucket-antivirus-function
[gcp-av]: https://github.com/robcharlwood/gcp-av
[gcp-tut]: https://cloud.google.com/solutions/automating-malware-scanning-for-documents-uploaded-to-cloud-storage
