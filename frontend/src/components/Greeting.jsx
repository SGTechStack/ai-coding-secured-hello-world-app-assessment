import { useEffect, useState } from "react";
import { fetchGreeting } from "../api/client.js";

export default function Greeting() {
  const [message, setMessage] = useState(null);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    fetchGreeting()
      .then((m) => setMessage(m))
      .catch(() => setMessage(null))
      .finally(() => setLoaded(true));
  }, []);

  if (!loaded) {
    return <p data-testid="greeting-loading">Loading…</p>;
  }
  if (message === null) {
    return <p data-testid="greeting-gated">Please log in to see your greeting.</p>;
  }
  return <p data-testid="greeting">{message}</p>;
}
