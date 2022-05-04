# cf-file-scanner

This application provides a service to scan bucket files for malicious
infections. The application will listen for `google.object.storage.finalize`
events on a PubSub topic, perform a scan of the object, and publish the scan's
result on another topic.

Request pubsub messages are expected to follow GCS's [notification
format][gcs-fmt]. The message must have a `bucketId` and `objectId` attribute,
but the other message contents are not used. The request message is passed back
in the result message so consumers may leverage this as needed, along with a
`scanResult` attribute with a value of either `CLEAN` or `INFECTED`.

There can be multiple callers requesting file scans and, since this CF only
publishes to one result topic, subscribers should expect to receive an event for
each scan performed. One tip here is to create your subscription with a
[filter][pubsub-filter] to match on your desired bucket.

Internally, the scanner uses the [ClamAV][clamav] open-source antivirus engine.
The ClamAV binaries are provided by the official ClamAV docker image, and
scanning is performed by communication using clamd's [socket protocol][clamd].

[gcs-fmt]: https://cloud.google.com/storage/docs/pubsub-notifications#format
[pubsub-filter]: https://cloud.google.com/pubsub/docs/filtering
[clamav]: https://www.clamav.net/
[clamd]: https://manpages.debian.org/unstable/clamav-daemon/clamd.8.en.html

## Project layout

```
config/                 - Put your config file in here. Ignored during deploy.
src/                    - Project source.
src/resources/container - Resources used during the image build
env.yaml                - Env vars file. This will be rendered during deploy.
pom.xml                 - Project POM file.
```

## Configuration

This application is configured using environment variables.
|name|required|default|description|
|:-|:-|:-|:-|
|GCP_PROJECT|Yes|N/A| The GCP project. Used for both storage and pubsub.|
|RESULT_TOPIC|No|null|The pubsub topic to publish scan results to.|
|DDP_CLAMAV_SERVER|No|localhost:3310|The host and port that clamd is listening on|
|PORT|No|8080|Port for the Java application to listen on. Used by the container.|

## Building
The image is built using Maven and the Docker container toolchain. In order to
push the image to the Google Artifact Registry, you will need to install the
[gcloud][gcloud-cli] cli, and configure Docker to use gcloud as an
authentication helper. Read Google's [documentation][registry-docs] for more details.

At the moment, only the `amd64` arch is supported. If you are building this image
using Apple Silicon, you will need to explicitly specify the `linux/amd64`
platform at build time. The image can still be run on your local machine via
Rosetta but will incur a performance penalty.

Replace `gcloud-region`, `gcp-project-name`, and `repository-name` with the
appropriate values for your environment.

```shell
$ docker build --platform linux/amd64 --tag cf-file-scanner .

$ docker tag cf-file-scanner:latest gcloud-region-docker.pkg.dev/gcp-project-name/repository-name/cf-file-scanner:latest

$ docker push gcloud-region-docker.pkg.dev/gcp-project-name/repository-name/cf-file-scanner:latest
```

[gcloud-cli]:https://cloud.google.com/sdk/docs/install
[registry-docs]:https://cloud.google.com/artifact-registry/docs/docker

## Deployment
A [deploy.sh](deploy.sh) script is included to aid in performing deployments to
a Google Cloud project. The script takes a single argument- the project name.
```shell
$ ./deploy.sh my-google-project-name
```

The script requires the use of the Google Artifact Registry to store the
container images used in the deploy. If an alternate registry is needed (such as
DockerHub or the Azure Container Registry), examine the variables prefixed with
`CONTAINER_` in the `deploy.sh` script for the necessary changes.

If finer control over the build process is required, certain environment variables may be provided to fine tune the names at deployment. A list of these variables,
and their default values, is provided in the table below.

In order to make use of these values, either export them to your environment,
or provide them on the command line.
```shell
$ export SERVICE_NAME="my-alternate-service-name"
$ ./deploy.sh my-gcp-project-name
```
```shell
$ SERVICE-NAME="my-alternate-service-name" ./deploy.sh my-gcp-project-name
```

### Deploy Environment Variables
|name|default|description|
|:-|:-|:-|
|SERVICE_NAME|cf-file-scanner|Name of the Cloud Run service to create|
|CLOUDSDK_RUN_REGION|us-central1|Region to deploy the service into|
|SA_NAME|`$SERVICE_NAME`|The local-part of a GCP Service Account email|
|SECRET_ID|`$SERVICE_NAME`|Name of the secret which contains the service's configuration|
|LISTEN_TOPIC_NAME|`$SERVICE_NAME`|Topic the service will subscribe to. If this topic does not exist, the deploy will abort.|
|LISTEN_DEAD_LETTER_TOPIC_NAME|`$SERVICE_NAME`-dead-letter|Topic to send unacked, expired messages from the listen topic to. This topic will be created if it does not exist.|
|RESULT_TOPIC_NAME|cf-file-scan-result|Topic to publish scan results to. If this topic does not already exist, the deploy will abort.|
|CONTAINER_NAME|`$SERVICE_NAME`|Name of the image to deploy|
|CONTAINER_VERSION|latest|The version of the image to deploy|

## How to run locally

This application makes use of Google's
[Function Framework for Java][function-framework] library and allows for running
them locally via the `function:run` Maven goal. If running locally, the function
can be triggered using a tool capable of sending HTTP requests such as `wget` or
`curl`. The HTTP request payload follows a defined format- See the
[docs][call-cf] for more info. You'll also need a set of Google
[application default credentials][application-credentials] set up in your local
environment.

1. Start the lastest ClamAV docker image.
    ```shell
    $ docker run \                                                     
        --interactive \
        --tty \
        --rm \
        --publish 3310:3310 \                                     
        clamav/clamav:0.104
    ```
2. Open a new terminal in the `cf-file-scanner` subdirectory, and run the
   `function:run` maven goal
    ```shell
    $ GCP_PROJECT="gcp-project-name" \
        DDP_CLAMAV_SERVER="127.0.0.1:3310" \
        mvn -Drun.port=8080 function:run
    ```
3. In a separate terminal, send a request to the running function. An example
    using `curl` is below.
      ```shell
      $ curl -X POST 127.0.0.1:8080 \
          -H 'content-type: application/json' \
          -d '{
            "context": {
              "eventId": "11235813853211",
              "timestamp": "1999-12-31T11:59:99.999Z",
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
                "objectId": "test-files/positive-eicar.txt",
                "other": "attributes"
              },
              "data": null
             }
           }'
      ```

[call-cf]: https://cloud.google.com/functions/docs/running/calling#background_functions
[function-framework]: https://github.com/GoogleCloudPlatform/functions-framework-java
[application-credentials]:https://cloud.google.com/sdk/gcloud/reference/auth/application-default/login

## References

* File scanning solution built for [AWS][aws-av]
* Another file scan solution but for [GCP][aws-av]
* Google's file scanning [tutorial][gcp-tut]

[aws-av]: https://github.com/upsidetravel/bucket-antivirus-function
[gcp-av]: https://github.com/robcharlwood/gcp-av
[gcp-tut]: https://cloud.google.com/solutions/automating-malware-scanning-for-documents-uploaded-to-cloud-storage