import { useEffect, useState } from "react";
import { fetchHealth, type HealthResponse } from "./api/client";

type Status =
  | { kind: "loading" }
  | { kind: "success"; data: HealthResponse }
  | { kind: "error"; message: string };

function App() {
  const [status, setStatus] = useState<Status>({ kind: "loading" });

  useEffect(() => {
    let isMounted = true;

    fetchHealth()
      .then((data) => {
        if (isMounted) setStatus({ kind: "success", data });
      })
      .catch((error: unknown) => {
        if (isMounted) {
          setStatus({
            kind: "error",
            message: error instanceof Error ? error.message : "Unknown error",
          });
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  return (
    <main className="app">
      <h1>Hello World Auth App</h1>
      <p>Frontend origin talking to the backend across origins.</p>

      {status.kind === "loading" && <p role="status">Checking backend health…</p>}

      {status.kind === "success" && (
        <p role="status">
          Backend says: <strong>{status.data.status}</strong> (as of{" "}
          {status.data.timestamp})
        </p>
      )}

      {status.kind === "error" && (
        <p role="alert" style={{ color: "crimson" }}>
          Could not reach backend: {status.message}
        </p>
      )}
    </main>
  );
}

export default App;
