#!/usr/bin/env bash

set -e

###
#
# Builds a static HTML file from the
function build_docs_image {
    local docs_path="docs/specification"
    local build_image="broadinstitute/pepper-docs:$GIT_SHA_SHORT"
    local latest_image="broadinstitute/pepper-docs:$tag"

    if ! command -v npm 2>&1 1>/dev/null; then
        # This is a workaround for the Jenkins environments
        # They do not have npm installed, so can't build the documentation
        #   natively.
        # In order to scoot past this, drop everything into a
        #   basic node image, and run the script.
        docker run \
            --rm \
            --env HOME=/tmp \
            --user $(id -u ${USER}):$(id -g ${USER}) \
            --volume "${PWD}/${docs_path}":"/build" \
            --workdir "/build" \
            node:10-slim \
            ./build.sh documentation
    else
        (
            cd "docs/specification"
            ./build.sh docker "$build_image"
        )
    fi

    if [ "$?" -ne "0" ]; then
        return 1
    fi

    docker build \
        --tag "$build_image" \
        --file "${docs_path}/Dockerfile" \
        "${PWD}/${docs_path}"

    docker tag "$build_image" "$latest_image"
}

function push_docs_image {
    local build_image="broadinstitute/pepper-docs:$GIT_SHA_SHORT"
    local latest_image="broadinstitute/pepper-docs:$tag"

    docker push "$build_image"
    docker push "$latest_image"
}

function render_config() {
  # render build configs
  GAE=false
  if [[ "$1" =  "gae" ]]; then
    GAE=true
  fi
  echo "rendering buildtime configs"
  INPUT_DIR=config DOCS_PROXIED_HOST=$DOCS_PROXIED_HOST NGINX_PROXIED_HOST=$NGINX_PROXIED_HOST OUTPUT_DIR=$BUILD_OUTDIR ENVIRONMENT=$ENVIRONMENT VERSION=$VERSION DIR=$DIR GAE=$GAE MANIFEST=build-manifest.rb ruby configure.rb -y

  if [[ "$1" == "local" ]] || [[ "$1" == "gae" ]]; then
      echo "rendering runtime configs locally"
      INPUT_DIR=config NO_SYSLOG=true DOCS_PROXIED_HOST=$DOCS_PROXIED_HOST NGINX_PROXIED_HOST=$NGINX_PROXIED_HOST OUTPUT_DIR=$OUTDIR ENVIRONMENT=$ENVIRONMENT DIR=$DIR/$OUTDIR VERSION=$VERSION GAE=$GAE ruby configure.rb -y
      cp $OUTDIR/docker-compose.yaml $DIR
  fi
}

function docker_build() {
    echo "building pepper-api-backend:${GIT_SHA_SHORT} docker image"
    # to run from source locally, replace the build below and tagging with: docker build -f Dockerfile-backend -t pepper-api-backend-local --no-cache .
    docker build \
        --build-arg GIT_SHA=$GIT_SHA \
        -t broadinstitute/pepper-api-backend:${GIT_SHA_SHORT} \
        -f Dockerfile-backend .
}

###
#
# Retags the pepper backend image from the commit to
# the latest version for the target environment.
#
# This should not be called if the build is being run
# on a developer machine.
function docker_retag() {
    echo "retagging pepper-api-backend:${GIT_SHA_SHORT} as $tag"
    docker tag broadinstitute/pepper-api-backend:${GIT_SHA_SHORT} broadinstitute/pepper-api-backend:$tag
}

