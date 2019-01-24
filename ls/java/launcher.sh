#!/bin/bash

if [[ -n "$2" ]]; then
    if [[ -n "$3" ]]; then
        MODULE_WS="$2/$3"
    else
        MODULE_WS="$2"
    fi
else
    # shouldn't happen as websocket is opened with some project path (mta or flat)
    MODULE_WS="___fallback"
fi

UUID_FILE_PATH="$WORKSPACE_ROOT/$MODULE_WS/.jdt.cache.clean"

if [[ -s ${UUID_FILE_PATH} ]]; then
    UUID=`cat ${UUID_FILE_PATH}`
else
    # compute new uuid as this is first time or file was cleaned by client code in order to clean jdt cache
    UUID=$(cat /proc/sys/kernel/random/uuid)
    echo ${UUID} > ${UUID_FILE_PATH}
fi

WS_DATA_PATH="$WORKSPACE_ROOT/$MODULE_WS/$UUID"

mkdir -p ${WS_DATA_PATH}

exec java -Declipse.application=org.eclipse.jdt.ls.core.id1 \
    -Dosgi.bundles.defaultStartLevel=4 -Declipse.product=org.eclipse.jdt.ls.core.product \
    -Dlog.protocol=true -Dlog.level=ALL -Duser.home=$HOME -noverify -javaagent:./lombok.jar \
    -Xbootclasspath/a:./lombok.jar -Xmx250M -XX:+UseG1GC -XX:+UseStringDeduplication \
    -jar ./plugins/org.eclipse.equinox.launcher_1.5.200.v20180922-1751.jar \
    -configuration ./config_linux -data ${WS_DATA_PATH}
