# cdm-processor
Image creation and parsing for the course "Creative Data Mining" at ETH Zurich.

The app works in two regimes:

1) Generate a .pdf with maps, annotated with QR code information.
Take a list of images as an input

2) Given a scanned (or photographed) images of pages generated at step (1), extract images and save them as files with filenames constructed from QR code annotation.

## Building and running

First of all, you need
[`JDK`](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
and
[`maven`](https://maven.apache.org/).
If you are not familiar with Java or `maven` development, follow the installation instructions carefully.
As a result, you will have commands `java` and `mvn` available.

To build a .jar package with all libraries run maven goal from the project's directory:

    mvn clean jfx:jar

The .jar file will be placed in folder `target/jfx/app/`
(Warning: a lot of native libraries).
To send the compiled application to someone else (to computer with the same platform, i.e. mac, windows, linux)
copy folder "target/jfx/app/" (`.jar` file and lib folder inside).

More help on building Java FX jars at maven-javafx plugin web page: http://zenjava.com/javafx/maven/

To run a .jar application either click on the file,
or run following command from the project's directory (the latter is better for debugging, since it will show more errors):

    java -jar target/jfx/app/cdm-processor-jfx.jar

If you encounter a program crash or freeze, please report it here in "Issues" page with a program terminal output attached.

## Licenses

A list of third-party libraries used in the project and their licenses can be found in FIRD-PARTY.txt
For generating the list of licenses for third-party packages run:

    mvn license:aggregate-add-third-party

More on license plugin: http://mojo.codehaus.org/license-maven-plugin/usage.html 
