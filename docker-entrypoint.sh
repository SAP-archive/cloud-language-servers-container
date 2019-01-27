#!/bin/sh

if [ "$DEBUG" = true ]; then
    if [ "$DEBUG_SUSPEND" = true ]; then
        export JPDA_SUSPEND=y
    fi
    JPDA_ADDRESS=8000 JPDA_TRANSPORT=dt_socket catalina.sh jpda run
else
    catalina.sh run
fi