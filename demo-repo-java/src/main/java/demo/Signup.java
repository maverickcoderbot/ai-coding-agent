package demo;

import java.util.Map;

public class Signup {

    public static Map<String, String> signup(String email, String password) {
        return Map.of("email", email, "password", password);
    }
}
