package flightapp;

import static org.junit.Assert.assertTrue;

import java.lang.Thread;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.stream.*;
import java.sql.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;


/**
 * Autograder for the transaction assignment
 */
@RunWith(Parameterized.class)
public class FlightServiceTest {
  BufferedWriter report;

  /**
   * The pathname and filename at which we'll log the list of passing tests
   */
  private static final String TESTLOG_PATHNAME = "report_pass";

  /**
   * The suffix we use to identify which files are intended to be tests
   */
  private static final String TEST_FILESUFFIX = ".test.txt";


  /**
   * Maximum number of concurrent sessions (ie, concurrent terminals) we will be testing
   */
  private static final int MAX_SESSIONS = 5;

  /**
   * Max time in seconds to wait for a session to complete
   */
  private static final int RESPONSE_WAIT_SECS = 60;

  /**
   * Thread pool used to run different sessions
   */
  private static ExecutorService pool;

  /**
   * Denotes a comment
   */
  private static final String COMMENTS = "#";

  /**
   * Denotes information mode change
   */
  private static final String DELIMITER = "*";

  /**
   * Denotes alternate result
   */
  private static final String ALTERNATIVE_RESULT_SEPARATOR = "|";

  /**
   * See usage for detailed comments.
   */
  private static final String BARRIER_COMMAND = "barrier";

  /**
   * Denotes private test.  The detailed output from these private tests is hidden
   * by default; to enable output from these private tests, use java property
   * <code>show_private_output</code>.
   */
  private static final String PRIVATE_TEST_MARKER = "private_test_case";

  /**
   * The list of tests whose output is private.
   */
  private static final Set<String> PRIVATE_TEST_LIST = new HashSet<>();

  /**
   * The Java property name used to toggle whether the detailed output from private tests
   * should be displayed, or merely the summary (ie, test name + success/fail).
   */
  private static final String PRIVATE_OUTPUT_PROPNAME = "show_private_output";

  /**
   * Whether to dump detailed failure messages in the test assertion for
   * ALL tests, or just the ones not annotated with PRIVATE_TEST_MARKER.
   */
  private static boolean showPrivateOutput = false;
  
  /**
   * Models a single session (ie, a single terminal that's issuing commands).
   * Callable from a thread.
   */
  static class Session implements Callable<String> {
    private Query q;
    private List<String> cmds; // commands that this session will execute
    private List<String> results; // the expected results from those
                                  // commands.  The entire output is stored
                                  // as a single string, even if there are
                                  // multiple output lines from executing
                                  // multiple commands
    private CyclicBarrier barrier;

    public Session(Query q, List<String> cmds, List<String> results) throws IOException, SQLException {
      this.q = q;
      this.cmds = cmds;
      this.results = results;
      this.barrier = null;
    }

    public List<String> results() {
      return results;
    }

    public int numBarrierInvocations() {
      int numBarrierInvocations = 0;
      for (String cmd : cmds) {
        if (cmd.equals(BARRIER_COMMAND)) {
          numBarrierInvocations++;
        }
      }
      return numBarrierInvocations;
    }

    public void setBarrier(CyclicBarrier b) {
      this.barrier = b;
    }

    @Override
    public String call() {
      StringBuffer sb = new StringBuffer();
      for (String cmd : cmds) {
        if (cmd.equals(BARRIER_COMMAND) && barrier != null) {
          // HELLO STUDENTS!  Congratulations on finding the secret "barrier" command!
          //
          // This "hidden" command implements a barrier across all sessions; no session may
          // proceed until all sessions have entered the barrier.  This is useful for, for
          // example, designating one session to do the setup (eg, creating an account and
          // booking a reservation) and then having all sessions attempt to do the test
          // concurrently (eg, paying for the reservation).
          try {
            barrier.await();
          } catch(Exception e) {
            e.printStackTrace();  // since this is a "hidden" command, just swallow any errors
          }
        } else {
          sb.append(FlightService.execute(q, cmd));
        }
      }

      return sb.toString();
    }

