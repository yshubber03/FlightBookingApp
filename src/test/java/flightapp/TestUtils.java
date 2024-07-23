package flightapp;

import java.io.FileReader;
import java.io.IOException;
import java.sql.*;
import java.util.*;

import org.apache.ibatis.jdbc.ScriptRunner;

public class TestUtils {

  private static final Set<String> DEFAULT_TABLES;
  private static final String BASE_SCHEMA = "dbo";
  private static final String[] TYPES = new String[]{"TABLE"};

  static {
    Set<String> temp = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    temp.addAll(Arrays.asList(new String[]{"Flights", "Months", "Carriers", "Weekdays"}));
    DEFAULT_TABLES = Collections.unmodifiableSet(temp);
  }

  public static void dropTablesWithOptionalSuffix(Connection conn, String suffix)
    throws SQLException
  {
    DatabaseMetaData metaData = conn.getMetaData();
    String schema = conn.getSchema();
    String normalizedSuffix = null;

    if (suffix != null && !suffix.isEmpty()) {
      normalizedSuffix = "_" + suffix.toLowerCase();
    }

    List<String> droppingTables = new ArrayList<>();
    try (Statement statement = conn.createStatement()) {
      try (ResultSet rs1 = metaData.getTables(null, schema, null, TYPES)) {
        while (rs1.next()) {

          String tableName = rs1.getString("TABLE_NAME");
          if (schema.equalsIgnoreCase(BASE_SCHEMA) && DEFAULT_TABLES.contains(tableName)) {
            continue;
          }
          if (normalizedSuffix != null && !tableName.toLowerCase().endsWith(normalizedSuffix)) {
            continue;
          }
 
          droppingTables.add(tableName);
          try (ResultSet rs2 = metaData.getImportedKeys(null, schema, tableName)) {
            while (rs2.next()) {
              String fk_name = rs2.getString("FK_NAME");
              String dropFK = String.format("ALTER TABLE %s DROP CONSTRAINT %s;",
                                            tableName, fk_name);
              statement.execute(dropFK);
            }
          }
        }
      }

      for (String table : droppingTables) {
        String dropTable = String.format("DROP TABLE %s;", table);
        statement.execute(dropTable);
      }
    } catch (SQLException e) {
      if (e.getErrorCode() == 1205) {
        dropTablesWithOptionalSuffix(conn, suffix);
      }
    }
  }

  public static void runCreateTables(Connection conn) throws SQLException, IOException {
    ScriptRunner scriptRunner = new ScriptRunner(conn);
    scriptRunner.setStopOnError(true);
    scriptRunner.setLogWriter(null);
    scriptRunner.setErrorLogWriter(null);
    FileReader reader = new FileReader("createTables.sql");
    scriptRunner.runScript(reader);
  }

  public static void checkTables(Connection conn) throws SQLException, IOException {
    DatabaseMetaData metaData = conn.getMetaData();
    String schema = conn.getSchema();
    if (schema.equalsIgnoreCase(BASE_SCHEMA))
      return;

    try (ResultSet rs = metaData.getTables(null, schema, null, TYPES)) {
      while (rs.next()) {
        String tableName = rs.getString("TABLE_NAME");
        if (DEFAULT_TABLES.contains(tableName)) {
          throw new IllegalStateException("Table '" + tableName
                                          + "' should not be in createTables.sql");
        }
      }
    }
  }
}
