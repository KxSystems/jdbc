package com.kx.jdbcexamples;
import java.sql.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Example of connecting to kdb+, examples of using updates,queries and resultsets.
 */
public class Example2 {
  private static final Logger LOGGER = Logger.getLogger(Example2.class.getName());
  private Example2(){/* default */}
  /**
   * To run example of connecting to kdb+, examples of using updates,queries and resultsets.
   * @param args 3 optional args. 0 is optional connection string of host:port e.g. localhost:1234, 1 is optional username, 2 is optional password
   */
  public static void main(String[] args){
    System.setProperty("java.util.logging.SimpleFormatter.format","[%1$tF %1$tT] [%4$-7s] %5$s %n");
    Connection conn=null;
    ResultSet rs=null;
    Statement stmt=null;
    PreparedStatement pstmt=null;
    String url="jdbc:q:";
    try{
      Class.forName("com.kx.jdbc");

      url+=(args.length>0)?args[0]:"localhost:5001";
      LOGGER.log(Level.INFO,"Connecting to {0}",url);
      conn=DriverManager.getConnection(url,args.length>1?args[1]:"",args.length>2?args[2]:"");

      LOGGER.info("Creating table and function...");
      stmt=conn.createStatement();
      stmt.executeUpdate("q)Employees:([]id:0 1 2;firstName:`Charlie`Arthur`Simon;lastName:`Skelton`Whitney`Garland;age:10 20 30;timestamp:.z.p+til 3)");
      stmt.executeUpdate("q)employeesYoungerThan:{[maxage]select from Employees where age<maxage}");
      LOGGER.info("Running SQL select...");
      rs=stmt.executeQuery("SELECT id, firstName, lastName, age,timestamp FROM Employees");

      while(rs.next()){
        long id=rs.getLong("id");
        long age=rs.getLong("age");
        String first=rs.getString("firstName");
        String last=rs.getString("lastName");
        Timestamp timestamp=rs.getTimestamp("timestamp");
        LOGGER.log(Level.INFO,"ID: {0}, Age: {1}, FirstName: {2}, LastName: {3}, Timestamp: {4}",new Object[]{id,age,first,last,timestamp});
      }
      rs.close();
      stmt.close();
    
      LOGGER.info("Running prepared statement (employees younger than 25)...");
      pstmt=conn.prepareStatement("{employeesYoungerThan . x}");
      pstmt.setLong(1,25);
      rs=pstmt.executeQuery();
      while(rs.next()){
        long id=rs.getLong("id");
        long age=rs.getLong("age");
        String first=rs.getString("firstName");
        String last=rs.getString("lastName");
        Timestamp timestamp=rs.getTimestamp("timestamp");
        LOGGER.log(Level.INFO,"ID: {0}, Age: {1}, FirstName: {2}, LastName: {3}, Timestamp: {4}",new Object[]{id,age,first,last,timestamp});
      }
    }catch(Exception e){
      LOGGER.log(Level.SEVERE,e.getMessage());
    }finally{
      try{if(rs!=null)rs.close();}catch(Exception e){/*ignored*/}
      try{if(stmt!=null)stmt.close();}catch(Exception e){/*ignored*/}
      try{if(pstmt!=null)pstmt.close();}catch(Exception e){/*ignored*/}
      try{conn.close();}catch(Exception e){/*ignored*/}
    }
  }
}
