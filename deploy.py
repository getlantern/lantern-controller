#!/usr/bin/env python

import base64
import os.path
import re
import shutil
import sys
from subprocess import call

here = os.path.dirname(sys.argv[0])

shutil.copyfile(os.path.join(here,
                             '..',
                             'too-many-secrets',
                             'lantern-controller',
                             'org.lantern.secrets.properties'),
                os.path.join(here, 'src', 'main', 'resources', 'org', 'lantern', 'secrets'))

secret = base64.b64encode(os.urandom(64))
file(os.path.join(here, 'src', 'main', 'resources', 'csrf-secret.properties'),
     'w').write("secret=%s\n" % secret)

filename = os.path.join(here, 'src', 'main', 'webapp', 'WEB-INF', 'appengine-web.xml')
contents = file(filename).read()

def get_arg_or_ask(index, prompt):
    if len(sys.argv) > index:
        return sys.argv[index]
    else:
        return raw_input(prompt)

while True:
    name = get_arg_or_ask(1, "Name of appengine app? ").strip()
    if name:
        break
    else:
        print "Name can't be left blank!"
        print "Enter lanternctrl1-2 if you want to deploy to the production",
        print "controller."

contents = re.sub(r'(?<=<application>)[^<]+(?=</application>)',
                  name,
                  contents,
                  1,
                  re.MULTILINE)

bump = get_arg_or_ask(2, "Shall I bump version? (y/N) ").lower().startswith('y')
if bump:
    contents = re.sub(r'(?<=<version>)\d+(?=</version>)',
                    (lambda s: str(int(s.group(0)) + 1)),
                    contents,
                    1,
                    re.MULTILINE)
    print "Version bumped!"
else:
    print "OK, version left alone."

file(filename, 'w').write(contents)

if bump and name == "lanternctrl1-2":
    assert call("git add src/main/webapp/WEB-INF/appengine-web.xml", shell=True) == 0, "Could not add new version"
    assert call("git commit -m 'Adding bumped version'", shell=True) == 0, "Could not commit new version"
    assert call("git push origin master", shell=True) == 0, "Could not push new version"

setdefault = (get_arg_or_ask(3, "Should this be the default version? (y/N) ")
              .lower().startswith('y'))

if setdefault:
    print "OK, we'll set this to be the default version"
else:
    print "OK, not setting the default version."

print "Deploying..."

assert call("mvn clean install", shell=True) == 0, "Could not run clean install"
assert call("mvn appengine:enhance -Dmaven.test.skip=true", shell=True) == 0, "Could not enhance"
assert call("mvn appengine:update -Dmaven.test.skip=true", shell=True) == 0, "Could not update"

if setdefault:
    assert call("mvn appengine:set_default_version", shell=True) == 0, "Could not set default version?"
    print "Default version set!"