    public void shutdown() throws Exception {
      this.q.closeConnection();
    }
  }

  /**
   * Parse the input test case.
   *
   * There may be more than one concurrent session, each delimited by DELIMITER.
   *     - Within a SINGLE session, there is a sequence of commands, followed by DELIMITER.
   *       Once we've encountered the delimiter, we switch to parsing one or more results.
   *     - A single result is the sequence of outputs from the specified commands.  If
   *       there is more than one possible result, they are separated by the
   *       ALTERNATIVE_RESULT_SEPARATOR
   *     - There is a final delimiter to indicate that the current session is complete.
   * If there is more than one session, we repeat as above.
   *
   * Example:
   *     # Session 1
   *     cmd1
   *     cmd2
   *     *  # delimiter
   *     result1.output1
   *     result1.output2
   *     |  # result delimiter
   *     result2.output1
   *     result2.output2
   *
   *     # Session 2
   *     cmdA
   *     *
   *     outputA
   *     *
   *
   * @param filename test case's path and file name
   * @return new Session objects with commands to run and expected results
   * @throws Exception
   */
  static List<Session> parse(String filename) throws IOException, SQLException {
    List<Session> sessions = new ArrayList<>();  // recall that a session is a single
                                                 // terminal that's executing commands
                                                 // against our database

    List<String> currCmds = new ArrayList<>();     // commands executed in the current session
    List<String> currResults = new ArrayList<>();  // results from the current session; a single
                                                   // result is likely to include newlines (it is
                                                   // the output of ALL the commands).  This list's
                                                   // length is equal to the number of alternative
                                                   // results for this session
    String partialResult = "";                     // A singular result, to be added to currResults
    boolean isCmd = true;

    BufferedReader reader = new BufferedReader(new FileReader(filename));
    String l;
    int lineNumber = 0;
    while ((l = reader.readLine()) != null) {
      lineNumber++;

      // Skip comment lines
      if (l.startsWith(COMMENTS)) {
        String line = l.substring(1).trim();
        String[] tokens = line.split("\\s+");
        if (tokens[0].equals(PRIVATE_TEST_MARKER)) {
          PRIVATE_TEST_LIST.add(filename);
        }
        continue;

      // Switch between recording commands and recording results (there may be more than
      // one result)
      } else if (l.startsWith(DELIMITER)) {
        if (isCmd) {
          isCmd = false;
        } else {
          // A single session's list of possible results has finished; record the current
          // result and finalize the entire session.
          currResults.add(partialResult);
          sessions.add(new Session(new Query(), currCmds, currResults));

          partialResult = "";
          currCmds = new ArrayList<>();
          currResults = new ArrayList<>();
          isCmd = true;
        }

      // Record an alternate result for the current session
      } else if (l.startsWith(ALTERNATIVE_RESULT_SEPARATOR)) {
        if (isCmd) {
          reader.close();
          throw new IllegalArgumentException(String.format(
              "Input file %s is malformatted on line: %d", filename, lineNumber));
        } else {
          currResults.add(partialResult);
          partialResult = "";
        }
      } else if (l.trim().isEmpty()) {
        // Skip blank lines
        continue;
      } else {
        // Last option: build command list or the current result

        // Ignore trailing comments
        l = l.split(COMMENTS, 2)[0].trim();
        
        // Add the current line either as (1) new command or (2) the next
        // line in our current result
        if (isCmd) {
          currCmds.add(l);
        } else {
          partialResult = partialResult + l + "\n";
        }
      }
    }
    reader.close();

    // Everything should be parsed by now and put into session objects
    if (currCmds.size() > 0 || partialResult.length() > 0 || currResults.size() > 0) {
      throw new IllegalArgumentException(
          String.format("Input file %s is malformatted, extra information found."
                        + "  #commands=%s, len(result)=%s, #results=%s",
                        filename, currCmds.size(), partialResult.length(), currResults.size()));
    }

    // Verify that all sessions have the same number of possible alternative results and invoke
    // the same number of barriers
    Session s = sessions.get(0);
    int numResults = s.results().size();

    CyclicBarrier b = null;
    int numBarrierInvocations = s.numBarrierInvocations();
    if (numBarrierInvocations > 0) {
      b = new CyclicBarrier(sessions.size());
      s.setBarrier(b);
    }

    for (int i = 1; i < sessions.size(); ++i) {
      s = sessions.get(i);
      if (s.results().size() != numResults) {
        throw new IllegalArgumentException(String.format(
            "Input file %s is malformed, session %s should have %s possible results rather than %s",
            filename, i, numResults, s.results.size()));
      } else if (s.numBarrierInvocations() != numBarrierInvocations) {
        throw new IllegalArgumentException(String.format(
            "Input file %s is malformed, unknown command in session %s", filename, i));
      }

      // Create the shared barrier for the sessions to synchronize on
      s.setBarrier(b);
    }

    return sessions;
  }

