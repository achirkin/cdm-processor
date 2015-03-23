# cdm-processor
Image creation and parsing for the course "Creative Data Mining" at ETH Zurich.

The app works in two regimes:
1) Generate a .pdf with maps, annotated with QR code information.
Take a list of images as an input

2) Given a scanned (or photographed) images of pages, generated at step 1, extract images and save them as files with filenames constructed from QR code annotation.

# Running

To build a .jar package with all libraries run maven goal "clean jfx:jar":
mvn clean jfx:jar
The .jar file will be placed in folder /target/jfx/app/
(Warning: a lot of native libraries)

More help on building Java FX jars at maven-javafx plugin web page: http://zenjava.com/javafx/maven/


A list of third-party libraries used in the project and their licenses can be found in FIRD-PARTY.txt
For generating the list of licenses for third-party packages run:
mvn license:aggregate-add-third-party

More on license plugin: http://mojo.codehaus.org/license-maven-plugin/usage.html 