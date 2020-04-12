# Adapted from https://github.com/marcelcorso/gcloud-pubsub-emulator
FROM golang:alpine as builder

RUN apk update && apk upgrade && apk add --no-cache curl git

RUN curl -s https://raw.githubusercontent.com/eficode/wait-for/master/wait-for -o /usr/bin/wait-for
RUN chmod +x /usr/bin/wait-for

RUN go get github.com/prep/pubsubc

###############################################################################

FROM google/cloud-sdk:alpine

RUN apk --no-cache add \
    bash \
    openjdk8-jre \
    netcat-openbsd && \
    gcloud components install beta pubsub-emulator

COPY --from=builder /usr/bin/wait-for /usr/bin
COPY --from=builder /go/bin/pubsubc   /usr/bin
COPY                run.sh            /run.sh

ENV PORT 8442

EXPOSE $PORT

CMD /run.sh $PORT
