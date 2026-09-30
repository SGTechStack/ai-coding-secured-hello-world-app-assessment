package com.example.hello.support;

import com.example.hello.passwordreset.EmailService;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Captures reset links so integration tests can redeem the token. */
public class RecordingEmailService implements EmailService {

  private static final Pattern TOKEN = Pattern.compile("[?&]token=([A-Za-z0-9_-]+)");

  private final List<String> links = new CopyOnWriteArrayList<>();

  @Override
  public void sendPasswordResetEmail(String toEmail, String resetLink) {
    links.add(resetLink);
  }

  public int sentCount() {
    return links.size();
  }

  public String lastToken() {
    if (links.isEmpty()) {
      throw new IllegalStateException("No reset email was sent");
    }
    Matcher matcher = TOKEN.matcher(links.get(links.size() - 1));
    if (!matcher.find()) {
      throw new IllegalStateException("Reset link has no token: " + links.get(links.size() - 1));
    }
    return matcher.group(1);
  }

  public void clear() {
    links.clear();
  }
}
