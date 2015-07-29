#!/bin/bash
# Go to the directory where this script is located.
cd "$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

ALEX_PATH="/xdisk/devel/proj/alex"


protoc -I${ALEX_PATH} ${ALEX_PATH}/alex/components/hub/wsio_messages.proto --java_out app/src/main/java