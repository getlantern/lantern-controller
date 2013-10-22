# Lantern Controller

This is the software for globally controlling the Lantern network.


## Development

To run the development server from the command-line, you will need Maven 3.1.0
or higher.  Then, just run `mvn appengine:devserver`.

To bring this project and its submodules into Eclipse, just do the following:

1. git clone --recursive git@github.com:getlantern/lantern-controller.git
   
   If you have already checked out lantern-controller but did not pass
   '--recursive', you can clone its submodules with
   `git submodule update --init`.
   
1. Open Eclipse
1. File->Import...->Existing Projects into Workspace
1. Choose the lantern-controller directory 


## Deployment

Lantern-controller requires some secrets to be put in place before it can run.
See (or just run) `./predeploy.py` (which requires the too-many-secrets repo to
be cloned alongside lantern-controller) to get secrets in place.

To deploy a new version, just run `./deploy.bash`. You'll be
prompted for whether you want to increment the version, and if you do
then you may also want to update the default serving version in the app engine
console.


## Admin Pages

Lantern Controller makes the following admin pages available for various
management tasks:

- `https://<appid>.appspot.com/admin/index.jsp` - misc. global state
- `https://<appid>.appspot.com/admin/pendingInvites.jsp` - approve
  pending invites


## Setting up a Test Lantern Controller

 1. Set up a appengine instance
 2. In `Dao.java`, uncomment the body of the method `createInitialUser`.
 3. Deploy your app using `deploy.py` (make sure to specify the right instance
    name).
 4. Open `org.lantern.RemoteApi.java` and edit it to call
    dao.createInitialUser() - this will set up a seed user with which you can
    run Lantern
 5. Set up a remoteapi.properties in your root folder with the following:
 
```
username=<your google username>
password=<your google password (Strongly recommend using app-specific password)>
controller=<name of your controller>
```

 6. `mvn exec:java -Dexec.mainClass="org.lantern.RemoteApi" -Dexec.classpathScope="test"`
 7. Revert `Dao.java`
 8. Revert `RemoteApi.java`
 9. Open /admin/index.jsp of your app in a browser
 10. Click "Pause Invites"
  

## i18n

Translated strings are fetched from json files in the "locale" directory. To
add or change a translated string, update the corresponding mapping in the
source file "locale/en_US.json", and add or update any references to it as
needed.

### Transifex

All translatable content for Lantern has been uploaded to [the Lantern
Transifex project](https://www.transifex.com/projects/p/lantern/) to help
manage translations. Translatable strings from this code have been uploaded to
the [controller resource](https://www.transifex.com/projects/p/lantern/resource/email/)
therein. Transifex has been set up to automatically pull updates to that
resource from [its GitHub
url](https://raw.github.com/getlantern/lantern-controller/master/locale/en_US.json)
(see
http://support.transifex.com/customer/portal/articles/1166968-updating-your-source-files-automatically
for more information).

After translators add translations of these strings to the Transifex project,
the [Transifex
client](http://support.transifex.com/customer/portal/articles/960804-overview)
can be used to pull them. See
http://support.transifex.com/customer/portal/articles/996157-getting-translations
for more.


## Generating email templates

The emails the controller sends out are assembled from a template containing
the html (e.g.
[src/main/resources/org/lantern/email/invite-notification.tmpl](https://github.com/getlantern/lantern-controller/tree/master/src/main/resources/org/lantern/email/invite-notification.tmpl)),
a sass stylesheet containing the styles (
[src/main/resources/org/lantern/email/style.sass](https://github.com/getlantern/lantern-controller/tree/master/src/main/resources/org/lantern/email/style.sass)),
and the json files containing the translated strings (e.g.
[locale/en_US.json](https://github.com/getlantern/lantern-controller/tree/master/locale/en_US.json)).
These sources are combined into a final html file suitable to be emailed, i.e. with
all translations and css rules inlined so that they will be displayed properly
by email clients (e.g.
[src/main/resources/org/lantern/email/invite-notification.html](https://github.com/getlantern/lantern-controller/tree/master/src/main/resources/org/lantern/email/invite-notification.html)). This final html is then passed to
[Mandrill](https://mandrillapp.com) for delivery, along with any
[merge variables](http://help.mandrill.com/entries/21678522-How-do-I-use-merge-tags-to-add-dynamic-content-)
to interpolate into it.

Any time the source content of these emails changes (e.g. a language tweak
to a string in the json, a style tweak to the sass, etc.), run `./generate_emails.py`
to regenerate the final html. Run the script from a Python 2.7 environment with
the following packages installed:

  - [Jinja2 2.7.1](https://pypi.python.org/pypi/Jinja2/2.7.1)
  - [premailer 1.2.3](https://pypi.python.org/pypi/premailer/1.2.3)

The recommended way of doing this is to create a
[virtualenv](https://pypi.python.org/pypi/virtualenv), cd into it and source
its activate script, and then run `pip install` commands for the required
packages.

Also make sure you have [compass](http://compass-style.org/) 0.12.2 installed,
which is required to compile the sass. You can run
`gem install compass --version '= 0.12.2'` to install it (sudo as necessary).
If the sass hasn't changed since the last time it was compiled, you can also
just comment out the code in the beginning of generate_emails.py which makes
the "compass compile" call.

Any time a **new** translation file is pulled from Transifex (see the
[Transifex](#transifex) section above), a corresponding `Lang` instance should
be added to the `LANGS` list in generate_emails.py. For instance, if "tx pull"
pulls a new file "locale/es_ES.json", add an entry to `LANGS` like
`Lang('es_ES', 'Español', 'ltr')`. The next time you run
`./generate_emails.py`, a new section will be added to the generated emails for
the Spanish translation.
