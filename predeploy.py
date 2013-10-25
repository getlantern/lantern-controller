#!/usr/bin/env python

import base64
import os.path
import re
import shutil
import string
import sys
from subprocess import call

here = os.path.dirname(sys.argv[0])
# Alphabet from which csrf secret will be generated.
# We use only garden variety characters, so our secret won't need be quoted or
# otherwise mangled.
# (See http://stackoverflow.com/a/7233959 )
secret_alphabet = string.ascii_letters + string.digits
secret_length = 72

shutil.copyfile(os.path.join(here,
                             '..',
                             'too-many-secrets',
                             'lantern-controller',
                             'org.lantern.secrets.properties'),
                os.path.join(here, 'src', 'main', 'resources', 'org', 'lantern', 'secrets'))

def get_random_char():
    # Chars at the end of the alphabet have a slightly lower probability of
    # being picked, but we're not using this to encode a stream, so that's OK.
    return secret_alphabet[ord(os.urandom(1)) % len(secret_alphabet)]

secret = ''.join(get_random_char() for _ in xrange(secret_length))

file(os.path.join(here, 'src', 'main', 'resources', 'csrf-secret.properties'),
     'w').write("secret=%s\n" % secret)

filename = os.path.join(here, 'src', 'main', 'webapp', 'WEB-INF', 'appengine-web.xml')
contents = file(filename).read()

if len(sys.argv) > 1:
    name = sys.argv[1]
else:
    name = raw_input("Name of appengine app? (leave blank for 'lanternctrl') ").strip()
    if len(name) == 0:
        name = "lanternctrl"
        print "Defaulting name to '%s'" % (name)

contents = re.sub(r'(?<=<application>)[^<]+(?=</application>)',
                name,
                contents,
                1,
                re.MULTILINE)

if len(sys.argv) > 2:
    bump_str = sys.argv[2]
else:
    bump_str = raw_input("Shall I bump version? (y/N) ")
bump = bump_str.lower().startswith('y')

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

if bump and name == "lanternctrl":
    assert call("git add src/main/webapp/WEB-INF/appengine-web.xml", shell=True) == 0, "Could not add new version"
    assert call("git commit -m 'Adding bumped version'", shell=True) == 0, "Could not commit new version"
    assert call("git push origin master", shell=True) == 0, "Could not push new version"

print "Ready to deploy!"
