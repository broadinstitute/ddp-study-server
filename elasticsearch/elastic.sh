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
  elastic.sh [OPTIONS] <COMMAND> <ARGS>...

OPTIONS:
  -h, --help    print this help message

COMMANDS:
  create-index <INDEX_FILE>
    create the index pointed to by the given file

  create-templated-index <INDEX_FILE> <INDEX_NAME>
    create the index but use the given name for name substitution

  upload-role <ROLE_FILE>
    create/update the role pointed to by the given file

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
    create-templated-index)
      create_index "$4" "$5"
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
