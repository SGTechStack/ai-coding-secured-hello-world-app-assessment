package hello.desk.auth;

public interface EmailService {

    void sendPasswordResetEmail(String toEmail, String username, String resetLink);
}
