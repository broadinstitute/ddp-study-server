#!/usr/bin/env bash
#script will try to load all the index info from the esdata directory into the specified ES server
set -e
#set -x

ES_SERVER_BASE_URL=$1
DATA_FILE_DIR='../esdata'

for file in "${DATA_FILE_DIR}"/*-data.json; do
  #extract the index name from the file name and remove the -data.json suffix
  INDEX_NAME=$(echo "${file}" | xargs -n 1 basename | rev | cut -c11- | rev)
  ./importIndexInfo.sh "$ES_SERVER_BASE_URL" "$INDEX_NAME" &
done
wait
echo "All done!"



