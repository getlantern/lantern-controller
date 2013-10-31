#!/usr/bin/env bash

mvn exec:java -Dexec.mainClass="org.lantern.RemoteApi" -Dexec.classpathScope="test"
