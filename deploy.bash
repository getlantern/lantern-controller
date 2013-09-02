#!/usr/bin/env bash

./predeploy.py
mvn clean install
mvn appengine:enhance
mvn appengine:update
