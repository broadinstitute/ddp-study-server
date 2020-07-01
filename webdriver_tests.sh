#!/usr/bin/env bash

set -o errexit

render_configs() {
    if [[ "$1" == "local" ]]; then
        output_dir=ddp-automation/output-config
    else
        output_dir=output-config
    fi
    echo "Writing configs to $output_dir"
    INPUT_DIR=ddp-automation/config OUTPUT_DIR=$output_dir DIR=$DIR \
        ENV=$ENV VERSION=$VERSION MANIFEST=ddp-automation/config/manifest.rb \
        ruby pepper-apis/configure.rb -y
}

docker_build() {
    echo "building broadinstitute/pepper-ui-tests:${GIT_SHA:0:12} docker image"
    docker build -t broadinstitute/pepper-ui-tests:${GIT_SHA:0:12} -f Dockerfile-uitesting .
    echo "retagging broadinstitute/pepper-ui-tests:${GIT_SHA:0:12} as $TAG"
    docker tag broadinstitute/pepper-ui-tests:${GIT_SHA:0:12} broadinstitute/pepper-ui-tests:$TAG
}

docker_push() {
    echo "pushing broadinstitute/pepper-ui-tests:${GIT_SHA}"
    docker push broadinstitute/pepper-ui-tests:${GIT_SHA:0:12}
    echo "pushing broadinstitute/pepper-ui-tests:$TAG"
    docker push broadinstitute/pepper-ui-tests:$TAG
}

run_test() {
    local session_name=$1
    local profile=$2
    TEST_PROFILE=$profile BUILD_NAME=$BROWSER_STACK_BUILD_NAME \
    SESSION_NAME=$session_name POST_TO_SLACK=$NOTIFY_SLACK \
    CONFIG_DIR="$PWD/$output_dir" \
    docker-compose -f "$PWD/$output_dir/docker-compose.yaml" up --abort-on-container-exit
}

run_smoke_tests_basil_app() {
    echo "running webdriver smoke tests on broadinstitute/pepper-ui-tests:$TAG on $ENV with configs from $PWD/$output_dir"

    local exit_status=0
    local profiles=(
        SmokeTestsBasilAppWindowsChrome
        SmokeTestsBasilAppWindowsFirefox
        SmokeTestsBasilAppMacSafari
        SmokeTestsBasilAppWindowsIE
        SmokeTestsBasilAppNexus6
    )

    for profile in "${profiles[@]}"; do
        echo "running end to end smoke tests for basil-app: $profile"
        if ! run_test "Smoke Tests" "$profile"; then
            exit_status=1
        fi
    done

    return $exit_status
}

run_smoke_tests_pepper_angio() {
    echo "running webdriver smoke tests on broadinstitute/pepper-ui-tests:$TAG on $ENV with configs from $PWD/$output_dir"

    local exit_status=0
    local profiles=(
        SmokeTestsAngioWindowsChrome
        SmokeTestsAngioWindowsFirefox
        SmokeTestsAngioMacSafari
    )

    for profile in "${profiles[@]}"; do
        echo "running end to end smoke tests for pepper-angio: $profile"
        if ! run_test "Smoke Tests" "$profile"; then
            exit_status=1
        fi
    done

    return $exit_status
}

run_smoke_tests_pepper_brain() {
    echo "running webdriver smoke tests on broadinstitute/pepper-ui-tests:$TAG on $ENV with configs from $PWD/$output_dir"

    local exit_status=0
    local profiles=(
        SmokeTestsBrainWindowsChrome
        SmokeTestsBrainWindowsFirefox
        SmokeTestsBrainMacSafari
        #SmokeTestsAngioNexus6
    )

    for profile in "${profiles[@]}"; do
        echo "running end to end smoke tests for pepper-angio: $profile"
        if ! run_test "Smoke Tests" "$profile"; then
            exit_status=1
        fi
    done

    return $exit_status
}

run_all_smoke_tests() {
    echo "running Angio smoke tests"
    run_smoke_tests_pepper_angio

    echo "running Brain smoke tests"
    run_smoke_tests_pepper_brain
}

run_feature_tests() {
    echo "running webdriver feature tests on broadinstitute/pepper-ui-tests:$TAG on $ENV with configs from $PWD/$output_dir"

    local exit_status=0
    local profiles=(
        FeatureTestsWindowsChrome
        FeatureTestsWindowsFirefox
        FeatureTestsMacSafari
        FeatureTestsWindowsIE
        FeatureTestsNexus6
    )

    for profile in "${profiles[@]}"; do
        echo "running feature tests: $profile"
        if ! run_test "Feature Tests" "$profile"; then
            exit_status=1
        fi
    done

    return $exit_status
}

run_sandbox_tests() {
    echo "running webdriver sandbox tests on broadinstitute/pepper-ui-tests:$TAG on $ENV with configs from $PWD/$output_dir"

    local exit_status=0
    local profiles=(
        SandboxTestsWindowsChrome
        SandboxTestsWindowsFirefox
        SandboxTestsMacSafari
        SandboxTestsWindowsIE
        SandboxTestsNexus6
    )

    for profile in "${profiles[@]}"; do
        echo "running sandbox tests: $profile"
        if ! run_test "Sandbox Tests" "$profile"; then
            exit_status=1
        fi
    done

    return $exit_status
}


VERSION=$1; shift
ENV=$1; shift
DIR=$1; shift

NOTIFY_SLACK=false
BROWSER_STACK_BUILD_NAME="Jenkins ${ENV}"

VAULT_TOKEN=$VAULT_TOKEN
TAG=${VERSION}_${ENV}
GIT_BRANCH=${GIT_BRANCH:-$(git rev-parse --abbrev-ref HEAD)}  # default to current branch
GIT_SHA=$(git rev-parse $GIT_BRANCH)

output_dir=output-config

while [[ "$1" != "" ]]; do
    case $1 in
        --config)
            render_configs "local"
            ;;
        --docker-build)
            render_configs "local"
            docker_build
            ;;
        --docker-push)
            render_configs "local"
            docker_build
            docker_push
            ;;
        --test)
            render_configs "local"
            docker_build
            run_smoke_tests_basil_app
            ;;
        --angio-test)
            render_configs "local"
            docker_build
            run_smoke_tests_pepper_angio
            ;;
        --brain-test)
            render_configs "local"
            docker_build
            run_smoke_tests_pepper_brain
            ;;
        --all-smoke-tests)
            render_configs "local"
            docker_build
            run_all_smoke_tests
            ;;
        --feature-test)
            render_configs "local"
            docker_build
            run_feature_tests
            ;;
        --sandbox-test)
            render_configs "local"
            docker_build
            run_sandbox_tests
            ;;
        --jenkins)
            render_configs
            docker_build
            docker_push
            run_smoke_tests_basil_app
            ;;
        --angio-jenkins)
            render_configs "local"
            docker_build
            docker_push
            run_smoke_tests_pepper_angio
            ;;
        --brain-test-jenkins)
            render_configs "local"
            docker_build
            docker_push
            run_smoke_tests_pepper_brain
            ;;
        --all-smoke-tests-jenkins)
            render_configs "local"
            docker_build
            docker_push
            run_all_smoke_tests
            ;;
        --feature-jenkins)
            render_configs
            docker_build
            docker_push
            run_feature_tests
            ;;
        --sandbox-jenkins)
            render_configs
            docker_build
            docker_push
            run_sandbox_tests
            ;;
    esac
    shift
done
