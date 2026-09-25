import { Badge } from "@/common/components/ui/badge";

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
    <div className="sticky top-0 z-10 flex flex-wrap items-center justify-center gap-2 border-b border-amber-300 bg-amber-100 px-4 py-2 text-center text-[0.82rem] text-amber-900 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-200">
      <Badge className="bg-amber-900/10 text-[0.68rem] font-bold tracking-wide text-amber-900 dark:bg-amber-200/15 dark:text-amber-200">
        DEV
      </Badge>
      <span>
        Inspect the database in the{" "}
        <a
          href={`${API_BASE_URL}/h2-console`}
          target="_blank"
          rel="noreferrer"
          className="font-semibold underline underline-offset-2"
        >
          H2 console
        </a>{" "}
        — JDBC URL{" "}
        <code className="rounded bg-amber-900/10 px-1 py-0.5 text-[0.78rem] dark:bg-amber-200/10">
          jdbc:h2:mem:hello-world-auth-app
        </code>
        , user <code className="rounded bg-amber-900/10 px-1 py-0.5 text-[0.78rem] dark:bg-amber-200/10">sa</code>,
        no password.
      </span>
    </div>
  );
}
