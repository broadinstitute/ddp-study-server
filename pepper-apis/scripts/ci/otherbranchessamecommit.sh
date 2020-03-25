#!/usr/bin/env bash
#List other branches that refer to current commit
CURRENT_BRANCH=$(git branch --contains HEAD | grep -E '^\*' | grep -Eo '\b\w.*' )
CURRENT_COMMIT=$(git log -1 --format=%H)
REMOTE_BRANCH_PREFIX='refs/remotes/origin/'
git show-ref |  #list all references in current rep
grep $CURRENT_COMMIT |  #find current commit sha
grep -Eo "$REMOTE_BRANCH_PREFIX.*" |  #keep only remote branches
cut -c "$((${#REMOTE_BRANCH_PREFIX} + 1))-" | #remove prefixes
grep -Ev "^${CURRENT_BRANCH}"  #exclude the current working branch

# turns out grep if no matching lines found returns exit code 1. We don't want that for our script!
if [ $? == 1 ] || [ $? == 0 ]
then
  exit 0
else
  exit $?
fi
