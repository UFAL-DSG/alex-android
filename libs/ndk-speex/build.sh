#!/bin/bash
if ! which android; then
    echo "Please put 'android' tool on your path.
    exit 1
fi
if which ndk-build; then
else
    echo "Please put 'ndk-build' tool on your path.
    exit 1
fi
android update lib-project --target android-15 --path speexjni

(cd speexjni; ndk-build)
