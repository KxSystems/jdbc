//2019.12.16 getTimestamp() was throwing an exception when given a java.util.date
//           setMaxRows/setFetchSize was not interpreting 0 to indicate unlimited.
//2019.11.10 Cleaned up logic from last commit.
//2019.11.08 setFetchSize/setMaxRows are now respected.
//           Queries still use sync request, but results>fetchSize are streamed back as async, [async...], response.
//           also updated the DatabaseMetaData to work for kdb+3.x, dropping support for kdb+2.x.
//           confirmed working for DBVisualizer v10.0.24.
//2014.03.25 allow calling connection close() even if already closed, use jdk1.7 api
//           jdk1.7 specific parts are sections after //1.7
//2012.11.26 getRow(), use jdk6 api, return char[] as String to support Aqua Data Studio for lists of char vectors.
//           java.sql.timestamp now maps to kdb+timestamp; use latest http://kx.com/q/s.k
//           jdk1.6 specific parts are sections after //4
//           For compiling for earlier jdks, remove those sections
//2007.04.20 c.java sql.date/time/timestamp
package com.kx;
import kx.*;
import java.io.*;
import java.math.*;
import java.sql.*;
import java.net.URL;
import java.util.Calendar;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.concurrent.Executor;

/**
 * JDBC driver for kdb+
 */
