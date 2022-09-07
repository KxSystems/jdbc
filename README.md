# jdbc for kdb+
JDBC client for kdb+

## Introduction

JDBC driver for KDB+

## Documentation

Documentation outlining the functionality available for this interface can be found [here](https://code.kx.com/q/interfaces/jdbc-client-for-kdb/).

## Building from Source

Java 1.8 (and above) is recommended. Please ensure that your `JAVA_HOME` environment variable is set to the version of Java you have installed (or the one preferred if you have multiple versions).

You will also need [Apache Maven](https://maven.apache.org/) installed. Run the following the check you have it setup and configured correctly

`mvn -version`

In order to build the library, run the following within the directory where the pom.xml file is located (from the downloaded source). NOTE: the java kdb interface should already be installed on your system prior to building, and can be found [here](https://github.com/KxSystems/javakdb/releases/tag/1.0)

`mvn clean compile`

If you wish to deploy the library to your machines local repository, in order to be used by other maven projects on your machine, run the following

`mvn clean install`

Please refer to the [Apache Maven documentation](https://maven.apache.org/guides/index.html) for further details
