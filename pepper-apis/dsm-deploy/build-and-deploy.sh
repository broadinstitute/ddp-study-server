#!/usr/bin/env bash
set -eu -o pipefail

NAME=$(basename "$0")
if (( $# < 2 )); then
  echo "usage: $NAME <PROJECT_ID> <SECRET_ID>"
  exit 1
fi

PROJECT_ID=$1
CONFIG_SECRETS=$2

echo "=> rendering yaml file"
cat StudyManager.tmpl.yaml \
  | sed "s/{{project_id}}/$PROJECT_ID/g" \
  > StudyManager.yaml

echo "=> reading configs from cloud secret manager"
gcloud --project=${PROJECT_ID} secrets versions access latest --secret="${CONFIG_SECRETS}" > vault.conf

echo "=> running build"
mvn -Dcheckstyle.skip=true -DskipTests clean install package -f ../pom.xml

# bundling dependencies
rm -fr lib
mkdir -p lib
mvn -Dcheckstyle.skip=true -f ../pom.xml dependency:copy-dependencies -DoutputDirectory=./dsm-deploy/lib
cp ../target/DSMServer.jar .
cp ../src/main/resources/logback.xml .

# echo "=> deploying to appengine"
# gcloud --project=${PROJECT_ID} app deploy -q --stop-previous-version --promote StudyManager.yaml
