package flightapp;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * A collection of utility methods to help with parsing dbconn.properties.
 */
public class DBConnUtils {
  /**
   * Open and return a connection using dbconn.properties file
   *
   * @throws SQLException
   * @throws IOException
   */
  public static Connection openConnection() throws SQLException, IOException {
    // Connect to the database with the provided connection configuration
    Properties configProps = new Properties();
    configProps.load(new FileInputStream("dbconn.properties"));

    String serverURL = configProps.getProperty("flightapp.server_url");
    String dbName = configProps.getProperty("flightapp.database_name");
    String adminName = configProps.getProperty("flightapp.username");
    String password = configProps.getProperty("flightapp.password");

    String connectionUrl =
        String.format("jdbc:sqlserver://%s:1433;databaseName=%s;user=%s;password=%s", 
                      serverURL, dbName, adminName, password);
    Connection conn = DriverManager.getConnection(connectionUrl);

    // By default, automatically commit after each statement
    conn.setAutoCommit(true);

    // By default, set the transaction isolation level to serializable
    conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

    return conn;
  }

  /**
   * Get the table suffix
   *
   * @returns null if the suffix wasn't specified, or was specified and empty
   * @throws IOException
   */
  public static String getTableSuffix() throws SQLException, IOException {
    // Connect to the database with the provided connection configuration
    Properties configProps = new Properties();
    configProps.load(new FileInputStream("dbconn.properties"));

    final String PROPERTY_NAME = "flightapp.tablename_suffix";

    String suffix = configProps.getProperty(PROPERTY_NAME);
    if (suffix == null || suffix.isEmpty()) {
      suffix = System.getProperty(PROPERTY_NAME);
      if (suffix != null && !suffix.isEmpty()) {
        return suffix;
      }
      return null;
    } else {
      return suffix;
    }
  }
}
