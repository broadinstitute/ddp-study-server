#!/bin/bash
set -e

mkdir -p $SQUID_LOG_DIR
chmod -R 755 $SQUID_LOG_DIR
chown -R $SQUID_USER:$SQUID_USER $SQUID_LOG_DIR

mkdir -p $SQUID_CACHE_DIR
chown -R $SQUID_USER:$SQUID_USER $SQUID_CACHE_DIR

# Initialize cache dirs if they're missing.
if [[ ! -d "$SQUID_CACHE_DIR/00" ]]; then
  squid -Nz -f /app/squid.conf
fi

squid -NYC -d 1 -f /app/squid.conf
