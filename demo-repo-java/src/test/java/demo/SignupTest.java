package demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SignupTest {

    @Test
    void validSignupReturnsUser() {
        assertEquals("a@b.com", Signup.signup("a@b.com", "secret12").get("email"));
    }

    @Test
    void rejectsBadEmail() {
        assertThrows(IllegalArgumentException.class,
                () -> Signup.signup("not-an-email", "secret12"));
    }

    @Test
    void rejectsShortPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> Signup.signup("a@b.com", "short"));
    }
}
