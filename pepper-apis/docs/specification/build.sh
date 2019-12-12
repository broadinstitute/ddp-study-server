#!/usr/bin/env sh

set -e

SRC="src"
BUILD="build"

usage() {
    cat <<-EOM

Usage: $0 COMMAND [OPTIONS]

Builds the Pepper API documentation

Commands:
    help              Print usage
    documentation     Generates a monolithic specification file and static HTML version of the documentation
    docker            Builds the docker image for serving the documentation
EOM
}

usage_docker() {
  cat <<-EOM

Usage: $0 docker IMAGE

Builds the ReDoc docker image for the pepper API documentation.

The IMAGE parameter sets the name, and optionally a tag (using the name:tag format) for the built
image. The value may also be supplied by setting an IMAGE_NAME environment variable. If the value
is specified both at execution time, and in the environment, the execution time parameter value
will be used.
EOM
}


build_docs() {
    npm install

    rm -rf "${BUILD}" || true
    mkdir -p "${BUILD}"

    $(npm bin)/swagger-cli bundle --outfile "${BUILD}/pepper.yml" "${SRC}/pepper.yml"
    $(npm bin)/speccy lint --rules="rules.yml" "${BUILD}/pepper.yml"
    $(npm bin)/redoc-cli bundle --output "${BUILD}/pepper.html" "${SRC}/pepper.yml"
}

build_docker() {
    if ! command -v docker 2>&1 1>/dev/null; then
        echo "docker not found in PATH"
        return 2
    fi

    local image="$1"
    docker build \
        --tag "$image" \
        --file Dockerfile \
        .
}

if [ -z "$1" ]; then
    echo "no command specified"
    usage
    exit 1
fi

command="$1"; shift

case "$command" in
help)
    help_command="$1"

    if [ "$help_command" = "docker" ]; then
        usage_docker
    else
        usage
    fi
;;

documentation)
    build_docs
;;

docker)
    image="${1:-$IMAGE_NAME}"
    if [ -z "$image" ]; then
        echo "image name not specified"
        usage_docker
        exit 1
    fi

    build_docs
    build_docker "$image"
;;

*)
    echo "unrecognized command '${command}'"
    usage
    exit 1
;;
esac

exit 0
