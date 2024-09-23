#!/bin/bash

# Script to perform the release of dataverse. The release version depends on the script argument. For instance, if the
# current version in pom.xml is 1.0.1-SNAPSHOT:
#  - Without any argument => released version -> 1.0.1, next development version -> 1.0.2-SNAPSHOT
#  - major                => released version -> 2.0.0, next development version -> 2.0.1-SNAPSHOT
#  - minor                => released version -> 1.1.0, next development version -> 1.1.1-SNAPSHOT
#
# Project is build without tests. A tag is created for the released version.

set -e

RELEASE_VERSION=$1

parseVersion() {
  EXPRESSION=$1
  ./mvnw build-helper:parse-version help:evaluate -Dexpression=${EXPRESSION} -q -DforceStdout
}

echo "Retrieving version information."

CURRENT_MAJOR_VERSION=$(parseVersion "parsedVersion.majorVersion")
NEXT_MAJOR_VERSION=$(parseVersion "parsedVersion.nextMajorVersion")
CURRENT_MINOR_VERSION=$(parseVersion "parsedVersion.minorVersion")
NEXT_MINOR_VERSION=$(parseVersion "parsedVersion.nextMinorVersion")
CURRENT_PATCH_VERSION=$(parseVersion "parsedVersion.incrementalVersion")
NEXT_PATCH_VERSION=$(parseVersion "parsedVersion.nextIncrementalVersion")

if [ "${RELEASE_VERSION}" == "major" ]; then
  RELEASE_FULL_VERSION="${NEXT_MAJOR_VERSION}.0.0"
  DEVELOPMENT_FULL_VERSION="${NEXT_MAJOR_VERSION}.0.1-SNAPSHOT"
elif [ "${RELEASE_VERSION}" == "minor" ]; then
  RELEASE_FULL_VERSION="${CURRENT_MAJOR_VERSION}.${NEXT_MINOR_VERSION}.0"
  DEVELOPMENT_FULL_VERSION="${CURRENT_MAJOR_VERSION}.${NEXT_MINOR_VERSION}.1-SNAPSHOT"
else
  RELEASE_FULL_VERSION="${CURRENT_MAJOR_VERSION}.${CURRENT_MINOR_VERSION}.${CURRENT_PATCH_VERSION}"
  DEVELOPMENT_FULL_VERSION="${CURRENT_MAJOR_VERSION}.${CURRENT_MINOR_VERSION}.${NEXT_PATCH_VERSION}-SNAPSHOT"
fi

echo "Version to be released: ${RELEASE_FULL_VERSION}. Next development version will be set to: ${DEVELOPMENT_FULL_VERSION}."

./mvnw --batch-mode release:prepare \
    -Darguments="-DskipTests -Ddocker.skip" \
    -DignoreSnapshots=true -DautoVersionSubmodules=true \
    -DreleaseVersion=${RELEASE_FULL_VERSION} \
    -DdevelopmentVersion=${DEVELOPMENT_FULL_VERSION}

# Once ready, replace with release:perform to publish to artifactory
./mvnw release:clean