public class jdbc implements Driver{

private static final String CLOSED="Closed";
private static final int MAJOR_VERSION=2;
private static final int MINOR_VERSION=0;
static void O(String s){System.out.println(s);}
public int getMajorVersion(){return MAJOR_VERSION;}
public int getMinorVersion(){return MINOR_VERSION;}
public boolean jdbcCompliant(){return false;}
public boolean acceptsURL(String s){return s.startsWith("jdbc:q:");}
public Connection connect(String s,Properties p)throws SQLException{
  if(!acceptsURL(s))
    return null;
  return new co(s.substring(7),p.get("user"),p.get("password"),p.get("ssl"));
}

public DriverPropertyInfo[]getPropertyInfo(String s,Properties p)throws SQLException{return new DriverPropertyInfo[0];}
static{try{DriverManager.registerDriver(new jdbc());}catch(Exception e){O(e.getMessage());}}
static final int[]SQLTYPE={0,16,0,0,-2,5,4,-5,7,8,0,12,0,0,91,93,0,0,0,92};
static final String[]TYPE={"","boolean","","","byte","short","int","long","real","float","char","symbol","","month","date","timestamp","","minute","second","time"};

/**
 * default contructor for driver, not action taken
 */
public jdbc(){/* default contructor */}

static int find(String[]x,String s){
  int i=0;
  while(i<x.length&&!s.equals(x[i]))++i;
  return i;
}
static int find(int[]x,int j){
  int i=0;
  while(i<x.length&&x[i]!=j)++i;
  return i;
}
static void q(String s)throws SQLException{throw new SQLException(s);}
static void qNotSupported()throws SQLException{throw new SQLFeatureNotSupportedException("nyi");}
static boolean qNotSupportedBool()throws SQLException{throw new SQLFeatureNotSupportedException("nyi");}
static <T> T qNotSupportedObj()throws SQLException{throw new SQLFeatureNotSupportedException("nyi");}
static void q(Exception e)throws SQLException{throw new SQLException(e.getMessage());}

/**
 * A connection (session) with kdb+. SQL statements are executed and results are returned within the context of a connection
 */
public class co implements Connection{
 private boolean streaming;
 private c c;
 /**
  * Connect to kdb+
  * @param s connection url e.g. localhost:5001
  * @param u username 
  * @param p password
  * @throws SQLException issue connecting to kdb+
  */
 public co(String s,Object u,Object p,Object ssl)throws SQLException{
   int idx=s.indexOf(":");
   try{
    c=new c(s.substring(0,idx),Integer.parseInt(s.substring(idx+1)),u==null?"":(String)u+":"+(String)p,ssl!=null&&"true".equals(ssl));
    c.setCollectResponseAsync(true);
   }catch(Exception e){
    q(e);
   }
 }
 /**
  * Blocks until further response messages received from kdb+
  * @return deserialised data from kdb+ or null if no data present
  * @throws SQLException issue receiving msg (e.g. connection lost)
  */
 public Object getMoreRows()throws SQLException{
   if(isClosed())q(CLOSED);
   if(streaming){
     try{
       Object[]msg=c.readMsg();
       if((byte)msg[0]==(byte)2) // response msg type is the last msg to be received
         streaming=false;
       return msg[1];
     }
     catch(IOException e){
      close();
      q(e);
     }
     catch(Exception e){
       q(e);
     }
   }
   return null;
 }
 /**
  * Execute code on kdb+
  * @param s code to run
  * @param p args (null for no args)
  * @param maxRows max rows for query
  * @param fetchSize fetch size for returned data
  * @return empty array if issue, otherwise first element if whether streaming (more data available), second element is deserialised data returned
  * @throws SQLException error retrieving data
  */
 public Object[] ex(String s,Object[]p,int maxRows, int fetchSize)throws SQLException{
   if(isClosed())q(CLOSED);
   if(streaming)
     throw new SQLException("A ResultSet is still open on this connection with messages queued from the server");
   try{
     boolean args=0<kx.c.n(p);
     String lambda="{[maxRows;fetchSize;fn;args]$[not .Q.qt r:value[fn]args;::;count r:select[maxRows]from 0!r;{neg[.z.w]@/:-1_x;last x}(0N;fetchSize)#r;r]}["+(maxRows>0?maxRows:Integer.MAX_VALUE)+";"+(fetchSize>0?fetchSize:Integer.MAX_VALUE)+"]";
     if(args)
       c.k(lambda,s.toCharArray(),p);
     else
       c.k(lambda,".o.ex".toCharArray(),s.toCharArray());
     Object[]msg=c.readMsg();
     streaming=(byte)msg[0]==(byte)0;// msg[0]==0 means async msg, i.e. streamed result. msg[0]==2 would be the final response msg already
     return new Object[]{streaming,msg[1]};
   }
   catch(IOException e){
    close();
    q(e);
   }
   catch(Exception e){
    q(e);
   }
   return new Object[0];
 }
 /**
  * Run a sync query against kdb+
  * @param s code to execute
  * @return result of query (or null on execption)
  * @throws SQLException if issue with executing s (invalid or connection lost)
  */
 public rs qx(String s)throws SQLException{
   if(isClosed())q(CLOSED);
   try{
     c.k(s);
     return new rs(null,new Object[]{Boolean.FALSE,c.readMsg()[1]});
   }
   catch(IOException e){
    close();
    q(e);
   }
   catch(Exception e){
     q(e);
   }
   return null;
 }
 /**
  * Run a sync query against kdb+
  * @param s q code to execute
  * @param x argument to s
  * @return result of query (or null on execption)
  * @throws SQLException if issue with executing s (invalid or connection lost)
  */
 public rs qx(String s,Object x)throws SQLException{
   if(isClosed())q(CLOSED);
   try{
     c.k(s,x);
     return new rs(null,new Object[]{Boolean.FALSE,c.readMsg()[1]});
   }
   catch(IOException e){
    close();
    q(e);
   }
   catch(Exception e){
     q(e);
   }
   return null;
 }
 private boolean a=true;
 public void setAutoCommit(boolean b)throws SQLException{a=b;}
 public boolean getAutoCommit()throws SQLException{return a;}
 public void rollback()throws SQLException{ /* transactions not supported */ }
 public void commit()throws SQLException{ /* transactions not supported */ }
 public boolean isClosed()throws SQLException{return c==null;}
 public Statement createStatement()throws SQLException{return new st(this);}
 public DatabaseMetaData getMetaData()throws SQLException{return new dm(this);}
 public PreparedStatement prepareStatement(String s)throws SQLException{return new ps(this,s);}
 public CallableStatement prepareCall(String s)throws SQLException{return new cs(this,s);}
 public String nativeSQL(String s)throws SQLException{return s;}
 private boolean b;
 private int i=TRANSACTION_SERIALIZABLE;
 private int h=ResultSet.HOLD_CURSORS_OVER_COMMIT;
 public void setReadOnly(boolean x)throws SQLException{b=x;}
 public boolean isReadOnly()throws SQLException{return b;}
 public void setCatalog(String s)throws SQLException{q("setCatalog not supported");}
 public String getCatalog()throws SQLException{q("getCatalog not supported");return null;}
 public void setTransactionIsolation(int x)throws SQLException{i=x;}
 public int getTransactionIsolation()throws SQLException{return i;}
 public SQLWarning getWarnings()throws SQLException{return null;}
 public void clearWarnings()throws SQLException{ /* not supported */ }
 public void close()throws SQLException{
   if(isClosed())
     return;
   try{
     c.close();
   }catch(IOException e){
     /*ignored*/
   }finally{
     c=null;
   }
 }
 public Statement createStatement(int resultSetType,int resultSetConcurrency)throws SQLException{return new st(this);}
 public PreparedStatement prepareStatement(String s,int resultSetType,int resultSetConcurrency)throws SQLException{return new ps(this,s);}
 public CallableStatement prepareCall(String s,int resultSetType,int resultSetConcurrency)throws SQLException{return new cs(this,s);}
 public Map<String, Class<?>> getTypeMap()throws SQLException{return Collections.emptyMap();}
 public void setTypeMap(Map map)throws SQLException{ /* not supported */ }
//3
 public void setHoldability(int holdability)throws SQLException{h=holdability;}
 public int getHoldability()throws SQLException{return h;}
 public Savepoint setSavepoint()throws SQLException{q("setSavepoint not supported");return null;}
 public Savepoint setSavepoint(String name)throws SQLException{q("setSavepoint not supported");return null;}
 public void rollback(Savepoint savepoint)throws SQLException{ /* transactions not supported */ }
 public void releaseSavepoint(Savepoint savepoint)throws SQLException{ /* transactions not supported */ }
 public Statement createStatement(int resultSetType,int resultSetConcurrency,int resultSetHoldability)throws SQLException{return new st(this);}
 public PreparedStatement prepareStatement(String s,int resultSetType,int resultSetConcurrency,int resultSetHoldability)throws SQLException{return new ps(this,s);}
 public CallableStatement prepareCall(String s,int resultSetType,int resultSetConcurrency,int resultSetHoldability)throws SQLException{return new cs(this,s);}
 public PreparedStatement prepareStatement(String s,int autoGeneratedKeys)throws SQLException{return new ps(this,s);}
 public PreparedStatement prepareStatement(String s,int[]columnIndexes)throws SQLException{return new ps(this,s);}
 public PreparedStatement prepareStatement(String s,String[]columnNames)throws SQLException{return new ps(this,s);}
//4
 private Properties clientInfo=new Properties();
 public Clob createClob()throws SQLException{return qNotSupportedObj();}
 public Blob createBlob()throws SQLException{return qNotSupportedObj();}
 public NClob createNClob()throws SQLException{return qNotSupportedObj();}
 public SQLXML createSQLXML()throws SQLException{return qNotSupportedObj();}
 public boolean isValid(int i)throws SQLException{
   if(i<0)qNotSupported();
   return c!=null;
 }
 public void setClientInfo(String k, String v)throws SQLClientInfoException{clientInfo.setProperty(k,v);}
 public void setClientInfo(Properties p)throws SQLClientInfoException{clientInfo=p;}
 public String getClientInfo(String k)throws SQLException{return (String)clientInfo.get(k);}
 public Properties getClientInfo()throws SQLException{return clientInfo;}
 public Array createArrayOf(String string, Object[] os)throws SQLException{return qNotSupportedObj();}
 public Struct createStruct(String string, Object[] os)throws SQLException{return qNotSupportedObj();}
 public <T> T unwrap(Class<T> type)throws SQLException{return qNotSupportedObj();}
 public boolean isWrapperFor(Class<?> type)throws SQLException{return qNotSupportedBool();}
//1.7
 public int getNetworkTimeout()throws SQLFeatureNotSupportedException{throw new SQLFeatureNotSupportedException("nyi");}
 public void setNetworkTimeout(Executor executor,int milliseconds)throws SQLFeatureNotSupportedException{throw new SQLFeatureNotSupportedException("nyi");}
 public void abort(Executor executor)throws SQLFeatureNotSupportedException{throw new SQLFeatureNotSupportedException("nyi");}
 public void setSchema(String s){ /* not supported */ }
 public String getSchema(){return null;}
}

/**
 * The object used for executing a static SQL statement and returning the results it produces
 */
public class st implements Statement{
 private co co;
 private ResultSet resultSet;
 private int maxRows=0;
 private int timeOut;
 private int fetchSize=0;
 /**
  * Default empty args
  */
 protected Object[]p={};
 /**
  * Provide connection details of server to which sql statements should be executed
  * @param x connection to kdb+
  */
 public st(co x){co=x;}
 public int executeUpdate(String s)throws SQLException{
   if(resultSet!=null)
    resultSet.close();
   resultSet=null;
   Object[]nrsTuple=co.ex(s,p,maxRows,fetchSize);
   if(nrsTuple[1] instanceof c.Flip)
    q("Statement produced a ResultSet");
   return 0;}
 public ResultSet executeQuery(String s)throws SQLException{
   if(!execute(s))q("Statement did not produce a ResultSet");
   return getResultSet();
 }
 public boolean execute(String s)throws SQLException{
   if(resultSet!=null)
     resultSet.close();
   resultSet=null;
   Object[]nrsTuple=co.ex(s,p,maxRows,fetchSize); // get tuple of (streaming,first chunk of results)
   if(nrsTuple[1] instanceof c.Flip)
     resultSet=new rs(this,nrsTuple);
   return resultSet!=null;}
 public ResultSet getResultSet()throws SQLException{return resultSet;}
 public int getUpdateCount(){return -1;}
 public int getMaxRows()throws SQLException{return maxRows;}
 public void setMaxRows(int n)throws SQLException{
   if(n<0)
     q("setMaxRows(int), rows must be >=0. Passed "+n);
   maxRows=n;
 }
 public int getQueryTimeout()throws SQLException{return timeOut;}
 public void setQueryTimeout(int i)throws SQLException{timeOut=i;}
 // truncate excess BINARY,VARBINARY,LONGVARBINARY,CHAR,VARCHAR,and LONGVARCHAR fields
 public int getMaxFieldSize()throws SQLException{return 0;}
 public void setMaxFieldSize(int i)throws SQLException{ /* not supported */ }
 public void setEscapeProcessing(boolean b)throws SQLException{ /* not supported */ }
 public void cancel()throws SQLException{ /* not supported */ }
 public SQLWarning getWarnings()throws SQLException{return null;}
 public void clearWarnings()throws SQLException{ /* not supported */ }
 // positioned update? different statement?
 public void setCursorName(String name)throws SQLException{q("setCursorName not supported");}
 public boolean getMoreResults()throws SQLException{return false;}
 public void close()throws SQLException{
  if(resultSet!=null)
    resultSet.close();
  resultSet=null;
  co=null;
 }
 public void setFetchDirection(int direction)throws SQLException{q("setFetchDirection not supported");}
 public int getFetchDirection()throws SQLException{return 0;}
 public void setFetchSize(int rows)throws SQLException{
  if(fetchSize<0)
    throw new SQLException("setFetchSize(rows), rows must be >=0. Passed"+fetchSize);
  fetchSize=rows;
 }
 public int getFetchSize()throws SQLException{return fetchSize;}
 public int getResultSetConcurrency()throws SQLException{return ResultSet.CONCUR_READ_ONLY;}
 public int getResultSetType()throws SQLException{return ResultSet.TYPE_SCROLL_INSENSITIVE;}
 public void addBatch(String sql)throws SQLException{q("addBatch not supported");}
 public void clearBatch()throws SQLException{ /* not supported */ }
 public int[]executeBatch()throws SQLException{return new int[0];}
 public Connection getConnection()throws SQLException{return co;}
//3
 public boolean getMoreResults(int current)throws SQLException{return false;}
 public ResultSet getGeneratedKeys()throws SQLException{return null;}
 public int executeUpdate(String sql,int autoGeneratedKeys)throws SQLException{q("a");return 0;}
 public int executeUpdate(String sql,int[]columnIndexes)throws SQLException{q("a");return 0;}
 public int executeUpdate(String sql,String[]columnNames)throws SQLException{q("a");return 0;}
 public boolean execute(String sql,int autoGeneratedKeys)throws SQLException{q("a");return false;}
 public boolean execute(String sql,int[]columnIndexes)throws SQLException{q("a");return false;}
 public boolean execute(String sql,String[]columnNames)throws SQLException{q("a");return false;}
 public int getResultSetHoldability()throws SQLException{return ResultSet.HOLD_CURSORS_OVER_COMMIT;}
//4
 boolean poolable=false;
 public boolean isClosed()throws SQLException{return co==null||co.isClosed();}
 public void setPoolable(boolean b)throws SQLException{
  if(isClosed())
    throw new SQLException(CLOSED);
  poolable=b;
 }
 public boolean isPoolable()throws SQLException{
  if(isClosed())
    throw new SQLException(CLOSED);
  return poolable;
 }
 public <T> T unwrap(Class<T> type)throws SQLException{return qNotSupportedObj();}
 public boolean isWrapperFor(Class<?> type)throws SQLException{return qNotSupportedBool();}
//1.7
 boolean _closeOnCompletion=false;
 public void closeOnCompletion(){_closeOnCompletion=true;}
 public boolean isCloseOnCompletion(){return _closeOnCompletion;}
}

/**
 * An object that represents a precompiled SQL statement
 */
public class ps extends st implements PreparedStatement{
 private String s;
 /**
  * Provide connection details of server to which statement should be run
  * @param co connection to kdb+
  * @param x statement to run
  */
 public ps(co co,String x){super(co);s=x;}
 public ResultSet executeQuery()throws SQLException{return executeQuery(s);}
 public int executeUpdate()throws SQLException{return executeUpdate(s);}
 public boolean execute()throws SQLException{return execute(s);}
 public void clearParameters()throws SQLException{try{for(int i=0;i<c.n(p);i++)p[i]=null;}catch(UnsupportedEncodingException ex){throw new SQLException(ex);}}
 public void setObject(int i,Object x)throws SQLException{
  int n;
  try{n=c.n(p);
  }catch(UnsupportedEncodingException ex){
    throw new SQLException(ex);
  }
  if(i>n){
    Object[]r=new Object[i];
    System.arraycopy(p,0,r,0,n);
    p=r;
    while(n<i)p[n++]=null;
  }
  p[i-1]=x;
 }
 public void setObject(int i,Object x,int targetSqlType)throws SQLException{setObject(i,x);}
 public void setObject(int i,Object x,int targetSqlType,int scale)throws SQLException{setObject(i,x);}
 public void setNull(int i,int t)throws SQLException{setObject(i,c.NULL[find(SQLTYPE,t)]);}
 public void setBoolean(int i,boolean x)throws SQLException{setObject(i,Boolean.valueOf(x));}
 public void setByte(int i,byte x)throws SQLException{setObject(i,Byte.valueOf(x));}
 public void setShort(int i,short x)throws SQLException{setObject(i,Short.valueOf(x));}
 public void setInt(int i,int x)throws SQLException{setObject(i,Integer.valueOf(x));}
 public void setLong(int i,long x)throws SQLException{setObject(i,Long.valueOf(x));}
 public void setFloat(int i,float x)throws SQLException{setObject(i,Float.valueOf(x));}
 public void setDouble(int i,double x)throws SQLException{setObject(i,Double.valueOf(x));}
 public void setString(int i,String x)throws SQLException{setObject(i,x);}
 public void setDate(int i,Date x)throws SQLException{setObject(i,x);}
 public void setTime(int i,Time x)throws SQLException{setObject(i,x);}
 public void setTimestamp(int i,Timestamp x)throws SQLException{setObject(i,x);}
 public void setBytes(int i,byte[] x)throws SQLException{qNotSupported();}
 public void setBigDecimal(int i,BigDecimal x)throws SQLException{qNotSupported();}
 public void setAsciiStream(int i,InputStream x,int length)throws SQLException{qNotSupported();}
 /**
  * @deprecated depreciated in PreparedStatement
  */
 @Deprecated
 public void setUnicodeStream(int i,InputStream x,int length)throws SQLException{qNotSupported();}
 public void setBinaryStream(int i,InputStream x,int length)throws SQLException{qNotSupported();}
 public void addBatch()throws SQLException{ /* not supported */ }
 public void setCharacterStream(int parameterIndex,Reader reader,int length)throws SQLException{qNotSupported();}
 public void setRef(int i,Ref x)throws SQLException{qNotSupported();}
 public void setBlob(int i,Blob x)throws SQLException{qNotSupported();}
 public void setClob(int i,Clob x)throws SQLException{qNotSupported();}
 public void setArray(int i,Array x)throws SQLException{qNotSupported();}
 public ResultSetMetaData getMetaData()throws SQLException{q("getMetaData not supported");return null;}
 public void setDate(int parameterIndex,Date x,Calendar cal)throws SQLException{qNotSupported();}
 public void setTime(int parameterIndex,Time x,Calendar cal)throws SQLException{qNotSupported();}
 public void setTimestamp(int parameterIndex,Timestamp x,Calendar cal)throws SQLException{qNotSupported();}
 public void setNull(int paramIndex,int sqlType,String typeName)throws SQLException{qNotSupported();}
//3
 public void setURL(int parameterIndex,URL x)throws SQLException{qNotSupported();}
 public ParameterMetaData getParameterMetaData()throws SQLException{q("getParameterMetaData not supported");return null;}
//4
 public void setRowId(int i,RowId rowid)throws SQLException{qNotSupported();}
 public void setNString(int i,String string)throws SQLException{qNotSupported();}
 public void setNCharacterStream(int i,Reader reader,long l)throws SQLException{qNotSupported();}
 public void setNClob(int i,NClob nclob)throws SQLException{qNotSupported();}
 public void setClob(int i,Reader reader, long l)throws SQLException{qNotSupported();}
 public void setBlob(int i,InputStream in, long l)throws SQLException{qNotSupported();}
 public void setNClob(int i,Reader reader, long l)throws SQLException{qNotSupported();}
 public void setSQLXML(int i,SQLXML sqlxml)throws SQLException{qNotSupported();}
 public void setAsciiStream(int i,InputStream in,long l)throws SQLException{qNotSupported();}
 public void setBinaryStream(int i,InputStream in,long l)throws SQLException{qNotSupported();}
 public void setCharacterStream(int i,Reader reader,long l)throws SQLException{qNotSupported();}
 public void setAsciiStream(int i,InputStream in)throws SQLException{qNotSupported();}
 public void setBinaryStream(int i,InputStream in)throws SQLException{qNotSupported();}
 public void setCharacterStream(int i,Reader reader)throws SQLException{qNotSupported();}
 public void setNCharacterStream(int i,Reader reader)throws SQLException{qNotSupported();}
 public void setClob(int i,Reader reader)throws SQLException{qNotSupported();}
 public void setBlob(int i,InputStream in)throws SQLException{qNotSupported();}
 public void setNClob(int i,Reader reader)throws SQLException{qNotSupported();}
}

/**
 * kdb+ stored procedures not supported. Methods can throw exception to indicate not support
 * or return default values.
 */
public class cs extends ps implements CallableStatement{
   /**
  * Provide connection details of server to stored procedure would be executed
  * @param c connection to kdb+
  * @param s statement to run
  */
 public cs(co c,String s){super(c,s);}
 public void registerOutParameter(int i,int sqlType)throws SQLException{ /* not supported */ }
 public void registerOutParameter(int i,int sqlType,int scale)throws SQLException{ /* not supported */ }
 public boolean wasNull()throws SQLException{return false;}
 public String getString(int i)throws SQLException{return null;}
 public boolean getBoolean(int i)throws SQLException{return false;}
 public byte getByte(int i)throws SQLException{return 0;}
 public short getShort(int i)throws SQLException{return 0;}
 public int getInt(int i)throws SQLException{return 0;}
 public long getLong(int i)throws SQLException{return 0;}
 public float getFloat(int i)throws SQLException{return(float)0.0;}
 public double getDouble(int i)throws SQLException{return 0.0;}
 /**
  * @deprecated depreciated in CallableStatement
  */
 @Deprecated
 public BigDecimal getBigDecimal(int i,int scale)throws SQLException{return null;}
 public Date getDate(int i)throws SQLException{return null;}
 public Time getTime(int i)throws SQLException{return null;}
 public Timestamp getTimestamp(int i)throws SQLException{return null;}
 public byte[]getBytes(int i)throws SQLException{return new byte[0];}
 public Object getObject(int i)throws SQLException{return null;}
 public BigDecimal getBigDecimal(int parameterIndex)throws SQLException{return qNotSupportedObj();}
 public Object getObject(int i,Map map)throws SQLException{return qNotSupportedObj();}
 public Ref getRef(int i)throws SQLException{return qNotSupportedObj();}
 public Blob getBlob(int i)throws SQLException{return qNotSupportedObj();}
 public Clob getClob(int i)throws SQLException{return qNotSupportedObj();}
 public Array getArray(int i)throws SQLException{return qNotSupportedObj();}
 public Date getDate(int parameterIndex,Calendar cal)throws SQLException{return qNotSupportedObj();}
 public Time getTime(int parameterIndex,Calendar cal)throws SQLException{return qNotSupportedObj();}
 public Timestamp getTimestamp(int parameterIndex,Calendar cal)throws SQLException{return qNotSupportedObj();}
 public void registerOutParameter(int paramIndex,int sqlType,String typeName)throws SQLException{qNotSupported();}
//3
 public void registerOutParameter(String parameterName,int sqlType)throws SQLException{qNotSupported();}
 public void registerOutParameter(String parameterName,int sqlType,int scale)throws SQLException{qNotSupported();}
 public void registerOutParameter(String parameterName,int sqlType,String typeName)throws SQLException{qNotSupported();}
 public URL getURL(int parameterIndex)throws SQLException{return qNotSupportedObj();}
 public void setURL(String parameterName,URL val)throws SQLException{qNotSupported();}
 public void setNull(String parameterName,int sqlType)throws SQLException{qNotSupported();}
 public void setBoolean(String parameterName,boolean x)throws SQLException{qNotSupported();}
 public void setByte(String parameterName,byte x)throws SQLException{qNotSupported();}
 public void setShort(String parameterName,short x)throws SQLException{qNotSupported();}
 public void setInt(String parameterName,int x)throws SQLException{qNotSupported();}
 public void setLong(String parameterName,long x)throws SQLException{qNotSupported();}
 public void setFloat(String parameterName,float x)throws SQLException{qNotSupported();}
 public void setDouble(String parameterName,double x)throws SQLException{qNotSupported();}
 public void setBigDecimal(String parameterName,BigDecimal x)throws SQLException{qNotSupported();}
 public void setString(String parameterName,String x)throws SQLException{qNotSupported();}
 public void setBytes(String parameterName,byte[]x)throws SQLException{qNotSupported();}
 public void setDate(String parameterName,Date x)throws SQLException{qNotSupported();}
 public void setTime(String parameterName,Time x)throws SQLException{qNotSupported();}
 public void setTimestamp(String parameterName,Timestamp x)throws SQLException{qNotSupported();}
 public void setAsciiStream(String parameterName,InputStream x,int length)throws SQLException{qNotSupported();}
 public void setBinaryStream(String parameterName,InputStream x,int length)throws SQLException{qNotSupported();}
 public void setObject(String parameterName,Object x,int targetSqlType,int scale)throws SQLException{qNotSupported();}
 public void setObject(String parameterName,Object x,int targetSqlType)throws SQLException{qNotSupported();}
 public void setObject(String parameterName,Object x)throws SQLException{qNotSupported();}
 public void setCharacterStream(String parameterName,Reader reader,int length)throws SQLException{qNotSupported();}
 public void setDate(String parameterName,Date x,Calendar cal)throws SQLException{qNotSupported();}
 public void setTime(String parameterName,Time x,Calendar cal)throws SQLException{qNotSupported();}
 public void setTimestamp(String parameterName,Timestamp x,Calendar cal)throws SQLException{qNotSupported();}
 public void setNull(String parameterName,int sqlType,String typeName)throws SQLException{qNotSupported();}
 public String getString(String parameterName)throws SQLException{return null;}
 public boolean getBoolean(String parameterName)throws SQLException{return false;}
 public byte getByte(String parameterName)throws SQLException{return 0;}
 public short getShort(String parameterName)throws SQLException{return 0;}
 public int getInt(String parameterName)throws SQLException{return 0;}
 public long getLong(String parameterName)throws SQLException{return 0;}
 public float getFloat(String parameterName)throws SQLException{return 0;}
 public double getDouble(String parameterName)throws SQLException{return 0;}
 public byte[]getBytes(String parameterName)throws SQLException{return new byte[0];}
 public Date getDate(String parameterName)throws SQLException{return null;}
 public Time getTime(String parameterName)throws SQLException{return null;}
 public Timestamp getTimestamp(String parameterName)throws SQLException{return null;}
 public Object getObject(String parameterName)throws SQLException{return null;}
 public BigDecimal getBigDecimal(String parameterName)throws SQLException{return null;}
 public Object getObject(String parameterName,Map map)throws SQLException{return null;}
 public Ref getRef(String parameterName)throws SQLException{return null;}
 public Blob getBlob(String parameterName)throws SQLException{return null;}
 public Clob getClob(String parameterName)throws SQLException{return null;}
 public Array getArray(String parameterName)throws SQLException{return null;}
 public Date getDate(String parameterName,Calendar cal)throws SQLException{return null;}
 public Time getTime(String parameterName,Calendar cal)throws SQLException{return null;}
 public Timestamp getTimestamp(String parameterName,Calendar cal)throws SQLException{return null;}
 public URL getURL(String parameterName)throws SQLException{return null;}
//4
 public RowId getRowId(int i)throws SQLException{return qNotSupportedObj();}
 public RowId getRowId(String string)throws SQLException{return qNotSupportedObj();}
 public void setRowId(String string, RowId rowid)throws SQLException{qNotSupported();}
 public void setNString(String string, String string1)throws SQLException{qNotSupported();}
 public void setNCharacterStream(String string, Reader reader, long l)throws SQLException{qNotSupported();}
 public void setNClob(String string, NClob nclob)throws SQLException{qNotSupported();}
 public void setClob(String string, Reader reader, long l)throws SQLException{qNotSupported();}
 public void setBlob(String string, InputStream in, long l)throws SQLException{qNotSupported();}
 public void setNClob(String string, Reader reader, long l)throws SQLException{qNotSupported();}
 public NClob getNClob(int i)throws SQLException{return qNotSupportedObj();}
 public NClob getNClob(String string)throws SQLException{return qNotSupportedObj();}
 public void setSQLXML(String string, SQLXML sqlxml)throws SQLException{qNotSupported();}
 public SQLXML getSQLXML(int i)throws SQLException{return qNotSupportedObj();}
 public SQLXML getSQLXML(String string)throws SQLException{return qNotSupportedObj();}
 public String getNString(int i)throws SQLException{return qNotSupportedObj();}
 public String getNString(String string)throws SQLException{return qNotSupportedObj();}
 public Reader getNCharacterStream(int i)throws SQLException{return qNotSupportedObj();}
 public Reader getNCharacterStream(String string)throws SQLException{return qNotSupportedObj();}
 public Reader getCharacterStream(int i)throws SQLException{return qNotSupportedObj();}
 public Reader getCharacterStream(String string)throws SQLException{return qNotSupportedObj();}
 public void setBlob(String string, Blob blob)throws SQLException{qNotSupported();}
 public void setClob(String string, Clob clob)throws SQLException{qNotSupported();}
 public void setAsciiStream(String string, InputStream in, long l)throws SQLException{qNotSupported();}
 public void setBinaryStream(String string, InputStream in, long l)throws SQLException{qNotSupported();}
 public void setCharacterStream(String string, Reader reader, long l)throws SQLException{qNotSupported();}
 public void setAsciiStream(String string, InputStream in)throws SQLException{qNotSupported();}
 public void setBinaryStream(String string, InputStream in)throws SQLException{qNotSupported();}
 public void setCharacterStream(String string, Reader reader)throws SQLException{qNotSupported();}
 public void setNCharacterStream(String string, Reader reader)throws SQLException{qNotSupported();}
 public void setClob(String string, Reader reader)throws SQLException{qNotSupported();}
 public void setBlob(String string, InputStream in)throws SQLException{qNotSupported();}
 public void setNClob(String string, Reader reader)throws SQLException{qNotSupported();}
//1.7
 public <T>T getObject(String s,Class<T> t)throws SQLFeatureNotSupportedException{throw new SQLFeatureNotSupportedException("nyi");}
 public <T>T getObject(int parameterIndex,Class<T>t)throws SQLFeatureNotSupportedException{throw new SQLFeatureNotSupportedException("nyi");} 
}

/**
 * A table of data representing a database result set, which is usually generated by executing a statement that queries kdb+.
 */
public class rs implements ResultSet{
 private st st;
 private String[]f;
 private Object o;
 private Object[] d;
 private boolean streamed;
 private boolean endOfStream;
 private int r; // cursor position
 private int n; // number of rows in the current chunk
 private int offset; // first absolute row number for this chunk
 /**
  * Result of executing code on kdb+
  * @param s statement executed (can be null for queries)
  * @param nrsTuple first element is whether streaming, followed by deserialised data returned
  * @throws SQLException issue with data provided
  */
 public rs(st s,Object[]nrsTuple)throws SQLException{
   st=s;
   init(nrsTuple[1]);
   streamed=(boolean)nrsTuple[0];
   offset=0;
   r=-1;
 }
 private void init(Object x)throws SQLException{
   c.Flip a;
   try{
     a=c.td(x);
     f=a.x;
     d=a.y;
     n=c.n(d[0]);
   }catch(UnsupportedEncodingException ex){throw new SQLException(ex);}
 }
 public ResultSetMetaData getMetaData()throws SQLException{return new rm(f,d);}
 public int findColumn(String s)throws SQLException{return 1+find(f,s);}
 public boolean next()throws SQLException{
   if(r+1>=offset+n&&streamed&&!endOfStream&&st!=null){
     Object x=st.co.getMoreRows();
     if(x!=null){
       offset+=n;
       init(x);
     }
     else
       endOfStream=true;
   }
   if(r+1<offset+n){r++;return true;}else return false;
}
 public boolean wasNull()throws SQLException{return o==null;}
 public Object getObject(int i)throws SQLException{
  o=c.at(d[i-1],r-offset);
  return o instanceof char[]?new String((char[])o):o;
 }  
 public boolean getBoolean(int i)throws SQLException{return((Boolean)getObject(i)).booleanValue();}
 public byte getByte(int i)throws SQLException{return((Byte)getObject(i)).byteValue();}
 public short getShort(int i)throws SQLException{Object x=getObject(i);return x==null?0:((Short)x).shortValue();}
 public int getInt(int i)throws SQLException{Object x=getObject(i);return x==null?0:x instanceof Integer?((Integer)x).intValue():((Short)x).intValue();}
 public long getLong(int i)throws SQLException{Object x=getObject(i);return x==null?0:((Long)x).longValue();}
 public float getFloat(int i)throws SQLException{Object x=getObject(i);return x==null?0:((Float)x).floatValue();}
 public double getDouble(int i)throws SQLException{Object x=getObject(i);return x==null?0:((Double)x).doubleValue();}
 public String getString(int i)throws SQLException{Object x=getObject(i);return x==null?null:x.toString();}
 public Date getDate(int i)throws SQLException{return(Date)getObject(i);}
 public Time getTime(int i)throws SQLException{return(Time)getObject(i);}
 public Timestamp getTimestamp(int i)throws SQLException{Object obj=getObject(i);return obj instanceof java.util.Date?new Timestamp(((java.util.Date)obj).getTime()):(Timestamp)obj;}
 public byte[]getBytes(int i)throws SQLException{return qNotSupportedObj();}
 /**
  * @deprecated depreciated in ResultSet
  */
 @Deprecated
 public BigDecimal getBigDecimal(int i,int scale)throws SQLException{return qNotSupportedObj();}
 public InputStream getAsciiStream(int i)throws SQLException{return qNotSupportedObj();}
 /**
  * @deprecated depreciated in ResultSet
  */
 @Deprecated
 public InputStream getUnicodeStream(int i)throws SQLException{return qNotSupportedObj();}
 public InputStream getBinaryStream(int i)throws SQLException{return qNotSupportedObj();}
 public Object getObject(String s)throws SQLException{return getObject(findColumn(s));}
 public boolean getBoolean(String s)throws SQLException{return getBoolean(findColumn(s));}
 public byte getByte(String s)throws SQLException{return getByte(findColumn(s));}
 public short getShort(String s)throws SQLException{return getShort(findColumn(s));}
 public int getInt(String s)throws SQLException{return getInt(findColumn(s));}
 public long getLong(String s)throws SQLException{return getLong(findColumn(s));}
 public float getFloat(String s)throws SQLException{return getFloat(findColumn(s));}
 public double getDouble(String s)throws SQLException{return getDouble(findColumn(s));}
 public String getString(String s)throws SQLException{return getString(findColumn(s));}
 public Date getDate(String s)throws SQLException{return getDate(findColumn(s));}
 public Time getTime(String s)throws SQLException{return getTime(findColumn(s));}
 public Timestamp getTimestamp(String s)throws SQLException{return getTimestamp(findColumn(s));}
 public byte[] getBytes(String s)throws SQLException{return getBytes(findColumn(s));}
 /**
  * @deprecated depreciated in ResultSet
  */
 @Deprecated
 public BigDecimal getBigDecimal(String s,int scale)throws SQLException{return getBigDecimal(findColumn(s),scale);}
 public InputStream getAsciiStream(String s)throws SQLException{return getAsciiStream(findColumn(s));}
 /**
  * @deprecated depreciated in ResultSet
  */
 @Deprecated
 public InputStream getUnicodeStream(String s)throws SQLException{return getUnicodeStream(findColumn(s));}
 public InputStream getBinaryStream(String s)throws SQLException{return getBinaryStream(findColumn(s));}
 public SQLWarning getWarnings()throws SQLException{return null;}
 public void clearWarnings()throws SQLException{ /* not supported */ }
 public String getCursorName()throws SQLException{q("getCursorName not supported");return"";}
 public void close()throws SQLException{d=null;if(st!=null)while(null!=st.co.getMoreRows());}// drain remaining streamed messages
 public Reader getCharacterStream(int columnIndex)throws SQLException{return qNotSupportedObj();}
 public Reader getCharacterStream(String columnName)throws SQLException{return qNotSupportedObj();}
 public BigDecimal getBigDecimal(int columnIndex)throws SQLException{return qNotSupportedObj();}
 public BigDecimal getBigDecimal(String columnName)throws SQLException{return qNotSupportedObj();}
 public boolean isBeforeFirst()throws SQLException{return r<0;}
 public boolean isAfterLast()throws SQLException{
   if(streamed)q("isAfterLast not supported on a streamed ResultSet");
   return r>=n;
 }
 public boolean isFirst()throws SQLException{return r==0;}
 public boolean isLast()throws SQLException{
   if(streamed)q("isLast not supported on a streamed ResultSet");
   return r==n-1;
 }
 public void beforeFirst()throws SQLException{
   if(streamed)q("beforeFirst not supported on a streamed ResultSet");
   r=-1;
 }
 public void afterLast()throws SQLException{
   if(streamed)q("afterLast not supported on a streamed ResultSet");
   r=n;
 }
 public boolean first()throws SQLException{
   if(streamed)q("first not supported on a streamed ResultSet");
   r=0;
   return n>0;
 }
 public boolean last()throws SQLException{
   if(streamed)q("last not supported on a streamed ResultSet");
   r=n-1;
   return n>0;
 }
 public int getRow()throws SQLException{return r+1;}
 public boolean absolute(int row)throws SQLException{
   if(streamed)q("absolute not supported on a streamed ResultSet");
   r=row-1;
   return r<n;
 }
 public boolean relative(int rows)throws SQLException{
   if(streamed)q("relative not supported on a streamed ResultSet");
   r+=rows;
   return r>=0&&r<n;
 }
 public boolean previous()throws SQLException{
   if(streamed)q("previous not supported on a streamed ResultSet");
   --r;
   return r>=0;
 }
 public void setFetchDirection(int direction)throws SQLException{q("setFetchDirection not supported");}
 public int getFetchDirection()throws SQLException{return FETCH_FORWARD;}
 public void setFetchSize(int rows)throws SQLException{ /* not supported */ }
 public int getFetchSize()throws SQLException{return st!=null?st.getFetchSize():0;}
 public int getType()throws SQLException{return streamed?TYPE_FORWARD_ONLY:TYPE_SCROLL_SENSITIVE;}
 public int getConcurrency()throws SQLException{return CONCUR_READ_ONLY;}
 public boolean rowUpdated()throws SQLException{return qNotSupportedBool();}
 public boolean rowInserted()throws SQLException{return qNotSupportedBool();}
 public boolean rowDeleted()throws SQLException{return qNotSupportedBool();}
 public void updateNull(int columnIndex)throws SQLException{qNotSupported();}
 public void updateBoolean(int columnIndex,boolean x)throws SQLException{qNotSupported();}
 public void updateByte(int columnIndex,byte x)throws SQLException{qNotSupported();}
 public void updateShort(int columnIndex,short x)throws SQLException{qNotSupported();}
 public void updateInt(int columnIndex,int x)throws SQLException{qNotSupported();}
 public void updateLong(int columnIndex,long x)throws SQLException{qNotSupported();}
 public void updateFloat(int columnIndex,float x)throws SQLException{qNotSupported();}
 public void updateDouble(int columnIndex,double x)throws SQLException{qNotSupported();}
 public void updateBigDecimal(int columnIndex,BigDecimal x)throws SQLException{qNotSupported();}
 public void updateString(int columnIndex,String x)throws SQLException{qNotSupported();}
 public void updateBytes(int columnIndex,byte[]x)throws SQLException{qNotSupported();}
 public void updateDate(int columnIndex,Date x)throws SQLException{qNotSupported();}
 public void updateTime(int columnIndex,Time x)throws SQLException{qNotSupported();}
 public void updateTimestamp(int columnIndex,Timestamp x)throws SQLException{qNotSupported();}
 public void updateAsciiStream(int columnIndex,InputStream x,int length)throws SQLException{qNotSupported();}
 public void updateBinaryStream(int columnIndex,InputStream x,int length)throws SQLException{qNotSupported();}
 public void updateCharacterStream(int columnIndex,Reader x,int length)throws SQLException{qNotSupported();}
 public void updateObject(int columnIndex,Object x,int scale)throws SQLException{qNotSupported();}
 public void updateObject(int columnIndex,Object x)throws SQLException{qNotSupported();}
 public void updateNull(String columnName)throws SQLException{qNotSupported();}
 public void updateBoolean(String columnName,boolean x)throws SQLException{qNotSupported();}
 public void updateByte(String columnName,byte x)throws SQLException{qNotSupported();}
 public void updateShort(String columnName,short x)throws SQLException{qNotSupported();}
 public void updateInt(String columnName,int x)throws SQLException{qNotSupported();}
 public void updateLong(String columnName,long x)throws SQLException{qNotSupported();}
 public void updateFloat(String columnName,float x)throws SQLException{qNotSupported();}
 public void updateDouble(String columnName,double x)throws SQLException{qNotSupported();}
 public void updateBigDecimal(String columnName,BigDecimal x)throws SQLException{qNotSupported();}
 public void updateString(String columnName,String x)throws SQLException{qNotSupported();}
 public void updateBytes(String columnName,byte[]x)throws SQLException{qNotSupported();}
 public void updateDate(String columnName,Date x)throws SQLException{qNotSupported();}
 public void updateTime(String columnName,Time x)throws SQLException{qNotSupported();}
 public void updateTimestamp(String columnName,Timestamp x)throws SQLException{qNotSupported();}
 public void updateAsciiStream(String columnName,InputStream x,int length)throws SQLException{qNotSupported();}
 public void updateBinaryStream(String columnName,InputStream x,int length)throws SQLException{qNotSupported();}
 public void updateCharacterStream(String columnName,Reader reader,int length)throws SQLException{qNotSupported();}
 public void updateObject(String columnName,Object x,int scale)throws SQLException{qNotSupported();}
 public void updateObject(String columnName,Object x)throws SQLException{qNotSupported();}
 public void insertRow()throws SQLException{qNotSupported();}
 public void updateRow()throws SQLException{qNotSupported();}
 public void deleteRow()throws SQLException{qNotSupported();}
 public void refreshRow()throws SQLException{qNotSupported();}
 public void cancelRowUpdates()throws SQLException{qNotSupported();}
 public void moveToInsertRow()throws SQLException{qNotSupported();}
 public void moveToCurrentRow()throws SQLException{qNotSupported();}
 public Statement getStatement()throws SQLException{return st;}
 public Object getObject(int i,Map map)throws SQLException{return qNotSupportedObj();}
 public Ref getRef(int i)throws SQLException{return qNotSupportedObj();}
 public Blob getBlob(int i)throws SQLException{return qNotSupportedObj();}
 public Clob getClob(int i)throws SQLException{return qNotSupportedObj();}
 public Array getArray(int i)throws SQLException{return qNotSupportedObj();}
 public Object getObject(String colName,Map map)throws SQLException{return qNotSupportedObj();}
 public Ref getRef(String colName)throws SQLException{return qNotSupportedObj();}
 public Blob getBlob(String colName)throws SQLException{return qNotSupportedObj();}
 public Clob getClob(String colName)throws SQLException{return qNotSupportedObj();}
 public Array getArray(String colName)throws SQLException{return qNotSupportedObj();}
 public Date getDate(int columnIndex,Calendar cal)throws SQLException{return qNotSupportedObj();}
 public Date getDate(String columnName,Calendar cal)throws SQLException{return qNotSupportedObj();}
 public Time getTime(int columnIndex,Calendar cal)throws SQLException{return qNotSupportedObj();}
 public Time getTime(String columnName,Calendar cal)throws SQLException{return qNotSupportedObj();}
 public Timestamp getTimestamp(int columnIndex,Calendar cal)throws SQLException{return qNotSupportedObj();}
 public Timestamp getTimestamp(String columnName,Calendar cal)throws SQLException{return qNotSupportedObj();}
//3
 public URL getURL(int columnIndex)throws SQLException{return qNotSupportedObj();}
 public URL getURL(String columnName)throws SQLException{return qNotSupportedObj();}
 public void updateRef(int columnIndex,Ref x)throws SQLException{qNotSupported();}
 public void updateRef(String columnName,Ref x)throws SQLException{qNotSupported();}
 public void updateBlob(int columnIndex,Blob x)throws SQLException{qNotSupported();}
 public void updateBlob(String columnName,Blob x)throws SQLException{qNotSupported();}
 public void updateClob(int columnIndex,Clob x)throws SQLException{qNotSupported();}
 public void updateClob(String columnName,Clob x)throws SQLException{qNotSupported();}
 public void updateArray(int columnIndex,Array x)throws SQLException{qNotSupported();}
 public void updateArray(String columnName,Array x)throws SQLException{qNotSupported();}
//4
 public RowId getRowId(int i)throws SQLException{return qNotSupportedObj();}
 public RowId getRowId(String string)throws SQLException{return qNotSupportedObj();}
 public void updateRowId(int i, RowId rowid)throws SQLException{qNotSupported();}
 public void updateRowId(String string, RowId rowid)throws SQLException{qNotSupported();}
 public int getHoldability()throws SQLException{qNotSupported();return 0;}
 public boolean isClosed()throws SQLException{return d==null;}
 public void updateNString(int i, String string)throws SQLException{qNotSupported();}
 public void updateNString(String string, String string1)throws SQLException{qNotSupported();}
 public void updateNClob(int i, NClob nclob)throws SQLException{qNotSupported();}
 public void updateNClob(String string, NClob nclob)throws SQLException{qNotSupported();}
 public NClob getNClob(int i)throws SQLException{return qNotSupportedObj();}
 public NClob getNClob(String string)throws SQLException{return qNotSupportedObj();}
 public SQLXML getSQLXML(int i)throws SQLException{return qNotSupportedObj();}
 public SQLXML getSQLXML(String string)throws SQLException{return qNotSupportedObj();}
 public void updateSQLXML(int i, SQLXML sqlxml)throws SQLException{qNotSupported();}
 public void updateSQLXML(String string, SQLXML sqlxml)throws SQLException{qNotSupported();}
 public String getNString(int i)throws SQLException{return qNotSupportedObj();}
 public String getNString(String string)throws SQLException{return qNotSupportedObj();}
 public Reader getNCharacterStream(int i)throws SQLException{return qNotSupportedObj();}
 public Reader getNCharacterStream(String string)throws SQLException{return qNotSupportedObj();}
 public void updateNCharacterStream(int i, Reader reader, long l)throws SQLException{qNotSupported();}
 public void updateNCharacterStream(String string, Reader reader, long l)throws SQLException{qNotSupported();}
 public void updateAsciiStream(int i, InputStream in, long l)throws SQLException{qNotSupported();}
 public void updateBinaryStream(int i, InputStream in, long l)throws SQLException{qNotSupported();}
 public void updateCharacterStream(int i, Reader reader, long l)throws SQLException{qNotSupported();}
 public void updateAsciiStream(String string, InputStream in, long l)throws SQLException{qNotSupported();}
 public void updateBinaryStream(String string, InputStream in, long l)throws SQLException{qNotSupported();}
 public void updateCharacterStream(String string, Reader reader, long l)throws SQLException{qNotSupported();}
 public void updateBlob(int i, InputStream in, long l)throws SQLException{qNotSupported();}
 public void updateBlob(String string, InputStream in, long l)throws SQLException{qNotSupported();}
 public void updateClob(int i, Reader reader, long l)throws SQLException{qNotSupported();}
 public void updateClob(String string, Reader reader, long l)throws SQLException{qNotSupported();}
 public void updateNClob(int i, Reader reader, long l)throws SQLException{qNotSupported();}
 public void updateNClob(String string, Reader reader, long l)throws SQLException{qNotSupported();}
 public void updateNCharacterStream(int i, Reader reader)throws SQLException{qNotSupported();}
 public void updateNCharacterStream(String string, Reader reader)throws SQLException{qNotSupported();}
 public void updateAsciiStream(int i, InputStream in)throws SQLException{qNotSupported();}
 public void updateBinaryStream(int i, InputStream in)throws SQLException{qNotSupported();}
 public void updateCharacterStream(int i, Reader reader)throws SQLException{qNotSupported();}
 public void updateAsciiStream(String string, InputStream in)throws SQLException{qNotSupported();}
 public void updateBinaryStream(String string, InputStream in)throws SQLException{qNotSupported();}
 public void updateCharacterStream(String string, Reader reader)throws SQLException{qNotSupported();}
 public void updateBlob(int i, InputStream in)throws SQLException{qNotSupported();}
 public void updateBlob(String string, InputStream in)throws SQLException{qNotSupported();}
 public void updateClob(int i, Reader reader)throws SQLException{qNotSupported();}
 public void updateClob(String string, Reader reader)throws SQLException{qNotSupported();}
 public void updateNClob(int i, Reader reader)throws SQLException{qNotSupported();}
 public void updateNClob(String string, Reader reader)throws SQLException{qNotSupported();}
 public <T> T unwrap(Class<T> type)throws SQLException{return qNotSupportedObj();}
 public boolean isWrapperFor(Class<?> type)throws SQLException{return qNotSupportedBool();}
//1.7
 public <T>T getObject(String parameterName,Class<T>t)throws SQLFeatureNotSupportedException{throw new SQLFeatureNotSupportedException("nyi");}
 public <T>T getObject(int columnIndex,Class<T>t)throws SQLFeatureNotSupportedException{throw new SQLFeatureNotSupportedException("nyi");}
}

/**
 * Used to get information about the types and properties of the columns in a ResultSet object
 */
public class rm implements ResultSetMetaData{
 private String[]f;
 private Object[]d;
 /**
  * Create metadata info from data returned in ResultSet
  * @param x column names
  * @param y column values
  */
 public rm(String[]x,Object[]y){f=x;d=y;}
 public int getColumnCount()throws SQLException{return f.length;}
 public String getColumnName(int i)throws SQLException{return f[i-1];}
 public String getColumnTypeName(int i)throws SQLException{return TYPE[c.t(d[i-1])];}
 public int getColumnDisplaySize(int i)throws SQLException{return 11;}
 public int getScale(int i)throws SQLException{return 2;}
 public int isNullable(int i)throws SQLException{return 1;}
 public String getColumnLabel(int i)throws SQLException{return getColumnName(i);}
 public int getColumnType(int i)throws SQLException{return SQLTYPE[c.t(d[i-1])];}
 public int getPrecision(int i)throws SQLException{return 11;} //SQLPREC[c.t(d[i-1])];}
 public boolean isSigned(int i)throws SQLException{return true;}
 public String getTableName(int i)throws SQLException{return"";}
 public String getSchemaName(int i)throws SQLException{return"";}
 public String getCatalogName(int i)throws SQLException{return"";}
 public boolean isReadOnly(int i)throws SQLException{return false;}
 public boolean isWritable(int i)throws SQLException{return false;}
 public boolean isDefinitelyWritable(int i)throws SQLException{return false;}
 public boolean isAutoIncrement(int i)throws SQLException{return false;}
 public boolean isCaseSensitive(int i)throws SQLException{return true;}
 public boolean isSearchable(int i)throws SQLException{return true;}
 public boolean isCurrency(int i)throws SQLException{return false;}
 public String getColumnClassName(int column)throws SQLException{q("getColumnClassName not supported");return null;}
//4
 public <T> T unwrap(Class<T> type)throws SQLException{return qNotSupportedObj();}
 public boolean isWrapperFor(Class<?> type)throws SQLException{return qNotSupportedBool();}
}

/**
 * Information about kdb+ server capabilities
 */
public class dm implements DatabaseMetaData{
 private co co;
 /**
  * Provide connection details of server to which meta data should be retrieved
  * @param x connection to kdb+
  */
 public dm(co x){co=x;}
 public ResultSet getCatalogs()throws SQLException{return co.qx("([]TABLE_CAT:`symbol$())");}
 public ResultSet getSchemas()throws SQLException{return co.qx("([]TABLE_SCHEM:`symbol$())");}
 public ResultSet getTableTypes()throws SQLException{return co.qx("([]TABLE_TYPE:`TABLE`VIEW)");}
 public ResultSet getTables(String a,String b,String t,String[] x)throws SQLException{
  return co.qx("raze{([]TABLE_CAT:`;TABLE_SCHEM:`;TABLE_NAME:system string`a`b x=`VIEW;TABLE_TYPE:x)}each",x);
 }
 public ResultSet getTypeInfo()throws SQLException{
  return co.qx("`DATA_TYPE xasc([]TYPE_NAME:`boolean`byte`short`int`long`real`float`symbol`date`time`timestamp;DATA_TYPE:16 -2 5 4 -5 7 8 12 91 92 93i;PRECISION:11i;LITERAL_PREFIX:`;LITERAL_SUFFIX:`;CREATE_PARAMS:`;NULLABLE:1h;CASE_SENSITIVE:1b;SEARCHABLE:1h;UNSIGNED_ATTRIBUTE:0b;FIXED_PREC_SCALE:0b;AUTO_INCREMENT:0b;LOCAL_TYPE_NAME:`;MINIMUM_SCALE:0h;MAXIMUM_SCALE:0h;SQL_DATA_TYPE:0i;SQL_DATETIME_SUB:0i;NUM_PREC_RADIX:10i)");
 }
 public ResultSet getColumns(String a,String b,String t,String c)throws SQLException{
  if(t.startsWith("%"))
    t="";
  return co.qx("select TABLE_CAT:`,TABLE_SCHEM:`,TABLE_NAME:n,COLUMN_NAME:c,DATA_TYPE:0i,TYPE_NAME:`int$t,COLUMN_SIZE:2000000000i,BUFFER_LENGTH:0i,DECIMAL_DIGITS:16i,NUM_PREC_RADIX:10i,NULLABLE:1i,REMARKS:`,COLUMN_DEF:`,SQL_DATA_TYPE:0i,SQL_DATETIME_SUB:0i,CHAR_OCTET_LENGTH:2000000000i,ORDINAL_POSITION:`int$1+til count n,NULLABLE:`YES from .Q.nct`"+t);
 }
 public ResultSet getPrimaryKeys(String a,String b,String t)throws SQLException{
  q("getPrimaryKeys not supported");
  return co.qx("");
 } //"q)([]TABLE_CAT:'',TABLE_SCHEM:'',TABLE_NAME:'"+t+"',COLUMN_NAME:key "+t+",KEY_SEQ:1+asc count key "+t+",PK_NAME:'')");}
 public ResultSet getImportedKeys(String a,String b,String t)throws SQLException{
  q("getImportedKeys not supported");
  return co.qx("");
 } //"q)select PKTABLE_CAT:'',PKTABLE_SCHEM:'',PKTABLE_NAME:x,PKCOLUMN_NAME:first each key each x,FKTABLE_CAT:'',FKTABLE_SCHEM:'',FKTABLE_NAME:'"+t+"',FKCOLUMN_NAME:y,KEY_SEQ:1,UPDATE_RULE:1,DELETE_RULE:0,FK_NAME:'',PK_NAME:'',DEFERRABILITY:0 from('x','y')vars fkey "+t);}
 public ResultSet getProcedures(String a,String b,String p)throws SQLException{
  q("getProcedures not supported");
  return co.qx("");
 } // "q)([]PROCEDURE_CAT:'',PROCEDURE_SCHEM:'',PROCEDURE_NAME:varchar(),r0:0,r1:0,r2:0,REMARKS:'',PROCEDURE_TYPE:0)");}
 public ResultSet getExportedKeys(String a,String b,String t)throws SQLException{
  q("getExportedKeys not supported");
  return null;
 }
 public ResultSet getCrossReference(String pa,String pb,String pt,String fa,String fb,String ft)throws SQLException{
  q("getCrossReference not supported");
  return null;
 }
 public ResultSet getIndexInfo(String a,String b,String t,boolean unique,boolean approximate)throws SQLException{
  q("getIndexInfo not supported");
  return null;
 }
 public ResultSet getProcedureColumns(String a,String b,String p,String c)throws SQLException{
  q("getProcedureColumns not supported");
  return null;
 }
// PROCEDURE_CAT PROCEDURE_SCHEM PROCEDURE_NAME ...
 public ResultSet getColumnPrivileges(String a,String b,String table,String columnNamePattern)throws SQLException{
  q("getColumnPrivileges not supported");
  return null;
 }
//select TABLE_CAT TABLE_SCHEM TABLE_NAME COLUMN_NAME GRANTOR GRANTEE PRIVILEGE IS_GRANTABLE ordered by COLUMN_NAME and PRIVILEGE.
 public ResultSet getTablePrivileges(String a,String b,String t)throws SQLException{
  q("getTablePrivileges not supported");
  return null;
 }
//select TABLE_CAT TABLE_SCHEM TABLE_NAME GRANTOR GRANTEE PRIVILEGE IS_GRANTABLE ordered by TABLE_SCHEM,TABLE_NAME,and PRIVILEGE.
 public ResultSet getBestRowIdentifier(String a,String b,String t,int scope,boolean nullable)throws SQLException{
  q("getBestRowIdentifier not supported");
  return null;
 }
//select SCOPE COLUMN_NAME DATA_TYPE TYPE_NAME COLUMN_SIZE DECIMAL_DIGITS PSEUDO_COLUMN ordered by SCOPE
 public ResultSet getVersionColumns(String a,String b,String t)throws SQLException{
  q("getVersionColumns not supported");
  return null;
 }
//select SCOPE COLUMN_NAME DATA_TYPE TYPE_NAME COLUMN_SIZE DECIMAL_DIGITS PSEUDO_COLUMN ordered by SCOPE
 public boolean allProceduresAreCallable()throws SQLException{return true;}
 public boolean allTablesAreSelectable()throws SQLException{return true;}
 public boolean dataDefinitionCausesTransactionCommit()throws SQLException{return false;}
 public boolean dataDefinitionIgnoredInTransactions()throws SQLException{return false;}
 public boolean doesMaxRowSizeIncludeBlobs()throws SQLException{return true;}
 public String getSchemaTerm()throws SQLException{return"schema";}
 public String getProcedureTerm()throws SQLException{return"procedure";}
 public String getCatalogTerm()throws SQLException{return"catalog";}
 public String getCatalogSeparator()throws SQLException{return".";}
 public int getMaxBinaryLiteralLength()throws SQLException{return 0;}
 public int getMaxCharLiteralLength()throws SQLException{return 0;}
 public int getMaxColumnNameLength()throws SQLException{return 0;}
 public int getMaxColumnsInGroupBy()throws SQLException{return 0;}
 public int getMaxColumnsInIndex()throws SQLException{return 0;}
 public int getMaxColumnsInOrderBy()throws SQLException{return 0;}
 public int getMaxColumnsInSelect()throws SQLException{return 0;}
 public int getMaxColumnsInTable()throws SQLException{return 0;}
 public int getMaxConnections()throws SQLException{return 0;}
 public int getMaxCursorNameLength()throws SQLException{return 0;}
 public int getMaxIndexLength()throws SQLException{return 0;}
 public int getMaxSchemaNameLength()throws SQLException{return 0;}
 public int getMaxProcedureNameLength()throws SQLException{return 0;}
 public int getMaxCatalogNameLength()throws SQLException{return 0;}
 public int getMaxRowSize()throws SQLException{return 0;}
 public int getMaxStatementLength()throws SQLException{return 0;}
 public int getMaxStatements()throws SQLException{return 0;}
 public int getMaxTableNameLength()throws SQLException{return 0;}
 public int getMaxTablesInSelect()throws SQLException{return 0;}
 public int getMaxUserNameLength()throws SQLException{return 0;}
 public int getDefaultTransactionIsolation()throws SQLException{return Connection.TRANSACTION_SERIALIZABLE;}
 public String getSQLKeywords()throws SQLException{return"show,meta,load,save";}
 public String getNumericFunctions()throws SQLException{return"";}
 public String getStringFunctions()throws SQLException{return"";}
 public String getSystemFunctions()throws SQLException{return"";}
 public String getTimeDateFunctions()throws SQLException{return"";}
 public String getSearchStringEscape()throws SQLException{return"";}
 public String getExtraNameCharacters()throws SQLException{return"";}
 public String getIdentifierQuoteString()throws SQLException{return"";}
 public String getURL()throws SQLException{return null;}
 public String getUserName()throws SQLException{return"";}
 public String getDatabaseProductName()throws SQLException{return"kdb";}
 public String getDatabaseProductVersion()throws SQLException{return"2.0";}
 public String getDriverName()throws SQLException{return"jdbc";}
 public String getDriverVersion()throws SQLException{return MAJOR_VERSION+"."+MINOR_VERSION;}
 public int getDriverMajorVersion(){return MAJOR_VERSION;}
 public int getDriverMinorVersion(){return MINOR_VERSION;}
 public boolean isCatalogAtStart()throws SQLException{return true;}
 public boolean isReadOnly()throws SQLException{return false;}
 public boolean nullsAreSortedHigh()throws SQLException{return false;}
 public boolean nullsAreSortedLow()throws SQLException{return true;}
 public boolean nullsAreSortedAtStart()throws SQLException{return false;}
 public boolean nullsAreSortedAtEnd()throws SQLException{return false;}
 public boolean supportsMixedCaseIdentifiers()throws SQLException{return false;}
 public boolean storesUpperCaseIdentifiers()throws SQLException{return false;}
 public boolean storesLowerCaseIdentifiers()throws SQLException{return false;}
 public boolean storesMixedCaseIdentifiers()throws SQLException{return true;}
 public boolean supportsMixedCaseQuotedIdentifiers()throws SQLException{return true;}
 public boolean storesUpperCaseQuotedIdentifiers()throws SQLException{return false;}
 public boolean storesLowerCaseQuotedIdentifiers()throws SQLException{return false;}
 public boolean storesMixedCaseQuotedIdentifiers()throws SQLException{return true;}
 public boolean supportsAlterTableWithAddColumn()throws SQLException{return true;}
 public boolean supportsAlterTableWithDropColumn()throws SQLException{return true;}
 public boolean supportsTableCorrelationNames()throws SQLException{return true;}
 public boolean supportsDifferentTableCorrelationNames()throws SQLException{return true;}
 public boolean supportsColumnAliasing()throws SQLException{return true;}
 public boolean nullPlusNonNullIsNull()throws SQLException{return true;}
 public boolean supportsExpressionsInOrderBy()throws SQLException{return true;}
 public boolean supportsOrderByUnrelated()throws SQLException{return false;}
 public boolean supportsGroupBy()throws SQLException{return true;}
 public boolean supportsGroupByUnrelated()throws SQLException{return false;}
 public boolean supportsGroupByBeyondSelect()throws SQLException{return false;}
 public boolean supportsLikeEscapeClause()throws SQLException{return false;}
 public boolean supportsMultipleResultSets()throws SQLException{return false;}
 public boolean supportsMultipleTransactions()throws SQLException{return false;}
 public boolean supportsNonNullableColumns()throws SQLException{return true;}
 public boolean supportsMinimumSQLGrammar()throws SQLException{return true;}
 public boolean supportsCoreSQLGrammar()throws SQLException{return true;}
 public boolean supportsExtendedSQLGrammar()throws SQLException{return false;}
 public boolean supportsANSI92EntryLevelSQL()throws SQLException{return true;}
 public boolean supportsANSI92IntermediateSQL()throws SQLException{return false;}
 public boolean supportsANSI92FullSQL()throws SQLException{return false;}
 public boolean supportsIntegrityEnhancementFacility()throws SQLException{return false;}
 public boolean supportsOuterJoins()throws SQLException{return false;}
 public boolean supportsFullOuterJoins()throws SQLException{return false;}
 public boolean supportsLimitedOuterJoins()throws SQLException{return false;}
 public boolean supportsConvert()throws SQLException{return false;}
 public boolean supportsConvert(int fromType,int toType)throws SQLException{return false;}
 public boolean supportsSchemasInDataManipulation()throws SQLException{return false;}
 public boolean supportsSchemasInProcedureCalls()throws SQLException{return false;}
 public boolean supportsSchemasInTableDefinitions()throws SQLException{return false;}
 public boolean supportsSchemasInIndexDefinitions()throws SQLException{return false;}
 public boolean supportsSchemasInPrivilegeDefinitions()throws SQLException{return false;}
 public boolean supportsCatalogsInDataManipulation()throws SQLException{return false;}
 public boolean supportsCatalogsInProcedureCalls()throws SQLException{return false;}
 public boolean supportsCatalogsInTableDefinitions()throws SQLException{return false;}
 public boolean supportsCatalogsInIndexDefinitions()throws SQLException{return false;}
 public boolean supportsCatalogsInPrivilegeDefinitions()throws SQLException{return false;}
 public boolean supportsSelectForUpdate()throws SQLException{return false;}
 public boolean supportsPositionedDelete()throws SQLException{return false;}
 public boolean supportsPositionedUpdate()throws SQLException{return false;}
 public boolean supportsOpenCursorsAcrossCommit()throws SQLException{return true;}
 public boolean supportsOpenCursorsAcrossRollback()throws SQLException{return true;}
 public boolean supportsOpenStatementsAcrossCommit()throws SQLException{return true;}
 public boolean supportsOpenStatementsAcrossRollback()throws SQLException{return true;}
 public boolean supportsStoredProcedures()throws SQLException{return false;}
 public boolean supportsSubqueriesInComparisons()throws SQLException{return true;}
 public boolean supportsSubqueriesInExists()throws SQLException{return true;}
 public boolean supportsSubqueriesInIns()throws SQLException{return true;}
 public boolean supportsSubqueriesInQuantifieds()throws SQLException{return true;}
 public boolean supportsCorrelatedSubqueries()throws SQLException{return true;}
 public boolean supportsUnion()throws SQLException{return true;}
 public boolean supportsUnionAll()throws SQLException{return true;}
 public boolean supportsTransactions()throws SQLException{return true;}
 public boolean supportsTransactionIsolationLevel(int level)throws SQLException{return true;}
 public boolean supportsDataDefinitionAndDataManipulationTransactions()throws SQLException{return true;}
 public boolean supportsDataManipulationTransactionsOnly()throws SQLException{return false;}
 public boolean usesLocalFiles()throws SQLException{return false;}
 public boolean usesLocalFilePerTable()throws SQLException{return false;}
 public boolean supportsResultSetType(int type)throws SQLException{return type!=ResultSet.TYPE_SCROLL_SENSITIVE;}
 public boolean supportsResultSetConcurrency(int type,int concurrency)throws SQLException{return type==ResultSet.CONCUR_READ_ONLY;}
 public boolean ownUpdatesAreVisible(int type)throws SQLException{return false;}
 public boolean ownDeletesAreVisible(int type)throws SQLException{return false;}
 public boolean ownInsertsAreVisible(int type)throws SQLException{return false;}
 public boolean othersUpdatesAreVisible(int type)throws SQLException{return false;}
 public boolean othersDeletesAreVisible(int type)throws SQLException{return false;}
 public boolean othersInsertsAreVisible(int type)throws SQLException{return false;}
 public boolean updatesAreDetected(int type)throws SQLException{return false;}
 public boolean deletesAreDetected(int type)throws SQLException{return false;}
 public boolean insertsAreDetected(int type)throws SQLException{return false;}
 public boolean supportsBatchUpdates()throws SQLException{return false;}
 public ResultSet getUDTs(String catalog,String schemaPattern,String typeNamePattern,int[]types)throws SQLException{return null;}
 public Connection getConnection()throws SQLException{return co;}
//3
 public boolean supportsSavepoints()throws SQLException{return false;}
 public boolean supportsNamedParameters()throws SQLException{return false;}
 public boolean supportsMultipleOpenResults()throws SQLException{return false;}
 public boolean supportsGetGeneratedKeys()throws SQLException{return false;}
 public ResultSet getSuperTypes(String catalog,String schemaPattern,String typeNamePattern)throws SQLException{return null;}
 public ResultSet getSuperTables(String catalog,String schemaPattern,String tableNamePattern)throws SQLException{return null;}
 public ResultSet getAttributes(String catalog,String schemaPattern,String typeNamePattern,String attributeNamePattern)throws SQLException{return null;}
 public boolean supportsResultSetHoldability(int holdability)throws SQLException{return false;}
 public int getResultSetHoldability()throws SQLException{return 0;}
 public int getDatabaseMajorVersion()throws SQLException{return 0;}
 public int getDatabaseMinorVersion()throws SQLException{return 0;}
 public int getJDBCMajorVersion()throws SQLException{return 0;}
 public int getJDBCMinorVersion()throws SQLException{return 0;}
 public int getSQLStateType()throws SQLException{return 0;}
 public boolean locatorsUpdateCopy()throws SQLException{return false;}
 public boolean supportsStatementPooling()throws SQLException{return false;}
//4
 public RowIdLifetime getRowIdLifetime()throws SQLException{return qNotSupportedObj();}
 public ResultSet getSchemas(String string, String string1)throws SQLException{return qNotSupportedObj();}
 public boolean supportsStoredFunctionsUsingCallSyntax()throws SQLException{return qNotSupportedBool();}
 public boolean autoCommitFailureClosesAllResultSets()throws SQLException{return qNotSupportedBool();}
 public ResultSet getClientInfoProperties()throws SQLException{return qNotSupportedObj();}
 public ResultSet getFunctions(String string, String string1, String string2)throws SQLException{return qNotSupportedObj();}
 public ResultSet getFunctionColumns(String string, String string1, String string2, String string3)throws SQLException{return qNotSupportedObj();}
 public <T> T unwrap(Class<T> type)throws SQLException{return qNotSupportedObj();}
 public boolean isWrapperFor(Class<?> type)throws SQLException{return qNotSupportedBool();}
//1.7
 public boolean generatedKeyAlwaysReturned(){return false;}
 public ResultSet getPseudoColumns(String catalog,String schemaPattern,String tableNamePattern,String columnNamePattern)throws SQLFeatureNotSupportedException{throw new SQLFeatureNotSupportedException("nyi");}
}
//1.7
 public Logger getParentLogger()throws SQLFeatureNotSupportedException{throw new SQLFeatureNotSupportedException("nyi");}
}

