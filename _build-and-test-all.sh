#! /bin/bash

export TERM=dumb

set -e

GRADLE_OPTS=""

if [ "$1" = "--clean" ] ; then
  GRADLE_OPTS="clean"
  shift
fi

function testJdbc() {
  ${docker}Up

  ./gradlew $* :eventuate-common-micronaut-data-jdbc:cleanTest :eventuate-common-micronaut-data-jdbc:test \
  :eventuate-common-spring-jdbc:cleanTest :eventuate-common-spring-jdbc:test

  ${docker}Down
}

docker="./gradlew ${DATABASE?}Compose"
${docker}Down

./gradlew ${GRADLE_OPTS} testClasses



echo ""
echo "TESTING REGULAR DATABASE"
echo ""

${docker}UP
./gradlew $* cleanTest build
${docker}Down

echo ""
echo "TESTING DATABASE WITH DBID WITH APPLICATION ID GENERATION"
echo ""

export USE_DB_ID=true
export DB_ID_USED=true
testJdbc

echo ""
echo "TESTING DATABASE WITH DBID WITH DATABASE ID GENERATION"
echo ""

export EVENTUATE_OUTBOX_ID=1
testJdbc

echo ""
echo "TESTING DBID MIGRATION"
echo ""

unset USE_DB_ID
${docker}Up
./migration/db-id/migration.sh
testJdbc

echo ""
echo "TESTING DATABASE WITH JSON SUPPORT"
echo ""

unset DB_ID_USED
unset EVENTUATE_OUTBOX_ID
export USE_JSON_PAYLOAD_AND_HEADERS=true
testJdbc

