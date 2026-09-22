const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

/**
 * Dev-only banner linking to the H2 web console. Gated on Vite's built-in
 * `import.meta.env.DEV` flag (true for `npm run dev`, false for a
 * production build), so this never ships to a real deployment regardless
 * of how the app is hosted — no extra env var to remember to unset.
 */
export function DevToolsBanner() {
  if (!import.meta.env.DEV) {
    return null;
  }

  return (
    <div className="dev-banner">
      <span className="dev-banner-tag">DEV</span>
      <span>
        Inspect the database in the{" "}
        <a href={`${API_BASE_URL}/h2-console`} target="_blank" rel="noreferrer">
          H2 console
        </a>{" "}
        — JDBC URL <code>jdbc:h2:mem:hello-world-auth-app</code>, user <code>sa</code>, no password.
      </span>
    </div>
  );
}
