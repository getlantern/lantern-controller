This is the master controller project for globally controlling the Lantern network.

To bring this project into Eclipse, just do the following:

1. git clone git@github.com:getlantern/lantern-controller.git
1. Open Eclipse
1. File->Import...->Existing Projects into Workspace
1. Choose the lantern-controller directory 

Lantern-controller requires some secrets to be put in place before it can run.
See (or just run) `./predeploy.py` (which requires the too-many-secrets repo to
be cloned alongside lantern-controller) to get secrets in place.

To deploy a new version, just run `./deploy.bash`. You'll be
prompted for whether you want to increment the version, and if you do
then you may also want to update the default serving version in the app engine
console.
