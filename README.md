This is the master controller project for globally controlling the Lantern network.

To bring this project into Eclipse, just do the following:

1. git clone git@github.com:getlantern/lantern-controller.git
2. cd lantern-controller
3. mvn eclipse:eclipse 
4. File->Import...->Existing Projects into Workspace
5. Choose the lantern-controller directory 
6. Define M2_REPO classpath variable in Eclipse to be the maven repository directory locally. On Unix-based systems that will be ~/.m2/repository. To set classpath variables, open Eclipse preferences/options and enter "classpath" or "classpath variables" into the search box.
