# jdbc for kdb+
JDBC client for kdb+

## Introduction

JDBC driver for kdb+

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

## Code Example

The following describes each with an example of how to run from Maven (note: Maven is not required to run the applications, but used here for convenience).

The example remotely creates a table 't', then queries kdb+ for its contents, which it then displays its columns/data to the console

`mvn clean install` should be performed prior to running.

The jdbc driver passes the q or sql text to the server. For SQL support you should take the ps.k file from the odbc3 zip file [here](https://code.kx.com/v2/interfaces/q-server-for-odbc3/)
and ensure that is loaded into your kdb+ process. ps.k is the sql transpiler or enquire about SQL support with KX Insights. The example requires kdb+ to be listening on TCP port 5001

```mvn exec:java -pl jdbc-example -Dexec.mainClass="test"```
