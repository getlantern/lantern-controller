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
import sys

BASE_DIR = 'src/main/resources/org/lantern/email'

if len(sys.argv) == 1 or sys.argv[1] != "nosass":
    # Compile SASS
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
LANG_CS = Lang('cs', u'čeština', 'ltr')
LANG_DE = Lang('de', u'Deutsch', 'ltr')
LANG_EN_US = Lang('en_US', u'English', 'ltr')
LANG_ES = Lang('es', u'español', 'ltr')
LANG_FA_IR = Lang('fa_IR', u'فارسی', 'rtl')
LANG_FR_CA = Lang('fr_CA', u'français\N{NO-BREAK SPACE}(Canada)', 'ltr')
LANG_FR_FR = Lang('fr_FR', u'français\N{NO-BREAK SPACE}(France)', 'ltr')
LANG_HE_IL = Lang('he_IL', u'עברית', 'rtl')
LANG_HR = Lang('hr', u'Hrvatski', 'ltr')
LANG_IT = Lang('it', u'italiano', 'ltr')
LANG_JA = Lang('ja', u'日本語', 'ltr')
LANG_KO = Lang('ko', u'한국말', 'ltr')
LANG_NB = Lang('nb', u'Norsk bokmål', 'ltr')
LANG_NL = Lang('nl', u'Nederlands', 'ltr')
LANG_PT_BR = Lang('pt_BR', u'português', 'ltr')
LANG_RU_RU = Lang('ru_RU', u'Русский\N{NO-BREAK SPACE}язык', 'ltr')
LANG_SK = Lang('sk', u'slovenčina', 'ltr')
LANG_SV = Lang('sv', u'Svenska', 'ltr')
LANG_UG = Lang('ug', u'ئۇيغۇر', 'rtl')
LANG_UK_UA = Lang('uk_UA', u'Українська', 'ltr')
LANG_VI = Lang('vi', u'Tiếng Việt', 'ltr')
LANG_ZH_CN = Lang('zh_CN', u'中文', 'ltr')
LANG_DEFAULT = LANG_EN_US

LANGS_DEFAULT = [
    LANG_EN_US,
    LANG_ZH_CN,
    LANG_FA_IR,
    LANG_ES,
    LANG_DE,
    LANG_PT_BR,
    LANG_FR_FR,
    LANG_FR_CA,
    LANG_IT,
    LANG_UK_UA,
    LANG_VI,
    LANG_JA,
    LANG_KO,
    LANG_RU_RU,
    LANG_HE_IL,
    LANG_CA,
    LANG_NL,
    LANG_SV,
    LANG_NB,
    LANG_CS,
    LANG_SK,
    LANG_HR,
    LANG_UG,
    ]

# must be kept in sync with .tx/config
LOCALE_DIR = 'locale'
LOCALE_EXT = 'json'
TRANSLATIONS = dict()
for lang in LANGS_DEFAULT:
    lpath = join(LOCALE_DIR, lang.code + '.' + LOCALE_EXT)
    with open(lpath, encoding='utf-8') as fp:
        print('* Loading "%s"...' % lpath)
        TRANSLATIONS[lang.code] = load(fp)

# allow overriding LANGS_DEFAULT on a per-template basis
LANGS_BY_TMPL = {
    'new-trust-network-invite.tmpl': [  # XXX pull remaining translations when available
        LANG_EN_US,
        LANG_ZH_CN,
        LANG_FA_IR,
        LANG_ES,
        LANG_FR_CA,
        LANG_FR_FR,
        LANG_PT_BR,
        LANG_CA,
        LANG_DE,
        LANG_NB,
        LANG_CS,
        LANG_SK,
        LANG_UK_UA,
        ]
    }

env = Environment(loader=FileSystemLoader(BASE_DIR))
env.filters['trans'] = lambda key, lang: TRANSLATIONS[lang.code].get(key, TRANSLATIONS["en_US"][key])

tmpl_filenames = [filename
                  for filename in os.listdir(BASE_DIR)
                  if filename.endswith('.tmpl')]

templates = [env.get_template(i) for i in tmpl_filenames]

rendered = [i.render(
    COMPILED_CSS=COMPILED_CSS,
    LANGS=LANGS_BY_TMPL.get(i.name) or LANGS_DEFAULT,
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
