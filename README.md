<h1 align="center">
  <a href="https://github.com/orangebeard-io/qf-test-testrunlistener">
    <img src="https://raw.githubusercontent.com/orangebeard-io/qf-test-testrunlistener/master/.github/logo_qf.svg" alt="Orangebeard.io QF-Test TestRunListener" height="200">
  </a>
  <br>Orangebeard.io QF-Test TestRunListener<br>
</h1>

<h4 align="center">A test output listener for QF-Test.</h4>

<p align="center">
  <!-- <a href="https://github.com/orangebeard-io/qf-test-testrunlistener/actions">
    <img src="https://img.shields.io/github/workflow/status/orangebeard-io/qf-test-testrunlistener/release?style=flat-square"
      alt="Build Status" /> -->
  </a>
  <a href="https://github.com/orangebeard-io/qf-test-testrunlistener/blob/master/LICENSE.txt">
    <img src="https://img.shields.io/github/license/orangebeard-io/qf-test-testrunlistener?style=flat-square"
      alt="License" />
  </a>
</p>

<div align="center">
  <h4>
    <a href="https://orangebeard.io">Orangebeard</a> |
    <a href="#build-instructions">Build Instructions</a> |
    <a href="#installation">Installation</a> |
    <a href="#configuration">Configuration</a>
  </h4>
</div>

## Build Instructions

We use maven as our build tool. In order to easily build using maven, make sure that all dependencies are in your local repository.
For dependencies that are in Maven central, this is done automatically. To be able to use the dependencies from QF-Test, we need
to manually add them.

To install local jars, execute the following commands (the commands assume the jars are in ./lib, you can change this to your `qflibs`  directory in th qf test installation dir):

```shell
mvn install:install-file -Dfile=.\lib\qftest.jar -DgroupId=de.qfs -DartifactId=qftest -Dversion=5.0.3 -Dpackaging=jar -DgeneratePom=true
mvn install:install-file -Dfile=.\lib\qflib.jar -DgroupId=de.qfs -DartifactId=qflib -Dversion=5.0.3 -Dpackaging=jar -DgeneratePom=true
```

Run `maven clean install` to build the listener. A jar-with-dependencies will be built to the /target folder. This jar contains the listener and the necessary client binaries.

## Installation

1. Place the jar-with dependencies in the plugins/qftest folder of your QF-Test's installation directory.
2. Reference and register the listener in a groovy script step at the beginning of your test project:

```groovy
import io.orangebeard.listener.QFTestOrangebeardRunListener

listener = new QFTestOrangebeardRunListener()
rc.addTestRunListener(listener)
```

## Configuration
The configuration of the listener is done using properties. These can come from a properties file, system properties, or environment variables.
The configuration properties are:

```properties
orangebeard.endpoint=<ORANGEBEARD-ENDPOINT>
orangebeard.accessToken=<XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX>
orangebeard.project=<PROJECT_NAME>
orangebeard.testset=<TESTSET_NAME>

# optional
orangebeard.description=<DESCRIPTION>
orangebeard.attributes=key:value; value;
```

**Be warned**: the access token is a *credential token*. We advise you to store this token in a credential store, and pass it to the listener through system- or environment properties. See below how to do that!

### System- or Environment properties
QF-Test can be configured with system properties in various ways:
1. Start qf-test using the -J argument: `qftest.exe -J-DsystemPropertyName=systemPropertyValue`
2. Configure system properties in a script step: `System.setProperty("orangebeard.endpoint", "https://mytenant.orangebeard.app")` 
3. Set the necessary properties as environment variables. If necessary, you can replce the dot in the property names with an underscore when using ENV variables.