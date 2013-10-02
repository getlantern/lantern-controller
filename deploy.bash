#!/usr/bin/env bash

function die() {
  echo $*
  exit 1
}

./predeploy.py $* || die "Could not predeploy?"
mvn clean install || die "Could not run clean install"
mvn appengine:enhance || die "Could not enhance"
mvn appengine:update || die "Could not update"
