#!/usr/bin/env bash

export ROOT=$(dirname $0)
export ROOT=$(cd $ROOT/../; pwd)

DST_DIR=$ROOT/src/test/resources/spec-official

set -ex
rm -rf $DST_DIR
mkdir $DST_DIR
pushd $DST_DIR



for file in $ROOT/spec/test/core/*.wast ; do
    wast2json ${file} --disable-multi-value --disable-sign-extension --disable-saturating-float-to-int || true
done