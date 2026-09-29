import { useState } from "react";
import { register } from "../api/client.js";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Alert, AlertDescription } from "@/components/ui/alert";

export default function RegisterForm({ onRegistered }) {
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [done, setDone] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    try {
      const created = await register({ username, email, password });
      setDone(true);
      onRegistered?.(created);
    } catch (err) {
      setError(err.message);
    }
  }

  if (done) {
    return (
      <Alert data-testid="register-success">
        <AlertDescription>Account created for {username}.</AlertDescription>
      </Alert>
    );
  }

  return (
    <form onSubmit={handleSubmit} aria-label="register" className="flex flex-col gap-3">
      <div className="flex flex-col gap-1">
        <Label htmlFor="register-username">Username</Label>
        <Input
          id="register-username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          name="username"
          autoComplete="username"
        />
      </div>
      <div className="flex flex-col gap-1">
        <Label htmlFor="register-email">Email</Label>
        <Input
          id="register-email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          name="email"
          type="email"
          autoComplete="email"
        />
      </div>
      <div className="flex flex-col gap-1">
        <Label htmlFor="register-password">Password</Label>
        <Input
          id="register-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          name="password"
          type="password"
          autoComplete="new-password"
        />
      </div>
      {error && (
        <Alert variant="destructive" data-testid="register-error">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
      <Button type="submit" className="w-full">
        Register
      </Button>
    </form>
  );
}
