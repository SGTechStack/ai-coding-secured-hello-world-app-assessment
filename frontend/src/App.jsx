import { useEffect, useState } from "react";
import { ping } from "./api/client.js";

export default function App() {
  const [status, setStatus] = useState("checking…");

  useEffect(() => {
    ping()
      .then((data) => setStatus(`backend: ${data.status}`))
      .catch(() => setStatus("backend: unreachable"));
  }, []);

  return (
    <main>
      <h1>Hello World Auth</h1>
      <p data-testid="backend-status">{status}</p>
    </main>
  );
}
