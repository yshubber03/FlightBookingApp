package flightapp;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Runs queries against a back-end database
 */
public class Query extends QueryAbstract {
  // Canned queries
  // For Clear Method
  private static final String FLIGHT_CAPACITY_SQL = "SELECT capacity FROM Flights WHERE fid = ?";
  private PreparedStatement flightCapacityStmt;

  private static final String CLEAR_RESERVATIONS_SQL = "DELETE FROM Reservations_yshubber WHERE userId IS NOT NULL"; 
  private PreparedStatement clearReservationsStmt; 

  private static final String CLEAR_USERS_SQL = "DELETE FROM Users_yshubber WHERE username IS NOT NULL"; 
  private PreparedStatement clearUsersStmt; 

  //For Create Method
  private static final String CREATE_CUSTOMER_SQL = "INSERT INTO Users_yshubber VALUES(?, ?, ?)";
  private PreparedStatement createCustomerStmt;

  //For Login Method
  private static final String GET_USER_SQL = "SELECT password AS hashedPassword FROM Users_yshubber WHERE username = ?";
  private PreparedStatement getUserStmt;

  private static final String DIRECT_FLIGHT_SQL = "SELECT TOP (?) fid, day_of_month, carrier_id, flight_num, origin_city, dest_city, actual_time, capacity, price " +
                                                  "FROM Flights " +
                                                  "WHERE origin_city = ? AND dest_city = ? AND day_of_month = ? " +
                                                  "ORDER BY actual_time, fid";
  private PreparedStatement directFlightStmt;
  
  private static final String INDIRECT_FLIGHT_SQL = "SELECT TOP (?) f1.fid AS fid1, f1.day_of_month AS day1, f1.carrier_id AS carrier1, f1.flight_num AS flightNum1, f1.origin_city AS origin1, f1.dest_city AS dest1, f1.actual_time AS time1, f1.capacity AS capacity1, f1.price AS price1, " +
                                                   "f2.fid AS fid2, f2.day_of_month AS day2, f2.carrier_id AS carrier2, f2.flight_num AS flightNum2, f2.origin_city AS origin2, f2.dest_city AS dest2, f2.actual_time AS time2, f2.capacity AS capacity2, f2.price AS price2 " +
                                                   "FROM Flights f1, Flights f2 " +
                                                   "WHERE f1.origin_city = ? AND f2.dest_city = ? AND f1.dest_city = f2.origin_city AND f1.day_of_month = ? AND f2.day_of_month = ? AND f1.canceled = 0 AND f2.canceled = 0 " +
                                                   "ORDER BY (f1.actual_time + f2.actual_time), f1.fid, f2.fid";

  private PreparedStatement indirectFlightStmt;

  private static final String INSERT_BOOKING_SQL  = "INSERT INTO Reservations_yshubber (rid, userId, paid, DirectFlightId, IndirectFlightID) VALUES (?, ?, ?, ?, ?)";
  private PreparedStatement insertBookingStmt;

