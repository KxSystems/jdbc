// q -p 5001   use q/c/jdbc.jar
import java.sql.*;
import java.util.logging.Logger;
import java.util.logging.Level;

public class Example1{
  private static final Logger LOGGER = Logger.getLogger(Example1.class.getName());
  static final String DB_URL="jdbc:q:localhost:5001";
  static final String USER="";
  static final String PASS="";
  public static void main(String[] args){
    System.setProperty("java.util.logging.SimpleFormatter.format","[%1$tF %1$tT] [%4$-7s] %5$s %n");
    Connection h = null;
    Statement st = null;
    PreparedStatement p = null;
    try{
      Class.forName("jdbc");  //loads the driver 
      LOGGER.info("Connecting to localhost:5001");
      h = DriverManager.getConnection(DB_URL,USER,PASS);

      LOGGER.info("Creating table 't'");
      st = h.createStatement();
      st.executeUpdate("create table t(x int,f float,s varchar(0),d date,t time,z timestamp)");

      LOGGER.info("Inserting into 't'");
      p = h.prepareStatement("q){`t insert 0N!a::x}"); 
      p.setInt(1,10);
      p.setDouble(2,10.3);
      p.setString(3,"qwe1");
      p.setDate(4,Date.valueOf("2000-01-02"));
      p.setTime(5,Time.valueOf("12:34:56"));
      p.setTimestamp(6,Timestamp.valueOf("2000-01-02 12:34:56"));
      p.executeUpdate();

      LOGGER.info("Contents of 't' now:");
      ResultSet r = st.executeQuery("select * from t");
      ResultSetMetaData m=r.getMetaData();
      int n=m.getColumnCount();
      while(r.next())for(int i=1;i<=n;++i)LOGGER.log(Level.INFO,"{0} {1}",new Object[]{m.getColumnName(i),r.getObject(i)});
    }
    catch(Exception e){
      LOGGER.log(Level.SEVERE,e.getMessage());
    }
    finally{
      try{if(p!=null)p.close();}catch(Exception e){/*ignored*/}
      try{if(st!=null)st.close();}catch(Exception e){/*ignored*/}
      try{h.close();}catch(Exception e){/*ignored*/}
    }
  } 
}