/*
class ar implements Array{
 public String getBaseTypeName()throws SQLException{qNotSupported();return null;}
 public int getBaseType()throws SQLException{qNotSupported();return 0;}
 public Object getArray()throws SQLException{qNotSupported();return null;}
 public Object getArray(Map map)throws SQLException{qNotSupported();return null;}
 public Object getArray(long index,int count)throws SQLException{qNotSupported();return null;}
 public Object getArray(long index,int count,Map map)throws SQLException{qNotSupported();return null;}
 public ResultSet getResultSet()throws SQLException{qNotSupported();return null;}
 public ResultSet getResultSet(Map map)throws SQLException{qNotSupported();return null;}
 public ResultSet getResultSet(long index,int count)throws SQLException{qNotSupported();return null;}
 public ResultSet getResultSet(long index,int count,Map map)throws SQLException{qNotSupported();return null;}}
class bl implements Blob{
 public long length()throws SQLException{qNotSupported();return 0L;}
 public byte[]getBytes(long pos,int length)throws SQLException{qNotSupported();return null;}
 public InputStream getBinaryStream()throws SQLException{qNotSupported();return null;}
 public long position(byte[]pattern,long start)throws SQLException{qNotSupported();return 0L;}
 public long position(Blob pattern,long start)throws SQLException{qNotSupported();return 0L;}}
class cl implements Clob{
 public long length()throws SQLException{qNotSupported();return 0L;}
 public String getSubString(long pos,int length)throws SQLException{qNotSupported();return null;}
 public Reader getCharacterStream()throws SQLException{qNotSupported();return null;}
 public InputStream getAsciiStream()throws SQLException{qNotSupported();return null;}
 public long position(String searchstr,long start)throws SQLException{qNotSupported();return 0L;}
 public long position(Clob searchstr,long start)throws SQLException{qNotSupported();return 0L;}}
class re implements Ref{public String getBaseTypeName()throws SQLException{qNotSupported();return null;}}
// DriverPropertyInfo a=new DriverPropertyInfo("user",null),b=new DriverPropertyInfo("password",null),r[]=new DriverPropertyInfo[2];
// a.required=b.required=false;r[0]=a;r[1]=b;for(int i=0;i<r.length;i++)r[i].value = p.getProperty(r[i].name);return r;}
public ResultSet getBestRowIdentifier(String a,String b,String t,int scope,boolean nullable)throws SQLException
  {return co.qx("select SCOPE:'1',COLUMN_NAME:name,DATA_TYPE:([x:('int','float','varchar','date','time','timestamp','varbinary')]
   y:(4,8,12,91,92,93,-3,1111))[T].y,TYPE_NAME:T,COLUMN_SIZE:2000000000,BUFFER_LENGTH:0,DECIMAL_DIGITS:16,PSEUDO_COLUMN:1 from meta " + t+" where name in key "+t);}
*/

