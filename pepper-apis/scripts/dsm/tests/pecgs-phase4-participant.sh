#!/bin/bash


if [ -z "$1" ] || [ -z "$2" ] || [ -z "$3" ] || [ -z "$4" ] || [ -z "$5" ] || [ -z "$6" ] || [ -z "$7" ] || [ -z "$8" ]; then
  echo "Please provide the project name and bucket name as arguments."
  echo "Usage: ./pecgs-phase4-participant.sh <dsm-url> <participant-guid> <participant-shortId> <study> <env> <userId> <Auth0 Token file path> <BSP Token file path>"
  exit 1
fi

if [ $5 = "prod" ] || [ $1 = "dsm.datadonationplatform.org"]; then
  echo "You can't use this script in production!"
  exit
fi

echo "The participant should already be enrolled, and have consented yes to tissue collection, and yes to shared learning.
The script will not change that"

dsm_url=$1
guid=$2
shortId=$3
study=$4
env=$5
userId=$6
auth0TokenFile=$7
bspTokenFile=$8


auth0Token=$(cat "$auth0TokenFile")

if [ -f "$auth0TokenFile" ]; then
  auth0Token=$(cat "$auth0TokenFile")
else
  echo "$auth0TokenFile file does not exist"
fi

if [ -f "$bspTokenFile" ]; then
  bspToken=$(cat "$bspTokenFile")
else
  echo "$bspTokenFile file does not exist"
fi

function main() {

  check-if-mercury-sample-exists


}

