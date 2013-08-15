#!/usr/bin/env python

import base64
import os.path
import re
import shutil
import sys

here = os.path.dirname(sys.argv[0])

shutil.copyfile(os.path.join(here,
                             '..',
                             'too-many-secrets',
                             'lantern-controller',
                             'org.lantern.secrets.properties'),
                os.path.join(here, 'src', 'org', 'lantern', 'secrets'))

secret = base64.b64encode(os.urandom(64))
file(os.path.join(here, 'war', 'WEB-INF', 'classes', 'csrf-secret.properties'),
     'w').write("secret=%s\n" % secret)

if raw_input("Shall I bump version? (y/N)") in 'yY':
    filename = os.path.join(here, 'war', 'WEB-INF', 'appengine-web.xml')
    contents = file(filename).read()
    bumped = re.sub(r'(?<=<version>)\d+(?=</version>)',
                    (lambda s: str(int(s.group(0)) + 1)),
                    contents,
                    1,
                    re.MULTILINE)
    file(filename, 'w').write(bumped)
    print "OK, version bumped."
    print
    print "Note that this may cause the csrf-secret.properties file to be deleted"
    print "on deploy.  You can check this in the logs of the new version, before"
    print "you make it current.  If you see errors there, rerun this script"
    print "without bumping and all should be fine."
else:
    print "OK, version left alone."

print "Ready to deploy!"
