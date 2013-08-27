#!/usr/bin/python

import os
import json

metatemplate_directory = "resources/metatemplates/"
locale_directory = "resources/locale/"
output_directory = "src/org/lantern"

language_json_files = os.listdir(locale_directory)
languages = {}
for filename in language_json_files:
    language = filename.replace(".json","")
    path = os.path.join(locale_directory, filename)
    f = open(path, "r")
    data = f.read()
    f.close()
    languages[language] = json.loads(data)

files = os.listdir(metatemplate_directory)

for filename in files:
    output_filename = filename.replace(".html.tmpl", ".json")
    path = os.path.join(metatemplate_directory, filename)
    f = open(path, "r")
    data = f.read()
    f.close()
    language_data = {}
    for language_name, language in languages.items():
        language_data[language_name] = data % language

    output_path = os.path.join(output_directory, output_filename)
    f = open(output_path, "wb")
    f.write(json.dumps(language_data))
    f.close()
