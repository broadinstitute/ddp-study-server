#!/bin/bash
set -e

# Initialize cache dirs if they're missing.
if [[ ! -d "$SQUID_CACHE_DIR/00" ]]; then
  squid -Nz -f /app/squid.conf
fi

squid -NYC -d 1 -f /app/squid.conf
