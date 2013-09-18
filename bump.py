#!/usr/bin/env python

import base64
import os.path
import re
import shutil
import sys

here = os.path.dirname(sys.argv[0])


filename = os.path.join(here, 'src', 'main', 'webapp', 'WEB-INF', 'appengine-web.xml')
contents = file(filename).read()
bumped = re.sub(r'(?<=<version>)\d+(?=</version>)',
                    (lambda s: str(int(s.group(0)) + 1)),
                    contents,
                    1,
                    re.MULTILINE)
file(filename, 'w').write(bumped)