  private static final String PAY_BOOKING_SQL = "SELECT r.userId, r.paid, f1.price + COALESCE(f2.price, 0) AS totalCost FROM Reservations_yshubber r LEFT JOIN Flights f1 ON r.DirectFlightId = f1.fid LEFT JOIN Flights f2 ON r.IndirectFlightId = f2.fid WHERE r.rid = ?";
  private PreparedStatement payBookingStmt;
  private static final String RETRIEVE_RESERVATIONS_SQL = "SELECT r.rid, r.paid, f1.fid AS fid1, f1.day_of_month AS day_of_month1, f1.carrier_id AS carrier_id1, f1.flight_num AS flight_num1, f1.origin_city AS origin_city1, f1.dest_city AS dest_city1, f1.actual_time AS actual_time1, f1.capacity AS capacity1, f1.price AS price1, f2.fid AS fid2, f2.day_of_month AS day_of_month2, f2.carrier_id AS carrier_id2, f2.flight_num AS flight_num2, f2.origin_city AS origin_city2, f2.dest_city AS dest_city2, f2.actual_time AS actual_time2, f2.capacity AS capacity2, f2.price AS price2 FROM Reservations_yshubber r LEFT JOIN Flights f1 ON r.DirectFlightId = f1.fid LEFT JOIN Flights f2 ON r.IndirectFlightId = f2.fid WHERE r.userId = ?";
  private PreparedStatement retrieveReservationsStmt; 
  private static final String UPDATE_PAY_SQL = "UPDATE Reservations_yshubber SET paid = ? WHERE rid = ?"; 
  private PreparedStatement updatePayStmt;
  private static final String UPDATE_USER_BALANCE_SQL = "UPDATE Users_yshubber SET balance = balance + ? WHERE username = ?"; 
  private PreparedStatement updateUserBalanceStmt; 
  private static final String UPDATE_BALANCE_SQL = "SELECT balance FROM Users_yshubber WHERE username = ?"; 
  private PreparedStatement updateBalanceStmt; 
  //change below 
  private static final String SAME_DAY_RESERVATION_SQL = "SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END AS collision FROM Reservations_yshubber r JOIN Flights f1 ON r.DirectFlightId = f1.fid OR r.IndirectFlightId = f1.fid WHERE r.userId = ? AND f1.day_of_month = ?";
  private PreparedStatement reservationCollisionStmt; 

  private static final String GET_BOOKED_COUNT_SQL = "Select Count(*) as num FROM Reservations_yshubber WHERE DirectFlightId = ? or IndirectFlightId = ?"; 
  private PreparedStatement getBookedStmt;

  private static final String GET_DAY_SQL = "SELECT f.day_of_month FROM Reservations_yshubber AS r JOIN (SELECT f.day_of_month, f.fid FROM FLIGHTS AS f) AS f ON (f.fid = r.IndirectFlightId OR f.fid = r.DirectFlightId) WHERE r.userId = ?";

  // "SELECT day_of_month AS day FROM Flights JOIN Reservations_yshubber ON Reservations_yshubber.DirectFlightId = Flights.fid OR Reservations_yshubber.IndirectFlightId = Flights.fid WHERE rid = ?";
  private PreparedStatement getDayStmt; 


  //
  // Instance variables
  //
private List<Itinerary> itineraries = new ArrayList<>();

private String username;

public void setUsername(String username){
  this.username = ""; 

}
private int id; 
public int getId(int id) {
  this.id = id;; 
  return id; 
}

private int rid = 1; 
private boolean loggedIn;
  protected Query() throws SQLException, IOException {
    loggedIn = false; 
    prepareStatements();
  }

