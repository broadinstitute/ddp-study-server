#!/usr/bin/env bash
CI_PROJECT_SLUG='gh/broadinstitute/ddp-study-server'
DEFAULT_BRANCH=develop

CI_TOKEN=$(xargs <  ~/.circleci-token)
if [[ -z $CI_TOKEN ]]; then
  echo "Need to store personal circleci token value at \$HOME/.circleci-token"
  echo "You can generate it from this URL: https://circleci.com/account/api"
  exit 1
fi

BRANCH=${1-$DEFAULT_BRANCH}

echo "Will try to run build/test on branch: $BRANCH"
curl -u "${CI_TOKEN}:" -X POST --header "Content-Type: application/json" -d "{
                                  \"branch\": \"$BRANCH\",
                                  \"parameters\": {
                                      \"on_demand\": true
                                  }
}" "https://circleci.com/api/v2/project/${CI_PROJECT_SLUG}/pipeline"
