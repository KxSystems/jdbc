# JDBC for kdb+

_JDBC client for kdb+_



**Documentation** is in :open_file_folder: [`docs`](docs)


## Building from source

### Prerequisites

-   Java 1.8+ is recommended

	Ensure your `JAVA_HOME` environment variable is set to the version of Java you have installed (or the one preferred if you have multiple versions).

-   [Apache Maven](https://maven.apache.org/)

	Run the following to check you have it setup and configured correctly.

	```bash
	mvn -version
	```
-	[javakdb interface](https://github.com/KxSystems/javakdb)


### Build

To build the library, run the following within the directory where the `pom.xml` file is located (from the downloaded source). 

```bash
mvn clean compile
```

To deploy the library to your machine’s local repository (e.g. for use by other Maven projects on your machine), run the following

```bash
mvn clean install
```

:globe_with_meridians:
[Apache Maven documentation](https://maven.apache.org/guides) 


## Code example

The following describes, each with an example, how to run from Maven. (Maven is not required to run the applications, but used here for convenience.)

The example remotely creates a table `t`, then queries kdb+ for its contents, and then displays its columns/data to the console.

Run `mvn clean install`.

The JDBC driver passes the q or SQL text to the server. 
For SQL support, take the `ps.k` file from the [ODBC3 zip file](https://code.kx.com/q/interfaces/q-server-for-odbc3/)
and ensure that is loaded into your kdb+ process. ps.k is the sql transpiler or enquire about SQL support with KX Insights. The example requires kdb+ to be listening on TCP port 5001

```mvn exec:java -pl jdbc-example -Dexec.mainClass="test"```