function send-and-receive-saliva {

kits_without_label_response=$(curl "$dsm_url/ui/kitRequests?realm=$study&target=uploaded&kitType=SALIVA" \
  -H "Authorization Bearer $auth0Token" \
  -H "authority: $dsm_url" \
  -H "accept: application/json" \
  -H "accept-language: en-US,en;q=0.9" \
  -H "authorization: Bearer $auth0Token" \
  -H "content-type: application/json" \
  -H "cookie: _gid=GA1.2.1462420073.1687445141; _ga=GA1.2.662503047.1684868999; _ga_KB65C93K2D=GS1.1.1687527731.2.0.1687527731.0.0.0" \
  -H "referer: $dsm_url/$study/shippingUploaded" \
  -H "sec-ch-ua: \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"114\", \"Google Chrome\";v=\"114\"" \
  -H "sec-ch-ua-mobile: ?0" \
  -H "sec-ch-ua-platform: "macOS"" \
  -H "sec-fetch-dest: empty" \
  -H "sec-fetch-mode: cors" \
  -H "sec-fetch-site: same-origin" \
  -H "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36" \
  --compressed)

search_id=$guid

# Use jq to parse through the array and find the matching object
matching_object=$(echo "$kits_without_label_response" | jq -c --arg id "$search_id" '.[] | select(.ddpParticipantId == $id)')

if [[ $matching_object = "" ]]; then
    echo "The selected object is null, there is no kit in kits without label"
    echo "Going to search in the Queue kits "
    response=$(curl "$dsm_url/ui/kitRequests?realm=$study&target=queue&kitType=SALIVA" \
        -H "authority: $dsm_url" \
        -H "accept: application/json" \
        -H "accept-language: en-US,en;q=0.9" \
        -H "authorization: Bearer $auth0Token" \
        -H "content-type: application/json" \
        -H "cookie: _ga=GA1.2.662503047.1684868999; _ga_KB65C93K2D=GS1.1.1687527731.2.0.1687527731.0.0.0" \
        -H "referer: $dsm_url/$study/shippingQueue" \
        -H "sec-ch-ua: \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"114\", \"Google Chrome\";v=\"114\"" \
        -H "sec-ch-ua-mobile: ?0" \
        -H "sec-ch-ua-platform: \"macOS\"" \
        -H "sec-fetch-dest: empty" \
        -H "sec-fetch-mode: cors" \
        -H "sec-fetch-site: same-origin" \
        -H "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36" \
        --compressed)

        search_id=$guid

        # Use jq to parse through the array and find the matching object
        matching_object=$(echo "$kits_without_label_response" | jq -c --arg id "$search_id" '.[] | select(.ddpParticipantId == $id)')

        if [[ $matching_object = "" ]]; then
            echo "The selected object is null, there is no kit in kits without label"
            echo "Going to search in the received kits "
            search-received-kits
            return
        fi
fi

# Print the matching object
echo "Matching kit without label: $matching_object"

shippingId=$(echo "$matching_object" | jq -r ".ddpLabel" )

echo "Shipping ID is : $shippingId"

kitLabel=$(openssl rand -base64 32 | tr -dc "a-zA-Z0-9" | head -c14)

echo "SALIVA kit label generated is $kitLabel"

response=$(curl "$dsm_url/ui/initialScan?realm=$study&userId=$userId" \
  -H "Authorization: Bearer $auth0Token" \
  -H "authority: $dsm_url" \
  -H "accept: application/json" \
  -H "accept-language: en-US,en;q=0.9" \
  -H "content-type: application/json" \
  -H "cookie: _gid=GA1.2.1462420073.1687445141; _ga=GA1.2.662503047.1684868999; _ga_KB65C93K2D=GS1.1.1687527731.2.0.1687527731.0.0.0" \
  -H "origin: $dsm_url" \
  -H "referer: $dsm_url/$study/scan?scannerType=initial" \
  -H "sec-ch-ua: \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"114\", \"Google Chrome\";v=\"114\"" \
  -H "sec-ch-ua-mobile: ?0" \
  -H "sec-ch-ua-platform: "macOS"" \
  -H "sec-fetch-dest: empty" \
  -H "sec-fetch-mode: cors" \
  -H "sec-fetch-site: same-origin" \
  -H "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36" \
  --data-raw "[{\"kitLabel\":\"$random_saliva_label\",\"hruid\":\"$shortId\"}]" \
  --compressed)

echo $response


response=$(curl "$dsm_url/ui/finalScan?realm=$study&userId=$userId" \
  -H "authority: $dsm_url" \
  -H "accept: application/json" \
  -H "accept-language: en-US,en;q=0.9" \
  -H "authorization: Bearer $auth0Token" \
  -H "content-type: application/json" \
  -H "cookie: _gid=GA1.2.1462420073.1687445141; _ga=GA1.2.662503047.1684868999; _ga_KB65C93K2D=GS1.1.1687527731.2.0.1687527731.0.0.0" \
  -H "origin: $dsm_url" \
  -H "referer: $dsm_url/$study/scan?scannerType=final" \
  -H "sec-ch-ua: \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"114\", \"Google Chrome\";v=\"114\"" \
  -H "sec-ch-ua-mobile: ?0" \
  -H "sec-ch-ua-platform: \"macOS\"" \
  -H "sec-fetch-dest: empty" \
  -H "sec-fetch-mode: cors" \
  -H "sec-fetch-site: same-origin" \
  -H "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36" \
  --data-raw "[{\"kitLabel\":\"$kitLabel\",\"ddpLabel\":\"$shippingId\"}]" \
  --compressed)

echo $response

 response=$(curl "$dsm_url/ddp/ClinicalKits/$kitLabel"  -H "Authorization: Bearer $bspToken")

  echo $response

}