###
# usage: run_tests [build-style]
#
# Runs the maven test suite on a pepper-api backend image.
# After the run, the results of the tests, in the form of
# checkstyle and surefire reports (saved to `pepper-apis/surefire-reports`
# and `pepper-apis/checkstyle-result.xml`, respectively).
#
# The `build-style` parameter is optional. The only supported non-empty value
# is 'local'. Using the 'local' value directs the function to
# use the public address for the DSP sonarqube instance instead
# of the private one.
#
# Returns 0 on test suite success, and non-zero otherwise
#
function run_tests() {
    local build_style="$1"
    local docker_image_tag="${GIT_SHA_SHORT}"
    local sonar_domain

    if [[ "${build_style}" == "local" ]]; then
        # If we're building with the 'local' flag, we should use the
        #   publicly accessible IP for the SonarQube server.
        sonar_domain="sonarqube.dsp-techops.broadinstitute.org"
    else
        sonar_domain="sonarqube-priv.dsp-techops.broadinstitute.org"
    fi

    echo "Testing on image $docker_image_tag"
    echo "running tests on pepper-api-backend:$docker_image_tag with configs from $PWD/$BUILD_OUTDIR"

    # run tests on image, saving results to workspace
    touch "surefire-report.html"
    touch "checkstyle-result.xml"
    mkdir -p "surefire-reports"

    local sonar_server="https://${sonar_domain}"
    local sonar_ip=$(dig +short ${sonar_domain} | tail -1)
    echo "Using sonar server $sonar_server"

    # ensure a file exists for the checkstyle results, otherwise
    # Docker will assume it's intended to be a directory
    touch "$PWD/checkstyle-result.xml"

    # to run from source locally, replace broadinstitute/pepper-api-backend:$tag  image with pepper-api-backend-local
    # -v option a bit of magic to enable docker run inside another docker.
    docker run --rm \
         -v /var/run/docker.sock:/var/run/docker.sock \
        --add-host sonarqube.dsp-techops.broadinstitute.org:${sonar_ip} \
        --volume "$PWD/surefire-reports":"/app/target/surefire-reports" \
        --volume "$PWD/checkstyle-result.xml":"/app/target/checkstyle-result.xml" \
        --volume $PWD/surefire-report.html:/app/target/site/surefire-report.html \
        --volume $PWD/$BUILD_OUTDIR/local.conf:/app/config/local.conf \
        --volume $PWD/$BUILD_OUTDIR/fc_keys:/app/fc_keys \
        --volume $PWD/$BUILD_OUTDIR/itextkey.xml:/app/itextkey.xml \
        --interactive \
        broadinstitute/pepper-api-backend:$docker_image_tag \
        /bin/bash -s <<-EOM
            set -u
            set -o errexit

            function cleanup {
                local rv=\$?
                trap - EXIT

                # Docker might create the folder (and the contained files)
                # with a user of 0:0 (root:root, and a umask of 022. This
                # can prevent the parent environment from removing the files
                # without invoking root priviledges. Make sure everyone can
                # change the files.
                chmod ugo+rwx "/app/target/surefire-reports"
                chmod ugo+rw -R "/app/target/surefire-reports"
                chown -R `id -u`:`id -g` "/app/target/surefire-reports"
                exit \$rv
            }

            trap cleanup EXIT

            TESTCONTAINERS_RYUK_DISABLED=true \
            mvn -Ditext.license=/app/itextkey.xml \
                -Dmaven.repo.local=/app/repo \
                -Dconfig.file=config/local.conf \
                -o \
                --batch-mode \
                checkstyle:checkstyle test surefire-report:report-only

            # NOTE: sonarqube currently does not support Java 11.
            # mvn -Ditext.license=/app/itextkey.xml \
            #     -Dmaven.repo.local=/app/repo \
            #     -Dsonar.host.url=$sonar_server \
            #     -Dconfig.file=config/local.conf \
            #     -o \
            #     --batch-mode \
            #     sonar:sonar || echo 'warning: failed to run maven goal sonar:sonar'
EOM

}


function docker_push() {
    echo "pushing pepper-api-backend:${GIT_SHA}"
    docker push broadinstitute/pepper-api-backend:${GIT_SHA_SHORT}
    echo "pushing pepper-api-backend:$tag"
    docker push broadinstitute/pepper-api-backend:$tag
}

