#!/usr/bin/env python2.7
# -*- coding: utf-8 -*-

from codecs import open
from collections import namedtuple
from jinja2 import Environment, FileSystemLoader
from json import load
from os.path import join
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

# get these from some standard dataset
LANG_EN_US = Lang('en_US', 'English', 'ltr')
#LANG_ZH_CN = Lang('zh_CN', '中文', 'ltr')
#LANG_AR_AR = Lang('ar_AR', 'العربية', 'rtl')
#LANG_FA_IR = Lang('fa_IR', 'پارسی', 'rtl')
LANG_DEFAULT = LANG_EN_US

LANGS = [
    LANG_EN_US,
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

TMPL_FILENAMES = [
    'invite-notification.tmpl',
    ]
templates = [env.get_template(i) for i in TMPL_FILENAMES]

rendered = [i.render(
    COMPILED_CSS=COMPILED_CSS,
    LANGS=LANGS,
    ) for i in templates]

transformed = [transform(i) for i in rendered]

for (filename, content) in zip(TMPL_FILENAMES, transformed):
    opath = join(BASE_DIR, filename.replace('.tmpl', '.html'))
    with open(opath, mode='w', encoding='utf-8') as fp:
        print('* Writing "%s"...' % opath)
        fp.write(content)