function set-onc-history-values {

  response=$(curl "$dsm_url/ui/patch" \
    -X "PATCH" \
    -H "authority: $dsm_url" \
    -H "accept: application/json" \
    -H "accept-language: en-US,en;q=0.9" \
    -H "authorization: Bearer $auth0Token" \
    -H "content-type: application/json" \
    -H "cookie: _gid=GA1.2.1462420073.1687445141; _ga=GA1.2.662503047.1684868999; _ga_KB65C93K2D=GS1.1.1687527731.2.0.1687527731.0.0.0" \
    -H "origin: $dsm_url" \
    -H "referer: $dsm_url/$study/participantList" \
    -H "sec-ch-ua: \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"114\", \"Google Chrome\";v=\"114\"" \
    -H "sec-ch-ua-mobile: ?0" \
    -H "sec-ch-ua-platform: \"macOS\"" \
    -H "sec-fetch-dest: empty" \
    -H "sec-fetch-mode: cors" \
    -H "sec-fetch-site: same-origin" \
    -H "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36" \
    --data-raw "{\"id\":null,\"user\":\"$userId\",\"nameValue\":{\"name\":\"oD.datePx\",\"value\":\"2023-06-13\"},\"ddpParticipantId\":\"$guid\",\"parent\":\"participantId\",\"tableAlias\":\"oD\",\"isUnique\":true,\"realm\":\"$study\"}" \
    --compressed)

    echo $response

  oncHistoryDetailId=$(echo "$response" | jq -r ".oncHistoryDetailId" )

  accessionNumber=$(openssl rand -base64 32 | tr -dc "a-zA-Z0-9" | head -c10)

  echo "Accession number generated is $accessionNumber for ons history with id $oncHistoryDetailId"


  response=$(curl "$dsm_url/ui/patch" \
    -X "PATCH" \
    -H "authority: $dsm_url" \
    -H "accept: application/json" \
    -H "accept-language: en-US,en;q=0.9" \
    -H "authorization: Bearer $auth0Token" \
    -H "content-type: application/json" \
    -H "cookie: _ga=GA1.2.662503047.1684868999; _ga_KB65C93K2D=GS1.1.1687527731.2.0.1687527731.0.0.0" \
    -H "origin: $dsm_url" \
    -H "referer: $dsm_url/$study/participantList" \
    -H "sec-ch-ua: \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"114\", \"Google Chrome\";v=\"114\"" \
    -H "sec-ch-ua-mobile: ?0" \
    -H "sec-ch-ua-platform: \"macOS\"" \
    -H "sec-fetch-dest: empty" \
    -H "sec-fetch-mode: cors" \
    -H "sec-fetch-site: same-origin" \
    -H "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36" \
    --data-raw "{\"id\":$oncHistoryDetailId,\"user\":\"$userId\",\"nameValue\":{\"name\":\"oD.accessionNumber\",\"value\":\"$accessionNumber\"},\"ddpParticipantId\":\"$guid\",\"parent\":\"participantId\",\"tableAlias\":\"oD\",\"isUnique\":true,\"realm\":\"$study\"}" \
    --compressed)

  echo $response
    
  response=$(curl "$dsm_url/ui/patch" \
    -X "PATCH" \
    -H "authority: $dsm_url" \
    -H "accept: application/json" \
    -H "accept-language: en-US,en;q=0.9" \
    -H "authorization: Bearer $auth0Token" \
    -H "content-type: application/json" \
    -H "cookie: _ga=GA1.2.662503047.1684868999; _ga_KB65C93K2D=GS1.1.1687527731.2.0.1687527731.0.0.0" \
    -H "origin: $dsm_url" \
    -H "referer: $dsm_url/$study/participantList" \
    -H "sec-ch-ua: \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"114\", \"Google Chrome\";v=\"114\"" \
    -H "sec-ch-ua-mobile: ?0" \
    -H "sec-ch-ua-platform: \"macOS\"" \
    -H "sec-fetch-dest: empty" \
    -H "sec-fetch-mode: cors" \
    -H "sec-fetch-site: same-origin" \
    -H "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36" \
    --data-raw "{\"id\":$oncHistoryDetailId,\"user\":\"$userId\",\"nameValue\":{\"name\":\"oD.request\",\"value\":\"request\"},\"ddpParticipantId\":\"$guid\",\"parent\":\"participantId\",\"tableAlias\":\"oD\",\"isUnique\":true,\"realm\":\"$study\"}" \
    --compressed )

  echo $response

  response=$(curl "$dsm_url/ui/patch" \
    -X "PATCH" \
    -H "authority: $dsm_url" \
    -H "accept: application/json" \
    -H "accept-language: en-US,en;q=0.9" \
    -H "authorization: Bearer $auth0Token" \
    -H "content-type: application/json" \
    -H "cookie: _ga=GA1.2.662503047.1684868999; _ga_KB65C93K2D=GS1.1.1687527731.2.0.1687527731.0.0.0" \
    -H "origin: $dsm_url" \
    -H "referer: $dsm_url/$study/participantList" \
    -H "sec-ch-ua: \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"114\", \"Google Chrome\";v=\"114\"" \
    -H "sec-ch-ua-mobile: ?0" \
    -H "sec-ch-ua-platform: \"macOS\"" \
    -H "sec-fetch-dest: empty" \
    -H "sec-fetch-mode: cors" \
    -H "sec-fetch-site: same-origin" \
    -H "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36" \
    --data-raw "{\"id\":$oncHistoryDetailId,\"user\":\"$userId\",\"nameValue\":{\"name\":\"oD.faxSent\",\"value\":\"2023-06-24\"},\"ddpParticipantId\":\"$guid\",\"parent\":\"participantId\",\"tableAlias\":\"oD\",\"isUnique\":true,\"realm\":\"$study\"}" \
    --compressed)

  echo "setting tissue received date"
  
  response=$(curl "$dsm_url/ui/patch" \
    -X "PATCH" \
    -H "authority: $dsm_url" \
    -H "accept: application/json" \
    -H "accept-language: en-US,en;q=0.9" \
    -H "authorization: Bearer $auth0Token" \
    -H "content-type: application/json" \
    -H "cookie: _ga=GA1.2.662503047.1684868999; _ga_KB65C93K2D=GS1.1.1687527731.2.0.1687527731.0.0.0" \
    -H "origin: $dsm_url" \
    -H "referer: $dsm_url/$study/participantList" \
    -H "sec-ch-ua: \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"114\", \"Google Chrome\";v=\"114\"" \
    -H "sec-ch-ua-mobile: ?0" \
    -H "sec-ch-ua-platform: \"macOS\"" \
    -H "sec-fetch-dest: empty" \
    -H "sec-fetch-mode: cors" \
    -H "sec-fetch-site: same-origin" \
    -H "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36" \
    --data-raw "{\"id\":$oncHistoryDetailId,\"user\":\"$userId\",\"nameValue\":{\"name\":\"oD.tissueReceived\",\"value\":\"2023-06-24\"},\"ddpParticipantId\":\"$guid\",\"parent\":\"participantId\",\"tableAlias\":\"oD\",\"isUnique\":true,\"realm\":\"$study\"}" \
    --compressed)

  echo "setting gender"

  response=$(curl "$dsm_url/ui/patch" \
    -X "PATCH" \
    -H "authority: $dsm_url" \
    -H "accept: application/json" \
    -H "accept-language: en-US,en;q=0.9" \
    -H "authorization: Bearer $auth0Token" \
    -H "content-type: application/json" \
    -H "cookie: _ga=GA1.2.662503047.1684868999; _ga_KB65C93K2D=GS1.1.1687527731.2.0.1687527731.0.0.0" \
    -H "origin: $dsm_url" \
    -H "referer: $dsm_url/$study/participantList" \
    -H "sec-ch-ua: \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"114\", \"Google Chrome\";v=\"114\"" \
    -H "sec-ch-ua-mobile: ?0" \
    -H "sec-ch-ua-platform: \"macOS\"" \
    -H "sec-fetch-dest: empty" \
    -H "sec-fetch-mode: cors" \
    -H "sec-fetch-site: same-origin" \
    -H "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36" \
    --data-raw "{\"id\":$oncHistoryDetailId,\"user\":\"$userId\",\"nameValue\":{\"name\":\"oD.gender\",\"value\":\"female\"},\"ddpParticipantId\":\"$guid\",\"parent\":\"participantId\",\"tableAlias\":\"oD\",\"isUnique\":true,\"realm\":\"$study\"}" \
    --compressed)

  echo $response

  echo "looking up project's sample id prefix"

  response=$(curl "$dsm_url/ui/lookup?field=tCollab&value=undefined&realm=$study&shortId=$shortId" \
    -H "authority: $dsm_url" \
    -H "accept: application/json" \
    -H "accept-language: en-US,en;q=0.9" \
    -H "authorization: Bearer $auth0Token" \
    -H "content-type: application/json" \
    -H "cookie: _ga=GA1.2.662503047.1684868999; _ga_KB65C93K2D=GS1.1.1687527731.2.0.1687527731.0.0.0" \
    -H "referer: $dsm_url/$study/participantList" \
    -H "sec-ch-ua: \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"114\", \"Google Chrome\";v=\"114\"" \
    -H "sec-ch-ua-mobile: ?0" \
    -H "sec-ch-ua-platform: \"macOS\"" \
    -H "sec-fetch-dest: empty" \
    -H "sec-fetch-mode: cors" \
    -H "sec-fetch-site: same-origin" \
    -H "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36" \
    --compressed)

  echo $response

  value="$(echo $response | jq -r '.[0].field1.value')_T"

  bspSampleId="$value$(openssl rand -base64 32 | tr -dc "a-zA-Z0-9" | head -c4)"

  response=$(curl "$dsm_url/ui/patch" \
    -X "PATCH" \
    -H "authority: $dsm_url" \
    -H "accept: application/json" \
    -H "accept-language: en-US,en;q=0.9" \
    -H "authorization: Bearer $auth0Token" \
    -H "content-type: application/json" \
    -H "cookie: _ga=GA1.2.662503047.1684868999; _ga_KB65C93K2D=GS1.1.1687527731.2.0.1687527731.0.0.0" \
    -H "origin: $dsm_url" \
    -H "referer: $dsm_url/$study/participantList" \
    -H "sec-ch-ua: \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"114\", \"Google Chrome\";v=\"114\"" \
    -H "sec-ch-ua-mobile: ?0" \
    -H "sec-ch-ua-platform: \"macOS\"" \
    -H "sec-fetch-dest: empty" \
    -H "sec-fetch-mode: cors" \
    -H "sec-fetch-site: same-origin" \
    -H "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36" \
    --data-raw "{\"id\":null,\"user\":\"$userId\",\"nameValue\":{\"name\":\"t.collaboratorSampleId\",\"value\":\"$bspSampleId\"},\"ddpParticipantId\":\"$guid\",\"parent\":\"oncHistoryDetailId\",\"parentId\":$oncHistoryDetailId,\"tableAlias\":\"t\",\"isUnique\":true,\"realm\":\"$study\"}" \
    --compressed )

  echo $response

  tissueId=$(echo $response | jq -r '.tissueId')

  echo "Tissue Id is $tissueId"

  echo "bsp sample id for tissue is $bspSampleId"

  response=$(curl "$dsm_url/ui/patch" \
    -X "PATCH" \
    -H "authority: $dsm_url" \
    -H "accept: application/json" \
    -H "accept-language: en-US,en;q=0.9" \
    -H "authorization: Bearer $auth0Token" \
    -H "content-type: application/json" \
    -H "cookie: _ga=GA1.2.662503047.1684868999; _ga_KB65C93K2D=GS1.1.1687527731.2.0.1687527731.0.0.0" \
    -H "origin: $dsm_url" \
    -H "referer: $dsm_url/$study/participantList" \
    -H "sec-ch-ua: \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"114\", \"Google Chrome\";v=\"114\"" \
    -H "sec-ch-ua-mobile: ?0" \
    -H "sec-ch-ua-platform: \"macOS\"" \
    -H "sec-fetch-dest: empty" \
    -H "sec-fetch-mode: cors" \
    -H "sec-fetch-site: same-origin" \
    -H "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36" \
    --data-raw "{\"id\":\"$tissueId\",\"user\":\"$userId\",\"nameValue\":{\"name\":\"t.ussCount\",\"value\":\"1\"},\"ddpParticipantId\":\"$guid\",\"parent\":\"oncHistoryDetailId\",\"parentId\":$oncHistoryDetailId,\"tableAlias\":\"t\",\"isUnique\":true,\"realm\":\"$study\"}" \
    --compressed)

  echo $response

  response=$(curl "$dsm_url/ui/patch" \
    -X "PATCH" \
    -H "authority: $dsm_url" \
    -H "accept: application/json" \
    -H "accept-language: en-US,en;q=0.9" \
    -H "authorization: Bearer $auth0Token" \
    -H "content-type: application/json" \
    -H "cookie: _ga=GA1.2.662503047.1684868999; _ga_KB65C93K2D=GS1.1.1687527731.2.0.1687527731.0.0.0" \
    -H "origin: $dsm_url" \
    -H "referer: $dsm_url/$study/participantList" \
    -H "sec-ch-ua: \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"114\", \"Google Chrome\";v=\"114\"" \
    -H "sec-ch-ua-mobile: ?0" \
    -H "sec-ch-ua-platform: \"macOS\"" \
    -H "sec-fetch-dest: empty" \
    -H "sec-fetch-mode: cors" \
    -H "sec-fetch-site: same-origin" \
    -H "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36" \
    --data-raw "{\"id\":\"$tissueId\",\"user\":\"$userId\",\"nameValue\":{\"name\":\"t.sentGp\",\"value\":\"2023-06-24\"},\"ddpParticipantId\":\"$guid\",\"parent\":\"oncHistoryDetailId\",\"parentId\":$oncHistoryDetailId,\"tableAlias\":\"t\",\"isUnique\":true,\"realm\":\"$study\"}" \
    --compressed)

  echo $response

  smId="$value$(openssl rand -base64 32 | tr -dc "a-zA-Z0-9" | head -c8)"

  response=$(curl "$dsm_url/ui/patch" \
    -X "PATCH" \
    -H "authority: $dsm_url" \
    -H "accept: application/json" \
    -H "accept-language: en-US,en;q=0.9" \
    -H "authorization: Bearer $auth0Token" \
    -H "content-type: application/json" \
    -H "cookie: _ga=GA1.2.662503047.1684868999; _ga_KB65C93K2D=GS1.1.1687527731.2.0.1687527731.0.0.0" \
    -H "origin: $dsm_url" \
    -H "referer: $dsm_url/$study/participantList" \
    -H "sec-ch-ua: \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"114\", \"Google Chrome\";v=\"114\"" \
    -H "sec-ch-ua-mobile: ?0" \
    -H "sec-ch-ua-platform: \"macOS\"" \
    -H "sec-fetch-dest: empty" \
    -H "sec-fetch-mode: cors" \
    -H "sec-fetch-site: same-origin" \
    -H "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36" \
    --data-raw "{\"id\":null,\"user\":\"$userId\",\"nameValue\":{\"name\":\"sm.smIdType\",\"value\":\"uss\"},\"ddpParticipantId\":\"$guid\",\"parent\":\"tissueId\",\"parentId\":\"$tissueId\",\"nameValues\":[{\"name\":\"smIdType\",\"value\":\"uss\"},{\"name\":\"smIdValue\",\"value\":\"$smId\"}],\"tableAlias\":\"sm\",\"isUnique\":true,\"realm\":\"$study\"}" \
    --compressed)

  echo $response

  response=$(curl "$dsm_url/ddp/ClinicalKits/$smId"  -H "Authorization: Bearer $bspToken")

  echo $response

 response=$(curl "$dsm_url/ui/mercurySamples?realm=$study&ddpParticipantId=$guid" \
   -H "authority: $dsm_url" \
   -H "accept: application/json" \
   -H "accept-language: en-US,en;q=0.9" \
   -H "authorization: Bearer $auth0Token" \
   -H "content-type: application/json" \
   -H "cookie: _ga=GA1.2.662503047.1684868999; _ga_KB65C93K2D=GS1.1.1687527731.2.0.1687527731.0.0.0" \
   -H "referer: $dsm_url/$study/participantList" \
   -H "sec-ch-ua: \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"114\", \"Google Chrome\";v=\"114\"" \
   -H "sec-ch-ua-mobile: ?0" \
   -H "sec-ch-ua-platform: \"macOS\"" \
   -H "sec-fetch-dest: empty" \
   -H "sec-fetch-mode: cors" \
   -H "sec-fetch-site: same-origin" \
   -H "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36" \
   --compressed )

 echo $response

 selected_object=$(echo $response | jq -r --arg sample "$bspSampleId" '.[] | select(.sample == $sample)')

 echo $selected_object
 
 response=$(curl "$dsm_url/ui/submitMercuryOrder?realm=$study&ddpParticipantId=$guid&userId=$userId" \
   -H "authority: $dsm_url" \
   -H "accept: application/json" \
   -H "accept-language: en-US,en;q=0.9" \
   -H "authorization: Bearer $auth0Token" \
   -H "content-type: application/json" \
   -H "cookie: _ga=GA1.2.662503047.1684868999; _ga_KB65C93K2D=GS1.1.1687527731.2.0.1687527731.0.0.0" \
   -H "origin: $dsm_url" \
   -H "referer: $dsm_url/$study/participantList" \
   -H "sec-ch-ua: \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"114\", \"Google Chrome\";v=\"114\"" \
   -H "sec-ch-ua-mobile: ?0" \
   -H "sec-ch-ua-platform: \"macOS\"" \
   -H "sec-fetch-dest: empty" \
   -H "sec-fetch-mode: cors" \
   -H "sec-fetch-site: same-origin" \
   -H "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36" \
   --data-raw "[{\"sampleType\":\"Tumor\",\"sample\":\"$bspSampleId\",\"sampleStatus\":\"Sent to GP\",\"collectionDate\":\"2023-06-13\",\"sequencingOrderDate\":0,\"tissueId\":$tissueId,\"dsmKitRequestId\":null,\"sequencingRestriction\":\"\",\"lastStatus\":\"\",\"lastOrderNumber\":\"\",\"pdoOrderId\":\"\",\"isSelected\":true}]" \
   --compressed)

 echo $response

 response=$(curl "$dsm_url/ui/mercurySamples?realm=$study&ddpParticipantId=$guid" \
    -H "authority: $dsm_url" \
    -H "accept: application/json" \
    -H "accept-language: en-US,en;q=0.9" \
    -H "authorization: Bearer $auth0Token" \
    -H "content-type: application/json" \
    -H "cookie: _ga=GA1.2.662503047.1684868999; _ga_KB65C93K2D=GS1.1.1687527731.2.0.1687527731.0.0.0" \
    -H "referer: $dsm_url/$study/participantList" \
    -H "sec-ch-ua: \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"114\", \"Google Chrome\";v=\"114\"" \
    -H "sec-ch-ua-mobile: ?0" \
    -H "sec-ch-ua-platform: \"macOS\"" \
    -H "sec-fetch-dest: empty" \
    -H "sec-fetch-mode: cors" \
    -H "sec-fetch-site: same-origin" \
    -H "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36" \
    --compressed )

 echo $response

 selected_object=$(echo $response | jq -r --arg sample "$bspSampleId" '.[] | select(.sample == $sample)')

 echo $selected_object

 lastOrderNumber=$(echo $selected_object | jq -r '.lastOrderNumber')

 echo "order number is $lastOrderNumber"

 project="broad-ddp-$env"

 pdo="PDO-$(openssl rand -base64 32 | tr -dc "a-zA-Z0-9" | head -c6)"

 echo "PDO number is $pdo"

 topic="$env-mercury-dsm-task"

 gcloud --project=$project pubsub topics publish $topic \
   --message "{\"status\":{\"orderID\":\"$lastOrderNumber\",\"orderStatus\":\"Approved\",\"details\":\"Successfully created order\",\"pdoKey\": \"$pdo\",\"json\": \"\"}}"

 echo "published status message to pubsub"


}

