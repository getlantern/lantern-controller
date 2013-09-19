#!/bin/bash

mvn -Dmaven.test.skip=true appengine:devserver_stop appengine:devserver_start