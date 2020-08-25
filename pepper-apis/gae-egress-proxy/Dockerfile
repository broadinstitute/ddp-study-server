FROM ubuntu:focal-20200729

ENV SQUID_VERSION=4.10 \
    SQUID_CACHE_DIR=/var/spool/squid \
    SQUID_LOG_DIR=/var/log/squid \
    SQUID_USER=proxy

RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive apt-get install -y squid=$SQUID_VERSION* \
 && rm -rf /var/lib/apt/lists/*

RUN mkdir -p $SQUID_LOG_DIR \
 && chmod -R 755 $SQUID_LOG_DIR \
 && chown -R $SQUID_USER:$SQUID_USER $SQUID_LOG_DIR

RUN mkdir -p $SQUID_CACHE_DIR \
 && chown -R $SQUID_USER:$SQUID_USER $SQUID_CACHE_DIR

RUN mkdir /app
COPY squid.conf /app/squid.conf
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod 755 /app/entrypoint.sh

ARG GIT_SHA
RUN echo $GIT_SHA > /app/git_sha.txt

EXPOSE 3128/tcp
ENTRYPOINT ["/app/entrypoint.sh"]
