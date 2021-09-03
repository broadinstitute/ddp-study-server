#!/usr/bin/env bash
#
# This script will create a bundle of binaries and shared objects necessary for
# running clamav scanner. This should be run inside a fresh docker image of
# Ubuntu 18.04, which is the image that Cloud Functions use by default for the
# Java 11 runtime. This script also assumes that we're running as `root`, which
# should be the case if running inside docker.
#
# The final tarball will be located in the `/build` directory, so you can run
# docker with a mount point to grab the bundle.

set -e

echo '=> Updating Ubuntu...'
apt-get update -y

echo ''
echo '=> Saving list of pre-installed standard libraries...'
cd /usr/lib/x86_64-linux-gnu
prev_usr_lib="$(ls *.so* | sort)"
cd /lib/x86_64-linux-gnu
prev_lib="$(ls *.so* | sort)"

echo ''
echo '=> Installing ClamAV...'
apt-get install -y clamav

echo ''
echo '=> Preparing staging area...'
mkdir /staging
cd /staging

echo '=> Copying over binaries and shared library objects...'
cp /usr/bin/clamscan .
cp /usr/bin/freshclam .
cp /usr/lib/x86_64-linux-gnu/*.so* .
cp /lib/x86_64-linux-gnu/*.so* .

echo '=> Removing pre-installed libraries from staging area...'
rm $prev_usr_lib
rm $prev_lib

echo '=> Removing duplicate library objects...'
# Library objects are often symlinks to actual versioned lib files. However,
# when we do a copy, the symlinks get resolved and we copy over the actual
# underlying files. This means we end up with a lot of duplicate object files.
# We (mostly) only use the objects with single-number versions, so delete the
# other ones to minimize size of bundle. Start with the triple-numbers.
rm lib*.so.*.*.*

# Then the double-numbered ones. Explicitly spell these out since there are
# libs with double-number version that we need.
rm libgssapi*.so.*.*
rm libicu*.so.*.*
rm libk5crypto*.so.*.*
rm libkeyutils*.so.*.*
rm libkrb*.so.*.*

# Remove duplicate LLVM. Not sure why but clamav doesn't use the not-numbered one.
rm libLLVM-*.so

echo '=> Packaging clamav bundle...'
tar cvzf clamav.tar.gz *

echo '=> Moving bundle artifact into /build...'
if [[ ! -d /build ]]; then
  mkdir /build
fi
mv clamav.tar.gz /build

echo '=> Finished building clamav bundle.'
