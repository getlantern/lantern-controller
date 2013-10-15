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

name = raw_input("Name of appengine app? (leave blank for 'lanternctrl') ").strip()
if len(name) == 0:
    name = "lanternctrl"
    print "Defaulting name to '%s'" % (name)
    
contents = re.sub(r'(?<=<application>)[^<]+(?=</application>)',
                name,
                contents,
                1,
                re.MULTILINE)
    
if raw_input("Shall I bump version? (y/N) ").lower().startswith('y'):
    contents = re.sub(r'(?<=<version>)\d+(?=</version>)',
                    (lambda s: str(int(s.group(0)) + 1)),
                    contents,
                    1,
                    re.MULTILINE)
    
    file(filename, 'w').write(contents)                    
    if name == "lanternctrl":
        assert call("git add src/main/webapp/WEB-INF/appengine-web.xml", shell=True) == 0, "Could not add new version"
        assert call("git commit -m 'Adding bumped version'", shell=True) == 0, "Could not commit new version"
        assert call("git push origin master", shell=True) == 0, "Could not push new version"

    print "Version bumped!"
else:
    print "OK, version left alone."
    file(filename, 'w').write(contents)

if raw_input("Should this be the default version? (y/N) ").lower().startswith('y'):
    setdefault=True
    print "OK, we'll set this to be the default version"
else:
    setdefault=False
    print "OK, not setting the default version."

print "Ready to deploy!"

assert call("mvn clean install", shell=True) == 0, "Could not run clean install"
assert call("mvn appengine:enhance -Dmaven.test.skip=true", shell=True) == 0, "Could not enhance"
assert call("mvn appengine:update -Dmaven.test.skip=true", shell=True) == 0, "Could not update"

if setdefault:
    assert call("mvn appengine:set_default_version", shell=True) == 0, "Could not set default version?"
    print "Default version set!"