  /**
   * Creates the thread pool to execute test cases with multiple sessions.
   */
  @BeforeClass
  public static void setup() {
    System.out.println("Running test setup...");

    pool = Executors.newFixedThreadPool(MAX_SESSIONS);
    
    try {
      System.out.println("... using dbconn.properties for test credentials");
      Connection conn = DBConnUtils.openConnection();

      // We drop the tables instead of asking students to submit a dropTables.sql because we
      // don't trust them to drop tables correctly :)
      //
      // Basically, we identify student-created tables by querying for every table in the DB
      // (optionally specifying a tablename suffix) and deleting everything that's not our
      // four domain tables (ie, FLIGHTS, CARRIERS, MONTHS, WEEKDAYS).
      //
      // TODO(hctang): in 22au, we ran into an issue where a simple "DROP TABLE x" would time
      // out.  If this happens again, we should update the test instructions to tell students
      // to disable table resetting.
      boolean dropTables = System.getProperty("flightapp.droptables", "true")
        .equalsIgnoreCase("true");
      if (dropTables) {
        String tableSuffix = DBConnUtils.getTableSuffix();
        if (tableSuffix != null) {
          System.out.println("... resetting database (ie, dropping all tables with suffix: "
                             + tableSuffix + ")");
        } else {
          System.out.println("... fully resetting database (ie, dropping everything except "
                             + "domain tables)");
        }
        TestUtils.dropTablesWithOptionalSuffix(conn, tableSuffix);

        System.out.println("... running createTables.sql");
        TestUtils.runCreateTables(conn);
      } else {
        System.out.println("... not resetting student-created tables [WARNING!  WARNING!]");
      }

      TestUtils.checkTables(conn);
      conn.close();
    } catch (Exception e) {
      System.err.println("Failed to drop tables and/or run createTables.sql");
      e.printStackTrace(System.out);
      System.exit(1);
    }

    showPrivateOutput = System.getProperty(PRIVATE_OUTPUT_PROPNAME, "false")
      .equalsIgnoreCase("true");
    if (showPrivateOutput) {
      System.out.println("\nWARNING: detailed results from private tests will be output");
    }

    String reportPath = System.getProperty("report_pass");
    if (reportPath != null) {
      FileUtils.deleteQuietly(new File(reportPath));
    }

    System.out.println("\nStarting tests");
  }

  /**
   * A file that will be parsed as a test case scenario
   */
  protected String file;

  /**
   * Initialize a test case with a file name
   */
  public FlightServiceTest(String file) {
    this.file = file;
  }

