import { useState } from "react";
import { login } from "../api/client.js";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Alert, AlertDescription } from "@/components/ui/alert";

export default function LoginForm({ onLoggedIn }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    try {
      const session = await login({ username, password });
      onLoggedIn?.(session);
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <form onSubmit={handleSubmit} aria-label="login" className="flex flex-col gap-3">
      <div className="flex flex-col gap-1">
        <Label htmlFor="login-username">Username</Label>
        <Input
          id="login-username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          name="username"
          autoComplete="username"
        />
      </div>
      <div className="flex flex-col gap-1">
        <Label htmlFor="login-password">Password</Label>
        <Input
          id="login-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          name="password"
          type="password"
          autoComplete="current-password"
        />
      </div>
      {error && (
        <Alert variant="destructive" data-testid="login-error">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
      <Button type="submit" className="w-full">
        Log in
      </Button>
    </form>
  );
}