function docker_nginx_build {
    echo "building and pushing nginx broadinstitute/pepper-nginx:$tag"

    # This handoff is needed to shift the build root for the images.
    # In order to build images with the API spec embedded, we need
    #   to combine the results from several steps, and putting
    #   generated files into the `src` tree is not a good move
    #
    # This would be nice as a mvn build step, but mvn is not guaranteed
    # to have been run at this point.
    local docker_path="dockers/nginx"
    local source_dir="src/${docker_path}"

    docker build \
        --build-arg GIT_SHA=$GIT_SHA \
        -t broadinstitute/pepper-nginx:$GIT_SHA_SHORT \
        -f "${source_dir}/Dockerfile" \
        "${source_dir}"

    docker tag "broadinstitute/pepper-nginx:$GIT_SHA_SHORT" "broadinstitute/pepper-nginx:$tag"
}

function docker_nginx_push {
    echo "--- Pushing pepper-nginx images"
    docker push "broadinstitute/pepper-nginx:$GIT_SHA_SHORT"
    docker push "broadinstitute/pepper-nginx:$tag"
}

function print_usage() {
    echo "usage: $NAME <version> <environment> <dir> <option>..."
    echo "       $NAME [-h, --help]"
}

function print_help() {
    cat << EOM
A script to help automate a few different steps of the process
for both local development and in CI deployments.

Configurations are rendered into the directories 'output-config'
and 'output-build-config'. Docker images, such as
'broadinstitute/pepper-api-backend', will be tagged with '\$VER_ENV'.

USAGE:
  $NAME <VER> <ENV> <DIR> [OPTIONS...]

VER
  is the version of the code, should always be 'v1'

ENV
  is the environment against which to build, one of
  'dev', 'test', 'staging', or 'prod'

DIR
  is the local directory from whence 'docker-compose' volumes originate,
  use '.' for local builds, '/app' for CI builds

OPTIONS:
  --config          render local configurations
  --docker-build    build docker images
  --docker-push     build docker images and push to dockerhub
  -h, --help        print this help message
  --jenkins         runs CI workflow of build, test, and push docker images,
                    should only use this in CI environment
  --jenkins-test    only build docker images and run test suite
  --local-deploy    build and test docker images for local development
  --nginx           build and push docker image for nginx
  --test            build and test docker images locally
EOM
}

###
#
# Main

NAME=${0##*/}

if (( $# == 1 )); then
    if [[ "$1" == '-h' ]] || [[  "$1" == '--help' ]]; then
        print_help
        exit 0
    fi
fi

if (( $# < 4 )); then
    print_usage
    exit 1
fi

OUTDIR=output-config
BUILD_OUTDIR=output-build-config
NGINX_PROXIED_HOST=$NGINX_PROXIED_HOST
DOCS_PROXIED_HOST=$DOCS_PROXIED_HOST
VERSION=$1
ENVIRONMENT=$2
DIR=$3
VAULT_TOKEN=$VAULT_TOKEN
tag=${VERSION}_${ENVIRONMENT}

GIT_SHA="${GIT_SHA:-$(git rev-parse --verify HEAD)}"
GIT_SHA_SHORT=${GIT_SHA:0:12}

shift
shift
shift
while [[ -n "$1" ]]; do
    case $1 in
        -h | --help)
            print_help
            exit 0
        ;;
        --config)
            render_config "local"
        ;;
      --gae-config)
            render_config "gae"
        ;;
        --docker-build)
            render_config "local"
            build_docs_image
            docker_build
        ;;
        --test)
            render_config "local"
            build_docs_image
            docker_build
            run_tests "local"
            docker_retag
        ;;
        --jenkins-test)
            render_config
            build_docs_image
            docker_build
            run_tests
        ;;
        --docker-push)
            render_config "local"
            build_docs_image
            push_docs_image
            docker_build
            run_tests
            docker_retag
            docker_push
        ;;
        --nginx)
            docker_nginx_build
            docker_nginx_push
        ;;
        --jenkins)
            render_config
            build_docs_image
            push_docs_image
            docker_build
            run_tests
            docker_retag
            docker_push
            docker_nginx_build
            docker_nginx_push
        ;;
        --local-deploy)
            NGINX_PROXIED_HOST="backend"
            DOCS_PROXIED_HOST="documentation"

            render_config local
            build_docs_image
            docker_build
            run_tests local
            docker_retag
            docker_nginx_build
    esac
    shift
done
