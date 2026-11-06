#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
set -a
. ./.env
set +a
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
cd backend
exec ./mvnw "$@"