  /**
   * Clear the data in any custom tables created.
   * 
   * WARNING! Do not drop any tables and do not clear the flights table.
   */
  public void clearTables() {
    try {
      // delete Reservations_yshubber and Users_yshubber
      clearReservationsStmt.executeUpdate(); 
      clearUsersStmt.executeUpdate();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /*
   * prepare all the SQL statements in this method.
   */
  private void prepareStatements() throws SQLException {
    flightCapacityStmt = conn.prepareStatement(FLIGHT_CAPACITY_SQL);
    //clearTables 
    clearReservationsStmt = conn.prepareStatement(CLEAR_RESERVATIONS_SQL);
    clearUsersStmt = conn.prepareStatement(CLEAR_USERS_SQL);
    //^^end of clearTables
    //create 
    createCustomerStmt = conn.prepareStatement(CREATE_CUSTOMER_SQL); 
    //login
    getUserStmt = conn.prepareStatement(GET_USER_SQL);
    directFlightStmt = conn.prepareStatement(DIRECT_FLIGHT_SQL);
    indirectFlightStmt = conn.prepareStatement(INDIRECT_FLIGHT_SQL);
    insertBookingStmt = conn.prepareStatement(INSERT_BOOKING_SQL); 
    payBookingStmt = conn.prepareStatement(PAY_BOOKING_SQL); 
    retrieveReservationsStmt = conn.prepareStatement(RETRIEVE_RESERVATIONS_SQL); 
    updatePayStmt = conn.prepareStatement(UPDATE_PAY_SQL); 
    updateUserBalanceStmt = conn.prepareStatement(UPDATE_USER_BALANCE_SQL);
    reservationCollisionStmt = conn.prepareStatement(SAME_DAY_RESERVATION_SQL); 
    getDayStmt = conn.prepareStatement(GET_DAY_SQL); 
    updateBalanceStmt  = conn.prepareStatement(UPDATE_BALANCE_SQL); 
    getBookedStmt = conn.prepareStatement(GET_BOOKED_COUNT_SQL);

    // TODO: YOUR CODE HERE
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_login(String username, String password) {
    // TODO: YOUR CODE HERE
    try {
        if(!loggedIn) {
          // receives the username 
            getUserStmt.setString(1, username.toLowerCase());
            // executes the querey above -> gets the password based on username 
            ResultSet resultSet = getUserStmt.executeQuery();
            // if user exists 
            if (resultSet.next()) {
              // receives the hashed password 
                byte[] realPassword = resultSet.getBytes("hashedPassword");
                //checks if the password matches what the user inputted and the realpassword 
                if (PasswordUtils.plaintextMatchesSaltedHash(password, realPassword)) {
                    loggedIn = true;
                    this.username = username;
                    
                    conn.commit();
                    conn.setAutoCommit(true);

                    return "Logged in as " + username + "\n";

                } else {
                  //if passwords dont match 
                    conn.rollback();
                    conn.setAutoCommit(true);
                    return "Login failed\n";
                }
            } else {
              //if user doesnt exist 
                conn.rollback();
                conn.setAutoCommit(true);
                return "Login failed\n";
            }
        } 
        // if the user is already logged in 
        else {
            return "User already logged in\n";
        }
    }  
    catch(SQLException e) {
      try {
        e.printStackTrace();
        conn.rollback();
        conn.setAutoCommit(true);
        if (isDeadlock(e)) {
          return transaction_login(username, password);
        }
        else {
          return "Login failed\n";
        }
      }
      catch(SQLException ex) {
        ex.printStackTrace();
        return "Login failed\n";
      }
    }
  }
  /* See QueryAbstract.java for javadoc */
  public String transaction_createCustomer(String username, String password, int initAmount) {
    try{
      if (initAmount < 0) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "Failed to create user\n";
    }
      createCustomerStmt.clearParameters();
      String inputUser = username.toLowerCase();
      createCustomerStmt.setString(1, inputUser);
      byte[] newPassword = PasswordUtils.saltAndHashPassword(password);
      createCustomerStmt.setBytes(2, newPassword);
      createCustomerStmt.setInt(3, initAmount);
      createCustomerStmt.executeUpdate();

      conn.commit();
      conn.setAutoCommit(true);
      return "Created user " + username + "\n"; 

    }
    catch(SQLException e) {
      try {
        e.printStackTrace();
        conn.rollback();
        conn.setAutoCommit(true);
        if (isDeadlock(e)) {
          return transaction_createCustomer(username, password, initAmount);
        }
        else {
          return "Failed to create user\n";
        }
      }
      catch(SQLException ex) {
        ex.printStackTrace();
        return "Failed to create user\n";
      }
    }
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_search(String originCity, String destinationCity, 
                                   boolean directFlight, int dayOfMonth,
                                   int numberOfItineraries) {
    StringBuffer sb = new StringBuffer();
    try {
        // Direct flights
        directFlightStmt.setInt(1, numberOfItineraries);
        directFlightStmt.setString(2, originCity);
        directFlightStmt.setString(3, destinationCity);
        directFlightStmt.setInt(4, dayOfMonth);
        
        ResultSet directResults = directFlightStmt.executeQuery();

        // itinerarates through the direct flights 
        while(directResults.next()){
            // Retrieves the flight details 
            int dayMonth = directResults.getInt("day_of_month");
            String carrierId = directResults.getString("carrier_id");
            String flightNum = directResults.getString("flight_num");
            String cityOrgin = directResults.getString("origin_city");
            String destCity = directResults.getString("dest_city");
            int time = directResults.getInt("actual_time");
            int capacity = directResults.getInt("capacity");
            int price = directResults.getInt("price");
            Flight flight = new Flight(directResults.getInt("fid"), dayMonth, carrierId, flightNum, cityOrgin, destCity, time, capacity, price);
            itineraries.add(new Itinerary(flight));
        }
        directResults.close();
      
        // Indirect flights if needed
        if (!directFlight && itineraries.size() < numberOfItineraries) {
            int indirectToGet = numberOfItineraries - itineraries.size();
             // Set parameters for the indirect flight query
            indirectFlightStmt.setInt(1, indirectToGet);
            indirectFlightStmt.setString(2, originCity);
            indirectFlightStmt.setString(3, destinationCity);
            indirectFlightStmt.setInt(4, dayOfMonth);
            indirectFlightStmt.setInt(5, dayOfMonth);
            ResultSet indirectResults = indirectFlightStmt.executeQuery();
          
            while(indirectResults.next()){
                Flight flightOne = new Flight(indirectResults.getInt("fid1"), 
                                              indirectResults.getInt("day1"), 
                                              indirectResults.getString("carrier1"), 
                                              indirectResults.getString("flightNum1"),
                                              indirectResults.getString("origin1"),
                                              indirectResults.getString("dest1"),
                                              indirectResults.getInt("time1"),
                                              indirectResults.getInt("capacity1"),
                                              indirectResults.getInt("price1"));
                Flight flightTwo = new Flight(indirectResults.getInt("fid2"), 
                                              indirectResults.getInt("day2"), 
                                              indirectResults.getString("carrier2"), 
                                              indirectResults.getString("flightNum2"),
                                              indirectResults.getString("origin2"),
                                              indirectResults.getString("dest2"),
                                              indirectResults.getInt("time2"),
                                              indirectResults.getInt("capacity2"),
                                              indirectResults.getInt("price2"));
                itineraries.add(new Itinerary(flightOne, flightTwo));
            }
            indirectResults.close();
        }

        // Sort itineraries
        Collections.sort(itineraries);

        // Build result string
        if(itineraries.isEmpty()){
            return "No flights match your selection\n";
        } else {
            for(int c = 0; c < itineraries.size(); c++){
                sb.append("Itinerary " + c + ": " + itineraries.get(c).toString());
            }
        }
    } catch (SQLException e) {
        e.printStackTrace();
        return "Failed to search\n";
    }
    return sb.toString();
}


  /* See QueryAbstract.java for javadoc */
  public String transaction_book(int itineraryId) {
    try {
      //Not logged in 
    if (!loggedIn) {
          return "Cannot book reservations, not logged in\n";
    }
      // Trying to book with invalid ID 
    if (itineraries.isEmpty() || itineraryId > itineraries.size()) {
        return "No such itinerary " + itineraryId + "\n";
    }
    conn.setAutoCommit(false);

        
    // Retrieve all existing reservations for the user
    retrieveReservationsStmt.clearParameters();
    retrieveReservationsStmt.setString(1, username);
    ResultSet reservationsResultSet = retrieveReservationsStmt.executeQuery();

    // find all days of reservations
    getDayStmt.setString(1, username);
    ResultSet resultSet = getDayStmt.executeQuery();

    while (resultSet.next()) {
      int day = resultSet.getInt("day_of_month");
      if (itineraries.get(itineraryId).day == day) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "You cannot book two flights in the same day\n";
        }
      }
        
      Itinerary itinerararies = itineraries.get(itineraryId);

      if(itinerararies.flightOne.capacity ==0 || ((!(itinerararies.directFlight)) && itinerararies.flightTwo.capacity == 0)){
        conn.rollback();
        conn.setAutoCommit(true);
        return "Booking failed\n";
      }
      // // check capacity of flights
      // // check for direct
      //  int capacity = itinerararies.flightOne.capacity; 
      //  int flightNumberCheck = itinerararies.flightOne.fid; 
      //  getBookedStmt.setInt(1, flightNumberCheck);
      //  getBookedStmt.setInt(2, flightNumberCheck); 
      //  ResultSet cut = getBookedStmt.executeQuery(); 
      //  if(cut.next()){
      //     if(capacity - cut.getInt(("num")) == 0){
      //       conn.rollback();
      //       conn.setAutoCommit(true);
      //       return "Booking failed\n"; 
      //    }
      //  }

      //  // check capacity of flights
      // // check for un-direct
      // int capacityTwo = itinerararies.flightTwo.capacity; 
      // int flightNumberTwoCheck = itinerararies.flightTwo.fid; 
      //  getBookedStmt.setInt(1, flightNumberTwoCheck);
      //  getBookedStmt.setInt(2, flightNumberTwoCheck); 
      //  ResultSet cutTwo = getBookedStmt.executeQuery(); 
      //  if(cutTwo.next()){
      //     if(capacityTwo - cutTwo.getInt(("num")) == 0){
      //       conn.rollback();
      //       conn.setAutoCommit(true);
      //       return "Booking failed\n"; 
      //    }
      //  }

      if (itinerararies.flightTwo != null) {
        int flightId1 = itinerararies.flightOne.fid;
        int flightId2 = itinerararies.flightTwo.fid;
        insertBookingStmt.setString(1, Integer.toString (this.rid));
        insertBookingStmt.setString(2, username);
        insertBookingStmt.setInt(3, 0);
        insertBookingStmt.setInt(4, flightId1);
        insertBookingStmt.setInt(5, flightId2);
        this.rid++;
      } else {
        int fid = itinerararies.flightOne.fid;
        insertBookingStmt.setString(1, Integer.toString (this.rid));
        insertBookingStmt.setString(2, username);
        insertBookingStmt.setInt(3, 0);
        insertBookingStmt.setInt(4, fid);
        insertBookingStmt.setNull(5, Types.INTEGER);
        this.rid++;
      }
      insertBookingStmt.executeUpdate();
      conn.commit();
      conn.setAutoCommit(true);
      return "Booked flight(s), reservation ID: " + (this.rid - 1) + "\n";
  } catch(SQLException e) {
    try {
      e.printStackTrace();
      conn.rollback();
      conn.setAutoCommit(true);
      if (isDeadlock(e)) {
        return transaction_book(itineraryId);
      }
      else {
        return "Booking failed\n";
      }
    }
    catch(SQLException ex) {
      ex.printStackTrace();
      return "Booking failed\n";
    }
  }
}

  /* See QueryAbstract.java for javadoc */
  public String transaction_pay(int reservationId) {
    try {
      // Base case: Check if the user is logged in
      if (!loggedIn) {
          return "Cannot pay, not logged in\n";
      }
      // Base case: Check if the reservation ID is valid
      if (reservationId <= 0 || reservationId >= rid) {
          return "Cannot find unpaid reservation " + reservationId + " under user: " + username + "\n";
      }
      conn.setAutoCommit(false);
      
      // Execute the payment statement
      payBookingStmt.setInt(1, reservationId);
      ResultSet resultSet = payBookingStmt.executeQuery();
      resultSet.next();
     
          String userId = resultSet.getString("userId");
          int paid = resultSet.getInt("paid");
          int totalCost = resultSet.getInt("totalCost");

          // Check if the reservation belongs to the logged-in user and is unpaid
          if (!userId.equals(username) || paid == 1) {
              conn.rollback();
              conn.setAutoCommit(true);
              return "Cannot find unpaid reservation " + reservationId + " under user: " + username + "\n";
          }

          // Update the user's balance
          updateBalanceStmt.setString(1, username);
          ResultSet balanceResultSet = updateBalanceStmt.executeQuery();

          if (balanceResultSet.next()) {
              int balance = balanceResultSet.getInt("balance");
              if (totalCost > balance) {
                  conn.rollback();
                  conn.setAutoCommit(true);
                  return "User has only " + balance + " in account but itinerary costs " + totalCost + "\n";
              }

              // Update the reservation status
              updateReservationStatus(reservationId);

              // Deduct the total cost from the user's balance
              balance -= totalCost;
              updateUserBalanceStmt.setInt(1, totalCost);
              updateUserBalanceStmt.setString(2, username);
              updateUserBalanceStmt.executeUpdate();
              updateUserBalanceStmt.close();
              conn.commit();
              conn.setAutoCommit(true);
              return "Paid reservation: " + reservationId + " remaining balance: " + balance + "\n";
          } else {
              conn.rollback();
              conn.setAutoCommit(true);
              return "Failed to retrieve user balance\n";
          }
    
    }
    catch(SQLException e) {
      try {
        e.printStackTrace();
        conn.rollback();
        conn.setAutoCommit(true);
        if (isDeadlock(e)) {
          return transaction_pay(reservationId);
        }
        else {
          return "Failed to pay for reservation " + reservationId + " \n";  
        }
      }
      catch(SQLException ex) {
        ex.printStackTrace();
        return "Failed to pay for reservation " + reservationId + " \n";  
      }
    }
  }

//HELPER METHOD
  private int getDayOfNewReservation(int reservationId) {
    try {
      getDayStmt.setInt(1, reservationId);
      ResultSet resultSet = getDayStmt.executeQuery();
      if (resultSet.next()) {
          return resultSet.getInt("day");
      }
      return -1; 
  } catch (SQLException e) {
      e.printStackTrace();
      return -1;
  }
}
//HELPER METHOD 
  private void updateReservationStatus(int reservationId) {
    try {
        updatePayStmt.setInt(1, 1); // Set paid status to 1 (indicating paid)
        updatePayStmt.setString(2, Integer.toString(reservationId)); // Set the reservation ID
        updatePayStmt.executeUpdate();
        updatePayStmt.close();
    } catch (SQLException e) {
        e.printStackTrace();
    }
}
  /* See QueryAbstract.java for javadoc */
  public String transaction_reservations() {
    try {
      if (!loggedIn) {
          return "Cannot view reservations, not logged in\n";
      }
      conn.setAutoCommit(false);

      retrieveReservationsStmt.setString(1, username);
      ResultSet resultSet = retrieveReservationsStmt.executeQuery();
      if (!resultSet.isBeforeFirst()) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "No reservations found\n";
      }


    StringBuilder sb = new StringBuilder();

    // Iterate through the reservations
    while (resultSet.next()) {
        String reservationId = resultSet.getString("rid");
        boolean paid = resultSet.getInt("paid") == 1;
        Flight flight1 = createFlightFromResultSet(resultSet, 1);
        Flight flight2 = createFlightFromResultSet(resultSet, 2);

        // Append reservation details to the string builder
        sb.append("Reservation ").append(reservationId).append(" paid: ").append(paid).append(":\n");
        sb.append(flight1.toString()).append("\n");
        if (flight2 != null) {
            sb.append(flight2.toString()).append("\n");
        }
    }

    // Close the result set and statement
    resultSet.close();
    retrieveReservationsStmt.close();

    // Return the formatted string
    conn.commit();
    conn.setAutoCommit(true);
    return sb.toString();
}
    
catch(SQLException e) {
  try {
    e.printStackTrace();
    conn.rollback();
    conn.setAutoCommit(true);
    if (isDeadlock(e)) {
      return transaction_reservations();
    }
    else {
      return "Failed to retrieve reservations\n";
    }
  }
  catch(SQLException ex) {
    ex.printStackTrace();
    return "Failed to retrieve reservations\n";
  }
}
}


  private Flight createFlightFromResultSet(ResultSet resultSet, int flightNumber) throws SQLException {
    if (resultSet.getObject("fid" + flightNumber) == null) {
        return null;
    }
    return new Flight(
            resultSet.getInt("fid" + flightNumber),
            resultSet.getInt("day_of_month" + flightNumber),
            resultSet.getString("carrier_id" + flightNumber),
            resultSet.getString("flight_num" + flightNumber),
            resultSet.getString("origin_city" + flightNumber),
            resultSet.getString("dest_city" + flightNumber),
            resultSet.getInt("actual_time" + flightNumber),
            resultSet.getInt("capacity" + flightNumber),
            resultSet.getInt("price" + flightNumber)
    );
}

  /**
   * Utility function to determine whether an error was caused by a deadlock
   */
  private static boolean isDeadlock(SQLException e) {
    return e.getErrorCode() == 1205;
  }

  /**
   * A class to store information about a single flight
   *
   * TODO(hctang): move this into QueryAbstract
   */
  class Flight {
    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public String flightNum;
    public String originCity;
    public String destCity;
    public int time;
    public int capacity;
    public int price;

    Flight(int id, int day, String carrier, String fnum, String origin, String dest, int tm,
           int cap, int pri) {
      fid = id;
      dayOfMonth = day;
      carrierId = carrier;
      flightNum = fnum;
      originCity = origin;
      destCity = dest;
      time = tm;
      capacity = cap;
      price = pri;
    }
    
    @Override
    public String toString() {
      return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId + " Number: "
          + flightNum + " Origin: " + originCity + " Dest: " + destCity + " Duration: " + time
          + " Capacity: " + capacity + " Price: " + price;
    }
  }
  
 public class Itinerary implements Comparable<Itinerary> {
    private final boolean directFlight;
    private final int time;
    private final Flight flightOne;
    private final Flight flightTwo;
    private int day; 

    public Itinerary(Flight flight) {
        this.directFlight = true;
        this.time = flight.time; 
        this.flightOne = flight;
        this.flightTwo = null;
        this.day = flight.dayOfMonth;
    }

    public int getDay() {
      return this.day;
    }

    public Itinerary(Flight flightOne, Flight flightTwo) {
        this.directFlight = false;
        this.time = flightOne.time + flightTwo.time;
        this.flightOne = flightOne;
        this.flightTwo = flightTwo;
        this.day = flightOne.dayOfMonth;
    }

    public boolean isDirectFlight() {
        return directFlight;
    }

    public int getTime() {
        return time;
    }

    public Flight getFlightOne() {
        return flightOne;
    }

    public Flight getFlightTwo() {
        return flightTwo;
    }

    @Override
    public String toString() {
        if (directFlight) {
            return "1 flight(s), " + time + " minutes\n" + flightOne.toString() + "\n";
        } else {
            return "2 flight(s), " + time + " minutes\n" + flightOne.toString() + "\n" + flightTwo.toString() + "\n";
        }
    }

    @Override
    public int compareTo(Itinerary other) {
        if (time == other.time) {
            if (Objects.equals(flightOne.fid, other.flightOne.fid)) {
                if (directFlight && !other.directFlight) {
                    return 1;
                } else if (!directFlight && other.directFlight) {
                    return -1;
                }
                return flightTwo.fid - other.flightTwo.fid;
            }
            return flightOne.fid - other.flightOne.fid;
        } else {
            return time - other.time;
        }
    }
}
}


