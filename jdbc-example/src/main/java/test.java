// q -p 5001   use q/c/jdbc.jar
import java.sql.*;
import java.util.logging.Logger;
import java.util.logging.Level;

public class test{
  private static final Logger LOGGER = Logger.getLogger(test.class.getName());
  public static void main(String[] args){
    System.setProperty("java.util.logging.SimpleFormatter.format","[%1$tF %1$tT] [%4$-7s] %5$s %n");
    Connection h = null;
    Statement st = null;
    PreparedStatement p = null;
    try{
      Class.forName("jdbc");  //loads the driver 
      h = DriverManager.getConnection("jdbc:q:localhost:5001","","");
      st = h.createStatement();
      st.executeUpdate("create table t(x int,f float,s varchar(0),d date,t time,z timestamp)");
      //e.execute("insert into t values(9,2.3,'aaa',date'2000-01-02',time'12:34:56',timestamp'2000-01-02 12:34:56)");

      p = h.prepareStatement("q){`t insert 0N!a::x}"); //insert into t values(?,?,?,?,?,?)");
      p.setInt(1,2);
      p.setDouble(2,2.3);p.setString(3,"qwe");
      p.setDate(4,Date.valueOf("2000-01-02"));
      p.setTime(5,Time.valueOf("12:34:56"));
      p.setTimestamp(6,Timestamp.valueOf("2000-01-02 12:34:56"));
      p.executeUpdate();

      ResultSet r = st.executeQuery("select * from t");
      ResultSetMetaData m=r.getMetaData();
      int n=m.getColumnCount();
      for(int i=1;i<=n;++i)LOGGER.log(Level.INFO,m.getColumnName(i));
      while(r.next())for(int i=1;i<=n;++i)LOGGER.log(Level.INFO,r.getObject(i).toString());
    }
    catch(Exception e){
      LOGGER.log(Level.SEVERE,e.getMessage());
    }
    finally{
      try{p.close();}catch(Exception e){/*ignored*/}
      try{st.close();}catch(Exception e){/*ignored*/}
      try{h.close();}catch(Exception e){/*ignored*/}
    }
  } 
}
