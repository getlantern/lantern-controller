#!/usr/bin/env python2.7
# -*- coding: utf-8 -*-

from codecs import open
from collections import namedtuple
from jinja2 import Environment, FileSystemLoader
from json import load
from os.path import join
import os
from premailer import transform
from subprocess import call

BASE_DIR = 'src/main/resources/org/lantern/email'

cmd = 'compass compile -c compass.rb'
print('* Running "%s"...' % cmd)
result = call(cmd.split())
assert result == 0
CSS_PATH = join(BASE_DIR, 'style.css')
with open(CSS_PATH, encoding='utf-8') as fp:
    COMPILED_CSS = fp.read()

Lang = namedtuple('lang', 'code name dir')

# http://omniglot.com/language/names.htm
# get these from some standard dataset
#LANG_AR_AR = Lang('ar_AR', u'العربية', 'rtl')
LANG_CA = Lang('ca', u'català', 'ltr')
LANG_EN_US = Lang('en_US', u'English', 'ltr')
LANG_ES = Lang('es', u'español', 'ltr')
LANG_PT_BR = Lang('pt_BR', u'português', 'ltr')
LANG_FA_IR = Lang('fa_IR', u'فارسی', 'rtl')
LANG_UK_UA = Lang('uk_UA', u'Українська', 'ltr')
LANG_VI = Lang('vi', u'Tiếng Việt', 'ltr')
LANG_ZH_CN = Lang('zh_CN', u'中文', 'ltr')
LANG_DEFAULT = LANG_EN_US

LANGS = [
    LANG_EN_US,
    LANG_ZH_CN,
    LANG_FA_IR,
    LANG_ES,
    LANG_PT_BR,
    LANG_UK_UA,
    LANG_VI,
    LANG_CA,
    ]

# must be kept in sync with .tx/config
LOCALE_DIR = 'locale'
LOCALE_EXT = 'json'
TRANSLATIONS = dict()
for lang in LANGS:
    lpath = join(LOCALE_DIR, lang.code + '.' + LOCALE_EXT)
    with open(lpath, encoding='utf-8') as fp:
        print('* Loading "%s"...' % lpath)
        TRANSLATIONS[lang.code] = load(fp)

env = Environment(loader=FileSystemLoader(BASE_DIR))
env.filters['trans'] = lambda key, lang: TRANSLATIONS[lang.code][key]

tmpl_filenames = [filename
                  for filename in os.listdir(BASE_DIR)
                  if filename.endswith('.tmpl')]

templates = [env.get_template(i) for i in tmpl_filenames]

rendered = [i.render(
    COMPILED_CSS=COMPILED_CSS,
    LANGS=LANGS,
    ) for i in templates]

transformed = [transform(i).replace('%7C', '|') for i in rendered]
# `transform` helpfully escapes characters like '|' inside href attributes for
# us, but this breaks Mailchimp merge variables inside hrefs
# e.g. <a href="mailto:*|INVITER_EMAIL|*">...</a>

for (filename, content) in zip(tmpl_filenames, transformed):
    opath = join(BASE_DIR, filename.replace('.tmpl', '.html'))
    with open(opath, mode='w', encoding='utf-8') as fp:
        print('* Writing "%s"...' % opath)
        fp.write(content)

print('Done.')
