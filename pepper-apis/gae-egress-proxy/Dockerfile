FROM ubuntu:focal-20200729

ENV SQUID_VERSION=4.10 \
    SQUID_CACHE_DIR=/var/spool/squid \
    SQUID_LOG_DIR=/var/log/squid \
    SQUID_USER=proxy

RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive apt-get install -y squid=$SQUID_VERSION* \
 && rm -rf /var/lib/apt/lists/*

RUN mkdir /app
COPY squid.conf /app/squid.conf
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod 755 /app/entrypoint.sh

ARG GIT_SHA
RUN echo $GIT_SHA > /app/git_sha.txt

EXPOSE 3128/tcp
ENTRYPOINT ["/app/entrypoint.sh"]
