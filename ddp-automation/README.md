## A word on runtime parameters
Although JDI exposes many things via annotations, in order to run across environments, we should avoid hardcoding
fields that change at runtime depending on environment--for example, `basil-dev`, `basil-test`, etc.  We'll specify
these values using typesafe config, similar to what we do in pepper-apis.  `DDPWebSite.CONFIG` holds the
config file that is loaded via `-Dconfig.file`.  All secrets should be loaded via this config file, which is
itself rendered via vault.  **Do not commit secrets to source control.**


## Rendering secrets
Before running tests locally, you must render the secrets from your terminal within this directory like so:
```
cd ../
./webdriver_tests.sh v1 test ddp-automation/output-config --config
```

When running tests, you'll then need to point at the absolute path for this rendered `config.conf` file
via `-Dconfig.file=output-config/config.conf`, assuming working directory of this directory.

## Building the backend
Because our webdriver tests have access to `pepper-apis` code, we must first build `pepper-apis`.  `webdriver_tests.sh` takes
care of this via docker, but when running locally, here's a simple way to *build* the backend code.  It's a good practice to rebuild
`pepper-apis` whenever you update your sandbox.
```
cd ../pepper-apis
mvn -DskipTests clean install
mvn -DskipTests -f parent-pom.xml install
```

More information on build/testing `pepper-apis` is [here](../pepper-apis)

## Testing with JDI/WebDriver

All tests must extend `BaseTest`.  `BaseTest` figures out whether to run in "browserstack" or
"local" mode depending on the `org.datadonationplatform.doBrowserStack` system property.  If set to
false, the test runs locally.  If set to true, it runs via browserstack.

When running via browserstack,  you must also reference a configuration file, which calls out things like
os, browser, auth keys, etc.  These files are in src/test/resources/conf.


## Running tests via Intellij

## SqlProxy
Some webdriver tests that run via `webdriver_tests.sh` connect to the cloud database in order to assert that what was entered on the frontend made it
into the database.  In order to connect to the database when running locally, you must run the cloudsqlproxy
on your machine.  When running under docker, cloudsql proxy is run automatically as a separate linked service.

To start the proxy to run the tests locally (via terminal - these steps are also needed for IntelliJ runs):
 1. Go to the directory where cloudsql proxy is installed
 2. There is more info concerning cloudsql proxy in the pepper-apis readme but to be brief, enter this to start the 
 proxy:
 
 
```
./cloud_sql_proxy -instances=[value of CLOUDSQL_CONNECTION_LIST var]
-credential_file=[path-to-your-pepper]/output-config/sqlproxy-service-account.json
```
Protip: the CLOUDSQL_CONNECTION_LIST vars are located in /ddp-automation/output-config/sqlproxy.env

To check if you are running sqlproxy use: 
```
ps | grep cloud_sql_proxy
```
if you are running it, then you will see './cloud_sql_proxy' as one of the processes

### Running tests locally with chrome

1. Create a test configuration like you normally do
2. Add a few _system properties_:
```
-Dorg.datadonationplatform.doBrowserStack=false
-Dconfig.file=output-config/config.conf

### You can also run your test from intellij and have it execute on browserstack
To do this, you must also include
the os/browser configuration you want to use by calling out a config file from src/test/resources/conf like so:

1. Create a test configuration like you normally do
2. Add a few system properties:
```
-Dorg.datadonationplatform.doBrowserStack=true
-Dconfig.file=output-config/config.conf
-Dconfig=windows-chrome.conf

## Running tests via maven

### Running tests locally with maven

1. Make sure that sqlproxy is running (See SqlProxy section)

2. Go to `/ddp-automation/config.conf` and change 'sqlproxy' in 'dbUrl' to '127.0.0.1' 
**(do not commit this - change it back to sqlproxy before commit)**. Also make sure that the editted
'dbUrl' line is in one line.

3. Open another terminal window and navigate to `/ddp-automation`

Run maven:

`mvn -Dconfig.file=output-config/config.conf -P local angio-test`


You can use Control + C to stop the tests entirely in maven or to skip a browser's e.g chrome's
tests when used with `./webdriver_tests.sh ...` commands such as `./webdriver_tests.sh v1 test . --test` 
(Basil-App flow test) and `./webdriver_tests.sh v1 test . --angio-test` (Pepper Angio flow test)

### Running your tests on BrowserStack with maven
We use different profiles in pom.xml to simplify setup with different os/browser combinations.  As the project evolves,
new profiles will be added.

Here are a few examples:

### Chrome on OSX
`mvn -Dconfig.file=output-config/config.conf -P RemoteOSXChrome test`

### Firefox on Windows
`mvn -Dconfig.file=output-config/config.conf -P RemoteWindowsFirefox test`

you can build the docker image and run the tests via docker like so.  Beforehand, just make sure any changes
are committed (but not necessarily pushed) so that you won't pickup the wrong image via a stale git sha tag.
```
cd ..
./webdriver_tests.sh v1 test ddp-automation/output-config --angio-test
```

## Running tests via docker
You may need to adjust the following lines in `webdriver_tests.sh` so that your local
run of the tests doesn't post misleading updates into the `pepper-ci` room:

```
NOTIFY_SLACK=true
BROWSER_STACK_BUILD_NAME="Jenkins ${ENV}"
```

To run all tests via docker:

```
cd ../
./webdriver_tests.sh v1 test ddp-automation/output-config --angio-test
```


## Controlling Browserstack's build name and session name
To set the build name and session name, use:
```
-Dorg.datadonationplatform.browserStackBuildName=...
-Dorg.datadonationplatform.browserStackSessionName=...
```

When running via docker, use these environment variables instead of java properties:
```
-e BUILD_NAME=... -e SESSION_NAME=...
```

## Viewing Browserstack test results
Login to the [automate dashboard](https://www.browserstack.com/automate) using the login creds at `vault read secret/pepper/test/browserstack`
