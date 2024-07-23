package flightapp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import junit.framework.TestCase;
import org.junit.Test;

public class PasswordUtilsTest extends TestCase {
  public PasswordUtilsTest() { }
  
  @Test
  public void testPasswordsAreUnique() {
    byte[] zhHash = PasswordUtils.saltAndHashPassword("你好");
    byte[] jaHash = PasswordUtils.saltAndHashPassword("こんにちは");

    // Turns out, Chinese != Japanese.
    assertFalse(Arrays.equals(zhHash, jaHash));
  }

  @Test
  public void testPasswordsAreSalted() {
    byte[] hash1 = PasswordUtils.saltAndHashPassword("hi");
    byte[] hash2 = PasswordUtils.saltAndHashPassword("hi");

    // Because we're requiring *salted* password hashes, even though the password is the same
    // the hashes should not be.
    assertFalse(Arrays.equals(hash1, hash2));
  }

  @Test
  public void testCanMatchPlaintextToHash() {
    byte[] hash = PasswordUtils.saltAndHashPassword("howdy");

    // The password hashes aren't the same (see previous test), but our utility function should
    // detect that the underlying password *is* the same.
    assertTrue(PasswordUtils.plaintextMatchesSaltedHash("howdy", hash));
  }

  @Test
  public void testDetectsDifferingPasswords() {
    byte[] hash = PasswordUtils.saltAndHashPassword("你好");

    // Turns out, Chinese != Korean either.
    assertFalse(PasswordUtils.plaintextMatchesSaltedHash("안녕하세요", hash));
  }

  @Test
  public void testSaltLengthsAreConsistent() {
    byte[] salt1 = PasswordUtils.generateSalt();
    byte[] salt2 = PasswordUtils.generateSalt();

    // Students are required to have 16-byte salts.
    assertEquals(16, salt1.length);
    assertEquals(16, salt2.length);
  }

  @Test
  public void testSuccessiveSaltsDiffer() {
    byte[] salt1 = PasswordUtils.generateSalt();
    byte[] salt2 = PasswordUtils.generateSalt();
    byte[] salt3 = PasswordUtils.generateSalt();

    // Requesting multiple salts should give us different values.
    assertFalse(Arrays.equals(salt1, salt2));
    assertFalse(Arrays.equals(salt2, salt3));
    assertFalse(Arrays.equals(salt3, salt1));
  }

  @Test
  public void testSameInputsProduceConsistentOutputs() {
    byte[] fakeSalt = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

    byte[] hash1 = PasswordUtils.hashWithSalt("እው ሰላም ነው", fakeSalt);
    byte[] hash2 = PasswordUtils.hashWithSalt("እው ሰላም ነው", fakeSalt);

    // If both inputs are the same (plaintext AND salt), then we expect the salted hash to
    // also be the same.
    assertArrayEquals(hash1, hash2);
  }

  @Test
  public void testDifferentInputsProduceDifferentOutputs() {
    byte[] salt1 = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    byte[] salt2  = {17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32};

    String amHello = "እው ሰላም ነው";
    String arHello = "أهلا";

    byte[] hash1;
    byte[] hash2;

    hash1 = PasswordUtils.hashWithSalt(amHello, salt1);
    hash2 = PasswordUtils.hashWithSalt(amHello, salt2);
    assertFalse(Arrays.equals(hash1, hash2));

    hash1 = PasswordUtils.hashWithSalt(amHello, salt1);
    hash2 = PasswordUtils.hashWithSalt(arHello, salt1);
    assertFalse(Arrays.equals(hash1, hash2));
  }
}
