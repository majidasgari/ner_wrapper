NER Wrapper
=====

A Java wrapper for running Majid Asgari's NER Engine on Windows and Linux.

+ Determine Person, Organization and Location names

# Installation and Using

To make a single jar file run this codes:

```bash
mvn clean compile assembly:single
```
For using this project as library in maven just use:
```bash
mvn clean install
```

To run and see the help:
```bash
java -jar new-wrapper-jar-with-dependencies.jar
```

For example to make a file containing output:
```bash
java -jar jhazm-jar-with-dependencies.jar input.txt
```
Good Luck!
