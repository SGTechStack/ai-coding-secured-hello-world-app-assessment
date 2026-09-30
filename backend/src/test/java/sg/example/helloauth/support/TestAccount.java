package sg.example.helloauth.support;

/** A made-up identity to register with, e.g. {@code testuser1 / testuser1@test.example.com}. */
public record TestAccount(String username, String email, String password) {

    /** The Bootstrap admin that {@code app.admin.*} in the test configuration describes. */
    public static final TestAccount BOOTSTRAP_ADMIN = new TestAccount("testadmin", "testadmin@test.example.com",
            "correct-horse-battery-admin");

    public static TestAccount testUser(int number) {
        return new TestAccount("testuser" + number, "testuser" + number + "@test.example.com",
                "correct-horse-battery-" + number);
    }
}
