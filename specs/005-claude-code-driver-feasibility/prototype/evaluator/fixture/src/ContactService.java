package crm;

import java.util.HashMap;
import java.util.Map;

/** Stores contacts and rejects duplicates by email. */
public class ContactService {
    private final Map<String, String> emailsToNames = new HashMap<>();

    public void create(String name, String email) {
        if (emailsToNames.containsKey(email)) {
            throw new IllegalArgumentException("duplicate email: " + email);
        }
        emailsToNames.put(email, name);
    }
}
