#!/usr/bin/env bash
# Export contents of an index to 3 separate files: analyzer, mapping and data

set +e
# set -x

if [ -z "$1" ] || [ "$1" = "--help" ]; then
    echo "Usage: $0 [indexName]"
    echo "Options:"
    echo "  --help      show this help message and exit"
    exit 0
fi

INDEX_NAME=$1
DATA_FILE_DIR='../esdata'

# Going to extract needed info out of vault
ES_ADMIN_NAME='pepper_engineering'
ES_ADMIN_PWD=$(vault read --format=json secret/pepper/dev/v1/elasticsearch | jq -r ".data.accounts[]  | select(.username == \"${ES_ADMIN_NAME}\") | .password")
ES_SERVER_BASE_URL=$(vault read --format=json secret/pepper/dev/v1/elasticsearch | jq -r .data.endpoint)

ES_AUTH_FILE=$(mktemp)
echo "user=${ES_ADMIN_NAME}" > ${ES_AUTH_FILE}
echo "password=${ES_ADMIN_PWD}" >> ${ES_AUTH_FILE}

#create an array with the string values "analyzer", "mapping", and "data"
DATA_TYPES=("analyzer" "mapping" "data")

#loop through the array and create a variable for each value
for DATA_TYPE in "${DATA_TYPES[@]}"; do
  elasticdump \
    --input=${ES_SERVER_BASE_URL}/${INDEX_NAME} \
    --output=${DATA_FILE_DIR}/${INDEX_NAME}-${DATA_TYPE}.json \
    --type=${DATA_TYPE} \
    --httpAuthFile ${ES_AUTH_FILE}

    #if elasticdump is not found, show a message and exit
    if [ $? -eq 127 ]; then
      echo "elasticdump utility was not found. Please install it with 'npm install elasticdump -g'"
      exit 1
    fi
done


