package com.sgtechstack.helloauth.passwordreset;

import java.net.URI;

public interface EmailService {

	void sendPasswordResetEmail(String toEmail, String username, URI resetLink);

}
