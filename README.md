# JDBC client for kdb+

> :warning: **Depends on Java interface**
>
> Compilation depends on version 1 of the Java interface
> [KxSystems/javakdb](https://github.com/KxSystems/javakdb/releases/tag/1.0).

> **Implementation**
>
> This is a pure Java native-protocol driver (type 4) JDBC driver. The implementation builds on the lower-level [javakdb API](https://github.com/KxSystems/javakdb), whic
h is somewhat simpler, and a good choice for application development when support for legacy code is not a consideration.

The JDBC driver implements only the minimal core of the JDBC feature set. 

There is no significant difference in performance between using the `!PreparedStatement`, `!CallableStatement` or `Statement` interfaces.

Q does not have the concept of transactions as expected by the JDBC API. 
That is, you cannot open a connection, explicitly begin a transaction, issue a series of separate queries within that transaction and finally roll back or commit the transaction. 
It will always behave as if `autoCommit` is set to true and the transaction isolation is set to `SERIALIZABLE`.
In practice, this means that any single query (or sequence of queries if executed in a single JDBC call) will be executed in isolation without noticing the effects of other queries, and modifications made to the database will always be permanent.

Operations must be prefixed by `"q)"` in order to be executed as q statements. 

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

Example1 shows executing inserts with types and general select

```mvn exec:java -pl jdbc-example -Dexec.mainClass="Example1" -q```

Example2 show running a q function and converting results to their appropriate types

```mvn exec:java -pl jdbc-example -Dexec.mainClass="Example2" -q```

## Connection pooling

If little work is being performed per interaction via the JDBC driver,
that is, few queries and each query is very quick to execute,
then there is a significant advantage to using connection pooling.

Using the [Apache Commons DBCP](https://commons.apache.org/proper/commons-dbcp/) component improves the performance of this case by about 70%.
DBCP avoids some complexity which can be introduced by other connection pool managers.
For example, it handles connections in the pool that have become invalid (say due to a database restart) by automatically reconnecting.
Furthermore it offers configuration options to check the status of connections in the connection pool using a variety of strategies.

Although it is not necessary to call the `close` method on `!ResultSet`, `Statement`, `!PreparedStatement` and `!CallableStatement` when using the q JDBC driver,
it is recommended with the DBCP component as it performs checks to ensure all resources are cleaned up, and has the ability to report resource leaks.
Explicitly closing the resources avoids a small runtime cost.

```java
#!java

import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDataSource;

...

Class.forName("jdbc");

// A ConnectionFactory is used by the to create Connections.
// This example uses the DriverManagerConnectionFactory, with a
// a connection string for a local q database listening on port 5001.
//
ConnectionFactory connectionFactory =
            new DriverManagerConnectionFactory("jdbc:q:localhost:5001",null);

// A PoolableConnectionFactory is used to wrap the Connections
// created by the ConnectionFactory with the classes that implement
// the pooling functionality.
//
PoolableConnectionFactory poolableConnectionFactory = new 
            PoolableConnectionFactory(connectionFactory, null);

// An ObjectPool serves as the pool of connections.
ObjectPool<PoolableConnection> connectionPool = new 
            GenericObjectPool<>(poolableConnectionFactory);

// Set the factory's pool property to the owning pool
poolableConnectionFactory.setPool(connectionPool);

// Now we can get a data source as before
PoolingDataSource<PoolableConnection> dataSource = new 
            PoolingDataSource<>(connectionPool);

// As we use/close connections, we can see the number
// of underlying instances in the pool
java.sql.Connection c1=dataSource.getConnection();
java.sql.Connection c2=dataSource.getConnection();
c2.close();

System.out.println("num active "+connectionPool.getNumActive());
System.out.println("num idle "+connectionPool.getNumIdle());
```
