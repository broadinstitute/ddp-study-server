A command-line tool for building a study on Pepper.

## Getting Started

The tool has a dependency on `pepper-apis` and that needs to be built
beforehand. Running the `install` maven command will build and put the
`pepper-apis` jar in the right place in the local filesystem, so that maven can
find it later when building `study-builder`.

1. Build and install pepper-apis
    ```
    $ cd /path/to/ddp/pepper-apis
    $ mvn -DskipTests clean install
    $ mvn -DskipTests -f parent-pom.xml install
    ```
2. Build this project
    ```
    $ cd ../study-builder
    $ mvn clean package
    ```
3. Render configurations
    ```
    $ ./render.sh <version> <env> <study>
    ```
4. Run the tool
    ```
    $ java -Dconfig.file=/path/to/app.conf -jar ./target/StudyBuilder.jar \
      --vars /path/to/vars.conf /path/to/study-config-file.conf
    ```

## Types of Configuration Files

There's a couple different files we need in order to use the `study-builder`
properly. Here's a breakdown of the types of configuration types:

- **application config** This is the configuration file for the builder tool
  itself. This file contains values the tool needs to perform properly, like
  the database url to connect to. This file can be specified using the
  `-Dconfig.file` flag to the `java` command.

- **variables config** This is the configuration file for study-specific
  "variables". Often, there will be secrets (like auth0 client ids, sendgrid
  api keys, etc) for a study that should not be checked into version-control.
  And there may be values that varies depending on the environment (whether
  dev/test/staging/prod). For the builder tool, these values are collectively
  called "variables", or "vars" for short. We can put values in here, and they
  will be substituted into the study config, as described later. This file is
  optional (so no need to have one if the study doesn't use secrets) and can be
  specified using the `--vars` flag.

- **substitutions config** This is the configuration file for study-wide
  substitution values. Unlike the vars file which is often generated and not
  checked into source version control, the substitutions file can store
  non-secret values that can be checked in. This is useful to centralize
  study-wide values, like a study's email or phone number that's used in
  various activity definitions, or even used for centralizing
  internationalization strings. This file is optional and can be specified
  using the `--substitutions` flag.

- **study config** This is the configuration file for the study. This is what
  the builder looks at to do its work. This is a required argument to the
  builder tool.

## Configuration Language

Configuration files end with the `.conf` extension and is written using the
[HOCON][hocon] language. HOCON is a superset of JSON, but it allows us do some
nifty things like write comments, do value substitutions, and "import" other
config files.

For the vars config file, we will likely pull values from `vault`, in which
case these files end with the `.ctmpl` extension and is written as a "template"
using a language similar to [golang template][go-tmpl]. The file is rendered
into it's final form using a tool called [consul-template][consul-tmpl], but
this is sufficiently abstracted away and you should simply use the `render.sh`
script.

## Configuration Loading and Resolution

Configurations are loaded at runtime using the `typesafe-config`
[library][tscfg-lib]. The vars config and substitutions config will be merged
into a single config, so try to avoid duplicate keys. If there are duplicates,
keys in substitutions config will override those in vars config. The study
config is first loaded from the provided file, and then it is "resolved" using
this merged config. Resolution will take care of value substitutions.

*Note: A vars config is not required if the study config does not have any
substitutions that refers to the vars config. In this case, the study config
will be resolved with an empty config. Likewise for substitutions config.*

Due to limitations of the config library, activity definitions for a study are
loaded from separate files. The file path to the activity definition config
file is understood to be relative to the `study.conf` file. Once loaded, the
activity definition config will be resolved with the vars config.

*Note: Paths in the study config that references other files, like icons and
pdfs, are also understood to be relative to the `study.conf` file.*

[hocon]: https://github.com/lightbend/config/blob/master/HOCON.md
[go-tmpl]: https://golang.org/pkg/text/template/
[consul-tmpl]: https://github.com/hashicorp/consul-template
[tscfg-lib]: https://github.com/lightbend/config