function check-if-mercury-sample-exists {

  echo "checking if a mercury order already exists"

  response=$(curl "$dsm_url/ui/mercurySamples?realm=$study&ddpParticipantId=$guid" \
     -H "authority: $dsm_url" \
     -H "accept: application/json" \
     -H "accept-language: en-US,en;q=0.9" \
     -H "authorization: Bearer $auth0Token" \
     -H "content-type: application/json" \
     -H "cookie: _ga=GA1.2.662503047.1684868999; _ga_KB65C93K2D=GS1.1.1687527731.2.0.1687527731.0.0.0" \
     -H "referer: $dsm_url/$study/participantList" \
     -H "sec-ch-ua: \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"114\", \"Google Chrome\";v=\"114\"" \
     -H "sec-ch-ua-mobile: ?0" \
     -H "sec-ch-ua-platform: \"macOS\"" \
     -H "sec-fetch-dest: empty" \
     -H "sec-fetch-mode: cors" \
     -H "sec-fetch-site: same-origin" \
     -H "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36" \
     --compressed )

   echo $response

  existing_order=$(echo "$response" | jq '.[] | select(.sampleType=="Tumor" and .lastOrderNumber != null)')


  if [[ $existing_order != "" ]]; then
    echo "The existing order is: $existing_order"

    pdoOrderId=$(echo $existing_order | jq -r '.pdoOrderId != null and .lastStatus!="Failed"')

    if [[ ! $pdoOrderId ]];then
      echo "PDO already exists $pdoOrderId"
      exit
    fi

    lastOrderNumber=$(echo $existing_order | jq -r 'select(.lastOrderNumber!=null) | .lastOrderNumber' | head -1)



    echo "order number is $lastOrderNumber"

    project="broad-ddp-$env"

    pdo="PDO-$(openssl rand -base64 32 | tr -dc "a-zA-Z0-9" | head -c6)"

    echo "PDO number is $pdo"

    topic="$env-mercury-dsm-task"

    gcloud --project=$project pubsub topics publish $topic \
    --message "{\"status\":{\"orderID\":\"$lastOrderNumber\",\"orderStatus\":\"Approved\",\"details\":\"Successfully created order\",\"pdoKey\": \"$pdo\",\"json\": \"\"}}"

    echo "published status message to pubsub"
  else
    if ! send-and-receive-saliva; then
        echo "Participant does not have a saliva sample"
        exit -1
    fi

    if ! set-onc-history-values; then
      echo "Could not set onc history values"
      exit -1
    fi
  fi

}

