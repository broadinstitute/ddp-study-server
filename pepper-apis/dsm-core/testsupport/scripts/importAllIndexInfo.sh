##!/usr/bin/env bash
##script will try to load all the index info from the esdata directory into the specified ES server
#set -e
##set -x
#
## if first argument is empty or if it is equal to --help, print the command usage
#if [ -z "$1" ] || [ "$1" = "--help" ]; then
#  echo "Usage: $0 <serverBaseUrl> [dataDirectory]"
#  echo "Options:"
#  echo "  --help            show this help message and exit"
#  exit 0
#fi
#
#ES_SERVER_BASE_URL=$1
#DATA_FILE_DIR=${2:-../esdata}
#
#for file in "${DATA_FILE_DIR}"/*-data.json; do
#  #extract the index name from the file name and remove the -data.json suffix
#  INDEX_NAME=$(echo "${file}" | xargs -n 1 basename | rev | cut -c11- | rev)
#  ./importIndexInfo.sh "$ES_SERVER_BASE_URL" "$INDEX_NAME" "$DATA_FILE_DIR" &
#done
#wait
#echo "All done!"
#
#
#
