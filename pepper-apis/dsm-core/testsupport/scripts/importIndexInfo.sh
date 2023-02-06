##!/usr/bin/env bash
## Import contents of an index from 3 separate files: analyzer, mapping and data
## This script is meant to import data that has been exported using exportIndexInfo.sh
#
## set -x
#
## if first argument is empty or if it is equal to --help, print the command usage
#if [ -z "$1" ] || [ -z "$2" ] || [ "$1" = "--help" ]; then
#  echo "Usage: $0 <serverBaseUrl> <indexName> [dataDirectory]"
#  echo "Options:"
#  echo "  --help            show this help message and exit"
#  exit 0
#fi
#
#ES_SERVER_BASE_URL=$1
#INDEX_NAME=$2
#
#ES_INDEX_URL=${ES_SERVER_BASE_URL}/${INDEX_NAME}
#
## Set DATA_FILE_DIR to be the third argument if provided, otherwise default to ../esdata
#DATA_FILE_DIR=${3:-../esdata}
#
#
##Types of imports. Order is important
#DATA_TYPES=("analyzer" "mapping" "data")
#
##loop through the array and create a variable for each value
#for DATA_TYPE in "${DATA_TYPES[@]}"; do
#
#  INPUT_FILE="${DATA_FILE_DIR}/${INDEX_NAME}-${DATA_TYPE}.json"
#
#  if [ -f  "${INPUT_FILE}" ]; then
#
#    elasticdump \
#      --input="${INPUT_FILE}" \
#      --output="${ES_INDEX_URL}" \
#      --type="${DATA_TYPE}" \
#      --limit=1000
#
#    #if elasticdump is not found, show a message and exit
#    if [ $? -eq 127 ]; then
#      echo "elasticdump utility was not found. Please install it with 'npm install elasticdump -g'"
#      exit 1
#    fi
#
#
#    if [ "$DATA_TYPE" = "analyzer" ]; then
#    #set the total number of fields in index limit to 2000. Do this only once
#    curl -XPUT "${ES_INDEX_URL}"/_settings \
#      -d '{"index.mapping.total_fields.limit": 2000}' \
#      -H "Content-Type:application/json"
#    fi
#  else
#      echo "File $INPUT_FILE does not exist. Skipping..."
#  fi
#done