function search-received-kits {

  echo "Searching received SALIVA kits for $guid"
  
  response=$(curl "$dsm_url/ui/kitRequests?realm=$study&target=received&kitType=SALIVA" \
               -H "authority: $dsm_url" \
               -H "accept: application/json" \
               -H "accept-language: en-US,en;q=0.9" \
               -H "authorization: Bearer $auth0Token" \
               -H "content-type: application/json" \
               -H "cookie: _ga=GA1.2.662503047.1684868999; _ga_KB65C93K2D=GS1.1.1687527731.2.0.1687527731.0.0.0" \
               -H "referer: $dsm_url/$study/shippingReceived" \
               -H "sec-ch-ua: \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"114\", \"Google Chrome\";v=\"114\"" \
               -H "sec-ch-ua-mobile: ?0" \
               -H "sec-ch-ua-platform: \"macOS\"" \
               -H "sec-fetch-dest: empty" \
               -H "sec-fetch-mode: cors" \
               -H "sec-fetch-site: same-origin" \
               -H "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36" \
               --compressed)

  search_id=$guid

  # Use jq to parse through the array and find the matching object
  matching_object=$(echo "$response" | jq -c --arg id "$search_id" '.[] | select(.ddpParticipantId == $id)')

  echo $matching_object
  
  if [[ $matching_object = "" ]]; then
      echo "No received kit for participant"
      echo "please upload a SALIVA kit for $guid"
      exit -1
  fi
  
  set-onc-history-values

}

main

