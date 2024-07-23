package flightapp;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.Properties;

public abstract class QueryAbstract {
  // DB Connection
  protected Connection conn;

  // For checking for dangling transactions
  private static final String TRANCOUNT_SQL = "SELECT @@TRANCOUNT AS tran_count";
  private PreparedStatement tranCountStatement;

  protected QueryAbstract() throws SQLException, IOException {
    this.conn = DBConnUtils.openConnection();
    tranCountStatement = conn.prepareStatement(TRANCOUNT_SQL);
  }

  /**
   * Get underlying connection
   */
  public Connection getConnection() {
    return conn;
  }

  /**
   * Closes the application-to-database connection
   */
  public void closeConnection() throws SQLException {
    conn.close();
  }

  /**
   * Clear the data in any custom tables created.
   *
   * WARNING! Do not drop any tables and do not clear the flights table.
   */
  public final void clearTablesWrap() {
    try {
      clearTables();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    } finally {
      checkDanglingTransaction();
    }
  }

  public abstract void clearTables() throws SQLException;

  /**
   * Takes a user's username and password and attempts to log the user in.
   *
   * @param username user's username
   * @param password user's password
   *
   * @return If someone has already logged in, then return "User already logged in\n".  For all
   *         other errors, return "Login failed\n". Otherwise, return "Logged in as [username]\n".
   */
  public final String login(String username, String password) {
    try {
      return transaction_login(username, password);
    } finally {
      checkDanglingTransaction();
    }
  }

  public abstract String transaction_login(String username, String password);

  /**
   * Creates a new user within the system.
   *
   * @param username   new user's username. User names are unique within the system.
   * @param password   new user's password.
   * @param initAmount initial amount to deposit into the user's account, should be >= 0 (failure
   *                   otherwise).
   *
   * @return either "Created user {@code username}\n" or "Failed to create user\n" if failed.
   */
  public final String createCustomer(String username, String password, int initAmount) {
    try {
      return transaction_createCustomer(username, password, initAmount);
    } finally {
      checkDanglingTransaction();
    }
  }

  public abstract String transaction_createCustomer(String username, String password,
                                                    int initAmount);

  /**
   * Searches for flights, according to user-specified origin, destination, and other parameters.
   *
   * Searches for flights from the given origin city to the given destination city, on the given
   * day of the month. If {@code directFlight} is true, it only searches for direct flights,
   * otherwise is searches for direct flights and flights with two "hops." Only searches for up
   * to the number of itineraries given by {@code numberOfItineraries}.
   *
   * The results are sorted based on total flight time.
   *
   * @param originCity
   * @param destinationCity
   * @param directFlight        if true, then only search for direct flights, otherwise include
   *                            indirect flights as well
   * @param dayOfMonth
   * @param numberOfItineraries number of itineraries to return, must be positive
   *
   * @return If no itineraries were found, return "No flights match your selection\n". If an error
   *         occurs, then return "Failed to search\n".
   *
   *         Otherwise, the sorted itineraries printed in the following format:
   *
   *         Itinerary [itinerary number]: [number of flights] flight(s), [total flight time]
   *         minutes\n [first flight in itinerary]\n ... [last flight in itinerary]\n
   *
   *         Each flight should be printed using the same format as in the {@code Flight} class.
   *         Itinerary numbers in each search should always start from 0 and increase by 1.
   *
   * @see Query.Flight#toString()
   */
  public final String search(String originCity, String destinationCity, boolean directFlight,
                             int dayOfMonth, int numberOfItineraries) {
    try {
      return transaction_search(originCity, destinationCity, directFlight,
                                dayOfMonth, numberOfItineraries);
    } finally {
      checkDanglingTransaction();
    }
  }

  public abstract String transaction_search(String originCity, String destinationCity, 
                                            boolean directFlight, int dayOfMonth,
                                            int numberOfItineraries);

  /**
   * Reserves (but doesn't pay for) an itinerary generated from a previous search.
   *
   * @param itineraryId ID of the itinerary to book. This must be one that is returned by search
   *                    in the current session.
   *
   * @return If the user is not logged in, then return "Cannot book reservations, not logged
   *         in\n". If the user is trying to book an itinerary with an invalid ID or without
   *         having done a search, then return "No such itinerary {@code itineraryId}\n". If the
   *         user already has a reservation on the same day as the one that they are trying to
   *         book now, then return "You cannot book two flights in the same day\n". For all
   *         other errors, return "Booking failed\n".
   *
   *         If booking succeeds, return "Booked flight(s), reservation ID: [reservationId]\n"
   *         where reservationId is a unique number in the reservation system that starts from
   *         1 and increments by 1 each time a successful reservation is made by any user in
   *         the system.
   *
   * @see #search()
   */
  public final String book(int itineraryId) {
    try {
      return transaction_book(itineraryId);
    } finally {
      checkDanglingTransaction();
    }
  }

  public abstract String transaction_book(int itineraryId);

  /**
   * Pays for a previously-reserved itinerary
   *
   * @param reservationId the reservation to pay for.
   *
   * @return If no user has logged in, then return "Cannot pay, not logged in\n".
   *         If the reservation is not found, not under the logged-in user's name, or
   *         is already paid, then return
   *         "Cannot find unpaid reservation [reservationId] under user: [username]\n".
   *         If the user does not have enough money in their account, then return
   *         "User has only [balance] in account but itinerary costs [cost]\n".
   *         For all other errors, return "Failed to pay for reservation [reservationId]\n"
   *
   *         If successful, return "Paid reservation: [reservationId] remaining balance:
   *         [balance]\n" where [balance] is the remaining balance in the user's account.
   *
   * @see #book()
   */
  public final String pay(int reservationId) {
    try {
      return transaction_pay(reservationId);
    } finally {
      checkDanglingTransaction();
    }
  }

  public abstract String transaction_pay(int reservationId);

  /**
   * Prints out reserved itineraries, regardless of their payment status.
   *
   * @return If no user has logged in, then return "Cannot view reservations, not logged in\n" If
   *         the user has no reservations, then return "No reservations found\n" For all other
   *         errors, return "Failed to retrieve reservations\n"
   *
   *         Otherwise return the reservations in the following format:
   *
   *         Reservation [reservation ID] paid: [true or false]:\n [flight 1 under the
   *         reservation]\n [flight 2 under the reservation]\n Reservation [reservation ID] paid:
   *         [true or false]:\n [flight 1 under the reservation]\n [flight 2 under the
   *         reservation]\n ...
   *
   *         Each flight should be printed using the same format as in
   *         the {@code Query.Flight} class.
   *
   * @see Query.Flight#toString()
   */
  public final String reservations() {
    try {
      return transaction_reservations();
    } finally {
      checkDanglingTransaction();
    }
  }

  public abstract String transaction_reservations();

  /**
   * Throw IllegalStateException if transaction not completely complete, rollback.
   *
   */
  protected void checkDanglingTransaction() throws IllegalStateException {
    try {
      try (ResultSet rs = tranCountStatement.executeQuery()) {
        rs.next();
        int count = rs.getInt("tran_count");
        if (count > 0) {
          throw new IllegalStateException(
              "\nTransaction not fully commited/rolledback. Number of transactions currently"
              + " in process: " + count
              + "\nImportant: transactions must committed or rolledback before returning from"
              + " a method.  Example: flight is full; you must conn.rollback() before returning"
              + " the error string.\n");
        }
      } finally {
        conn.setAutoCommit(true);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Database error", e);
    }
  }
}
