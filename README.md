This is the master controller project for globally controlling the Lantern network.

To bring this project into Eclipse, just do the following:

1. git clone git@github.com:getlantern/lantern-controller.git
2. cd lantern-controller
4. File->Import...->Existing Projects into Workspace
5. Choose the lantern-controller directory 

To deploy a new version, just run:

1. ./deploy.bash

You'll be prompted for whether or not you want to increment the version, and
if you do then you'll also want to set the default on the server.

You'll also need to fill in src/org.lantern.secrets.properties with
the correct data; accessKey must match the one used on getlantern.org

Also create a file from war/WEB-INF/classes/csrf-secret.properties with
secret=[some random string]
