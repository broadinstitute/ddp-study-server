## cf-circadia-sleeplog-connector

This Cloud Function provides the public API for Circadia study's minisleeplog 
feature. Circadia study's web client is expected to call this API in order
to reach out to 3rd party software without exposing client auth data.

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

* Create new `cf-circadia-sleeplog-connector` secret in Secret Manager.
* Create new `cf-circadia-sleeplog-connector` service account.
* Deploy using `deploy.sh`.

## How to run locally

We can use maven plugin to run CF locally, which will start a server running at
`localhost:8080` that internally runs the function. You can then call it by
using `curl`. See [docs][call-cf] for more details.

1. Source your `config/local.env` configuration.
2. Run `mvn function:run`.
5. In a separate terminal, send a request. For example:

```
$ curl -X POST localhost:8080 \
  -H 'content-type: application/json' \
  -d "{
    \"url\": \"$URL\",
    \"method\": \"$METHOD\",
    \"domain\": \"$AUTH0_DOMAIN\",
    \"clientId\": \"$AUTH0_CLIENT_ID\",
      ...
  }"
```

[call-cf]: https://cloud.google.com/functions/docs/calling/http
