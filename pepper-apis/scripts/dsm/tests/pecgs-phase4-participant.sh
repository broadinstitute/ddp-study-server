#!/bin/bash


if [ -z "$1" ] || [ -z "$2" ] || [ -z "$3" ] || [ -z "$4" ]; then
  echo "Please provide the project name and bucket name as arguments."
  echo "Usage: ./pecgs-phase4-participant.sh <dsm-url> <participant-guid> <participant-shortId> <study>"
  exit 1
fi

dsm_url=$1
guid=$2
shortId=$3

function main {
  if ! check-for-saliva; then
      echo "Participant does not have a saliva sample"
      exit -1
    fi
}

function check-for-saliva() {


}

