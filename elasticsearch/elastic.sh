#!/usr/bin/env bash
#
# Simple script for interacting with elasticsearch.

set -euo pipefail

VERSION=""
ENVIRONMENT=""
BASE_URL=""
CREDENTIALS=""

print_usage() {
  echo 'usage: elastic.sh <version> <environment> <command> <args>...'
  echo '       elastic.sh [-h, --help]'
}

print_help() {
  cat << 'EOM'
USAGE:
  elastic.sh [OPTIONS] <VER> <ENV> <COMMAND> <ARGS>...

VER:
  the version, i.e. v1

ENV:
  the environment, i.e. local/dev/test/staging/prod

OPTIONS:
  -h, --help    print this help message

COMMANDS:
  create-index <INDEX_FILE>
    create the index pointed to by the given file

  create-script <SCRIPT-FILE>
    create the script pointed to by the given file

  create-ingest-pipeline <PIPELINE-FILE>
    create the pipeline pointed to by the given file

  upload-role <ROLE_FILE>
    create/update the role pointed to by the given file

  upload-template <INDEX_TEMPLATE_FILE>
    create/update the index template pointed to by the given file

  upload-user <USER_FILE> <USER_NAME>
    finds the user in the given file and create/update it

If environment is `local`, this will use the default host and port on localhost
when making API calls to elasticsearch.
EOM
}

read_vault_value() {
  local ver="$1"
  local env="$2"
  local name="$3"
  vault read --field "$name" "secret/pepper/$env/$ver/elasticsearch"
}

set_api_credentials() {
  if [[ "$ENVIRONMENT" == 'local' ]]; then
    BASE_URL='http://localhost:9200'
  else
    BASE_URL="$(read_vault_value "$VERSION" "$ENVIRONMENT" 'endpoint')"
    local username="$(read_vault_value "$VERSION" "$ENVIRONMENT" 'rootUsername')"
    local password="$(read_vault_value "$VERSION" "$ENVIRONMENT" 'rootPassword')"
    CREDENTIALS="$(echo -n "$username:$password" | base64)"
  fi
}

create_index() {
  local index_file="$1"
  local index_name="${index_file##*/}"
  index_name="${index_name%.json}"

  if (( $# == 2 )); then
    index_name="${index_name%.any}"
    index_name="$index_name.$2"
  fi

  curl -s -X PUT "$BASE_URL/$index_name?pretty" \
    -H "Authorization: Basic $CREDENTIALS" \
    -H 'Content-Type: application/json' \
    -d "@$index_file"
}

create_script() {
  local script_file="$1"
  local script_name="${script_file##*/}"
  script_name="${script_name%.painless}"

  local script_content='{"script": {"lang": "painless", "source": "'
  while read line; do
    script_content="$script_content$line"
  done < $1
  script_content="$script_content\" } }'"

  curl -s -X PUT "$BASE_URL/_scripts/$script_name" \
    -H "Authorization: Basic $CREDENTIALS" \
    -H 'Content-Type: application/json' \
    -d "$script_content"
}

create_ingest_pipeline() {
  local pipeline_file="$1"
  local pipeline_name="${pipeline_file##*/}"
  pipeline_name="${pipeline_name%.json}"

  curl -s -X PUT "$BASE_URL/_ingest/pipeline/$pipeline_name" \
    -H "Authorization: Basic $CREDENTIALS" \
    -H 'Content-Type: application/json' \
    -d "@$pipeline_file"
}

upload_template() {
  local template_file="$1"
  local template_name="${template_file##*/}"
  template_name="${template_name%.json}"

  curl -s -X PUT "$BASE_URL/_template/$template_name?pretty" \
    -H "Authorization: Basic $CREDENTIALS" \
    -H 'Content-Type: application/json' \
    -d "@$template_file"
}

update_mapping() {
  payload="`python3 participants_structured_reader.py`"
  indexes=`curl -s -X GET "$BASE_URL/_aliases" \
                -H "Authorization: Basic $CREDENTIALS" \
                -H 'Content-Type: application/json'`
  participants_structured=`python3 participants_structured_extractor.py "$indexes"`

  all_indices=""
  for index in $participants_structured
  do
    index_setting=`curl -s -X GET "$BASE_URL/$index/_settings" \
        -H "Authorization: Basic $CREDENTIALS" \
        -H 'Content-Type: application/json'`
    all_indices+="`python3 participants_structured_filter.py "$index_setting" "$index"`"
  done

  curl -s -X PUT "$BASE_URL/$all_indices/_mapping/_doc" \
      -H "Authorization: Basic $CREDENTIALS" \
      -H 'Content-Type: application/json' \
      -d "$payload"
}

upload_user() {
  local user_file="$1"
  local user_name="$2"
  local payload="$(jq ".users[] | select(.username == \"$user_name\")" $user_file)"

  curl -s -X POST "$BASE_URL/_xpack/security/user/$user_name?pretty" \
    -H "Authorization: Basic $CREDENTIALS" \
    -H 'Content-Type: application/json' \
    -d "$payload"
}

upload_role() {
  local role_file="$1"
  local role_name="${role_file##*/}"
  role_name="${role_name%.json}"

  curl -s -X POST "$BASE_URL/_xpack/security/role/$role_name?pretty" \
    -H "Authorization: Basic $CREDENTIALS" \
    -H 'Content-Type: application/json' \
    -d "@$role_file"
}

main() {
  if (( $# < 1 )); then
    print_usage
    exit 1
  fi

  if [[ "$1" == "-h" ]] || [[ "$1" == "--help" ]]; then
    print_help
    exit 0
  fi

  VERSION="$1"
  ENVIRONMENT="$2"
  set_api_credentials

  case "$3" in
    create-index)
      create_index "$4"
      ;;
    create-script)
      create_script "$4"
      ;;
    create-ingest-pipeline)
      create_ingest_pipeline "$4"
      ;;
    upload-template)
      upload_template "$4"
      ;;
    update_mapping)
      update_mapping
      ;;
    upload-user)
      upload_user "$4" "$5"
      ;;
    upload-role)
      upload_role "$4"
      ;;
    *)
      echo "unknown command: '$3'"
      ;;
  esac
}

main "$@"
