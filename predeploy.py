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
                os.path.join(here, 'src', 'main', 'resources', 'org', 'lantern', 'secrets'))

secret = base64.b64encode(os.urandom(64))
file(os.path.join(here, 'src', 'main', 'resources', 'csrf-secret.properties'),
     'w').write("secret=%s\n" % secret)

if raw_input("Shall I bump version? (y/N)").lower().startswith('y'):
    filename = os.path.join(here, 'src', 'main', 'webapp', 'WEB-INF', 'appengine-web.xml')
    contents = file(filename).read()
    bumped = re.sub(r'(?<=<version>)\d+(?=</version>)',
                    (lambda s: str(int(s.group(0)) + 1)),
                    contents,
                    1,
                    re.MULTILINE)
    file(filename, 'w').write(bumped)
else:
    print "OK, version left alone."

print "Ready to deploy!"
