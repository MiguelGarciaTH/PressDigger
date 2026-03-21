#!/bin/bash

set -eu

cd $(dirname $0)

export JAVA_TOOL_OPTIONS

exec java \
     -Dserver.port="$SERVER_PORT" \
     -Dmanagement.server.port="$MANAGEMENT_SERVER_PORT" \
     ${SERVER_BASE_PATH:+-Dserver.servlet.context-path="$SERVER_BASE_PATH"} \
     ${IMAGES_BASE_PATH:+-Dscribe-ref.images.base-path="$IMAGES_BASE_PATH"} \
     -Dspring.datasource.url="$DB_URL" \
     -Dspring.datasource.username="$DB_USER" \
     -Dspring.datasource.password="$DB_PASSWORD" \
     ${LOGGING_CONFIG:+-Dlogging.config="$LOGGING_CONFIG"} \
     -jar ./scribe-news-rest-*.jar