  /**
   * Gets test case scenario files from the specified folder.
   */
  @Parameterized.Parameters
  public static Collection<String> files() throws IOException {
    String pathString = System.getProperty("test.cases");
    return Arrays.stream(pathString.split(":", -1)).map(Paths::get).flatMap(path -> {
      try {
        if (Files.isDirectory(path)) {
          try (Stream<Path> paths = Files.walk(path, 5, FileVisitOption.FOLLOW_LINKS)) {
            return paths.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(TEST_FILESUFFIX)).map(p -> {
                  try {
                    return p.toFile().getCanonicalPath().toString();
                  } catch (IOException e) {
                    return null;
                  }
                }).filter(p -> p != null).collect(Collectors.toList()).stream();
          }
        } else if (Files.isRegularFile(path)) {
          return Stream.of(path.toFile().getCanonicalPath().toString());
        } else {
          System.err.println(path + " does not exist.");
        }
      } catch (Exception e) {
        return Stream.empty();
      }
      return Stream.empty();
    }).collect(Collectors.toCollection(LinkedHashSet::new));
  }

  @Before
  public void clearDB() throws SQLException, IOException {
    // TODO(hctang): pull connection mgmt out of Query altogether, and make clearTables a
    // static method that accepts a conn argument instead.
    Query query = new Query();
    query.clearTables();
    query.closeConnection();

    String reportPath = System.getProperty(TESTLOG_PATHNAME);

    if (reportPath != null) {
      report = new BufferedWriter(new FileWriter(reportPath, true));
    }
  }

  @After
  public void after() throws SQLException, IOException {
    if (report != null) {
      report.close();
      report = null;
    }
  }

  /**
   * Runs the test case scenario
   */
  @Test
  public void runTest() throws Exception {
    System.out.println("Running test file: " + this.file);

    // Loads the scenario and initializes sessions
    List<Session> sessions = parse(this.file);
    List<Future<String>> futures = new ArrayList<>();
    for (Session sess : sessions) {
      futures.add(pool.submit(sess));
    }

    try {
      // Waits for output from each session
      List<String> outputs = new ArrayList<>();
      for (Future<String> f : futures) {
        try {
          outputs.add(f.get(RESPONSE_WAIT_SECS * futures.size(), TimeUnit.SECONDS));
        } catch (TimeoutException e) {
          System.out.println("Timed out!");
        }
      }

      // For each possible outcome, check if each session matches the respective output
      // for the given outcome
      // TODO(hctang): dafuq with this comment
      boolean passed = false;
      Map<Integer, List<String>> outcomes = new HashMap<Integer, List<String>>();
      for (int i = 0; i < sessions.get(0).results().size(); ++i) {
        boolean isSame = true;
        for (int j = 0; j < sessions.size(); ++j) {
          isSame = isSame && outputs.get(j).equals(sessions.get(j).results().get(i));
          if (!outcomes.containsKey(i)) {
            outcomes.put(i, new ArrayList<String>());
          }
          outcomes.get(i).add(sessions.get(j).results().get(i));
        }
        passed = passed || isSame;
      }

      // Print the result and debugging info (if applicable) under the assertion
      String error_message = "";
      if (!passed) {
        if (!showPrivateOutput && PRIVATE_TEST_LIST.contains(file)) {
          error_message = String.format("Failed: %s. No output since this test is private.",
                                        this.file);
        } else {
          String outcomesFormatted = "";
          for (Map.Entry<Integer, List<String>> outcome : outcomes.entrySet()) {
            outcomesFormatted += "===== Outcome " + outcome.getKey() + " =====\n";
            outcomesFormatted += formatOutput(outcome.getValue()) + "\n";
          }
          error_message = String.format(
            "Failed: %s. Actual outcome were: \n%s\n\nPossible outcomes were: \n%s\n",
            this.file, formatOutput(outputs), outcomesFormatted);
        }
      } else {
        if (report != null) {
          report.write(FilenameUtils.separatorsToUnix(this.file));
          report.newLine();
        }
      }

      assertTrue(error_message, passed);
    } catch (Exception e) {
      System.out.println("failed");
      e.printStackTrace(System.out);
      throw e;
    } finally {
      // Cleanup
      for (Session sess : sessions) {
        sess.shutdown();
      }
    }
  }

  public static String formatOutput(List<String> output) {
    StringBuilder sb = new StringBuilder();
    int i = 0;
    for (String s : output) {
      sb.append("---Terminal " + i + " begin\n");
      sb.append(s);
      sb.append("---Terminal " + i + " end\n");
      ++i;
    }

    return sb.toString();
  }
}
