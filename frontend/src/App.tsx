import { useState } from "react";

import { Card, CardContent } from "@/common/components/ui/card";
import { Button } from "@/common/components/ui/button";
import { Alert, AlertDescription } from "@/common/components/ui/alert";
import { cn } from "@/common/lib/utils";
import { type LoginResponse } from "@/features/auth/api";
import { useResetTokenFromUrl } from "@/features/auth/hooks/useResetTokenFromUrl";
import { RegistrationForm } from "@/features/auth/components/RegistrationForm";
import { LoginForm } from "@/features/auth/components/LoginForm";
import { ForgotPasswordForm } from "@/features/auth/components/ForgotPasswordForm";
import { ResetPasswordForm } from "@/features/auth/components/ResetPasswordForm";
import { ProtectedGreeting } from "@/features/account/components/ProtectedGreeting";
import { DevToolsBanner } from "@/features/devtools/components/DevToolsBanner";

type View = "login" | "register" | "forgot-password" | "reset-password";

function App() {
  const [loggedInAs, setLoggedInAs] = useState<LoginResponse | null>(null);
  const [resetToken, setResetToken] = useResetTokenFromUrl();
  const [view, setView] = useState<View>(() => (resetToken ? "reset-password" : "login"));
  const [registeredUsername, setRegisteredUsername] = useState<string | null>(null);

  const isAuthView = view === "login" || view === "register";

  return (
    <>
      <DevToolsBanner />
      <main className="flex min-h-screen flex-col items-center px-6 py-12">
        <div className="mb-6 w-full max-w-[34rem] text-center">
          <h1 className="mb-1.5 text-[1.75rem] font-semibold tracking-tight">Hello World Auth App</h1>
          <p className="text-[0.9rem] text-muted-foreground">A secure username/password reference implementation.</p>
        </div>

        <div className={cn("flex w-full max-w-[26rem] flex-col gap-5", loggedInAs && "max-w-6xl")}>
          {loggedInAs && (
            <ProtectedGreeting
              username={loggedInAs.username}
              role={loggedInAs.role}
              onLoggedOut={() => {
                setLoggedInAs(null);
                setView("login");
              }}
            />
          )}

          {!loggedInAs && isAuthView && (
            <Card>
              <CardContent>
                <div className="mb-5 flex gap-1 rounded-lg bg-muted p-[3px]" role="tablist" aria-label="Authentication">
                  <Button
                    type="button"
                    role="tab"
                    aria-selected={view === "login"}
                    variant="ghost"
                    className={cn(
                      "flex-1 rounded-md",
                      view === "login" && "bg-background text-foreground shadow-sm",
                    )}
                    onClick={() => setView("login")}
                  >
                    Log in
                  </Button>
                  <Button
                    type="button"
                    role="tab"
                    aria-selected={view === "register"}
                    variant="ghost"
                    className={cn(
                      "flex-1 rounded-md",
                      view === "register" && "bg-background text-foreground shadow-sm",
                    )}
                    onClick={() => {
                      setRegisteredUsername(null);
                      setView("register");
                    }}
                  >
                    Register
                  </Button>
                </div>

                {view === "login" ? (
                  <>
                    {registeredUsername && (
                      <Alert variant="success" className="mb-4">
                        <AlertDescription>
                          Account <strong>{registeredUsername}</strong> created successfully. Log in below to
                          continue.
                        </AlertDescription>
                      </Alert>
                    )}
                    <LoginForm
                      initialUsername={registeredUsername ?? undefined}
                      onLoginSuccess={(result) => {
                        setRegisteredUsername(null);
                        setLoggedInAs(result);
                      }}
                    />
                    <p className="mt-4 text-center text-[0.85rem] text-muted-foreground">
                      <Button type="button" variant="link" className="h-auto p-0" onClick={() => setView("forgot-password")}>
                        Forgot password?
                      </Button>
                    </p>
                  </>
                ) : (
                  <RegistrationForm
                    onRegistered={(data) => {
                      setRegisteredUsername(data.username);
                      setView("login");
                    }}
                  />
                )}
              </CardContent>
            </Card>
          )}

          {!loggedInAs && view === "forgot-password" && (
            <Card>
              <CardContent>
                <ForgotPasswordForm onBackToLogin={() => setView("login")} onHaveToken={() => setView("reset-password")} />
              </CardContent>
            </Card>
          )}

          {!loggedInAs && view === "reset-password" && (
            <Card>
              <CardContent>
                <ResetPasswordForm
                  initialToken={resetToken ?? ""}
                  onResetComplete={() => {
                    setResetToken(null);
                    setView("login");
                  }}
                />
              </CardContent>
            </Card>
          )}
        </div>
      </main>
    </>
  );
}

export default App;
