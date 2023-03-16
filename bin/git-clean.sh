#!/bin/sh

# This script can be used to clean up everything that is not explicitly tracked by
# source control, but ignoring the IntelliJ IDEA configuration, so that it won't be
# lost, even though it's not under source control.
#
# It is preferable to always do a dry run first, if you are unsure if you have any
# important stuff under source control.
#
# "git clean" is what developers unfamiliar with Gradle usually intend, when they
# run "gradle clean". But, those are not the same thing.
#
# If you have persistent build issues, or discrepancies between what the IDEA Gradle
# plugin compiler/builder/debugger and the command line Gradle do, it is a good idea
# to run this script and rebuild from scratch.

REPO_ROOT=$(git rev-parse --show-toplevel)
echo "Examining current repository, rooted at: '${REPO_ROOT}'..."

pushd $REPO_ROOT >/dev/null

echo "git clean operation would delete the following files:"
git clean -nfdx -e .idea

echo
read -n 1 -p "Do you want to clean? [y/N] " reply;
if [ "$reply" != "" ]; then
    echo
fi
if [ "$reply" = "${reply#[Yy]}" ]; then
    echo "Aborted."
else
    echo "Commencing non-dry run of git clean operation..."
    git clean -fdx -e .idea
    echo "Done."
fi

popd >/dev/null
