import { QueryClient } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { retryUnlessUnauthorized } from "./api/session";
import { createAppRouter } from "./router";
import "./styles.css";

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: retryUnlessUnauthorized } },
});
const router = createAppRouter({ queryClient });

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App router={router} queryClient={queryClient} />
  </StrictMode>,
);
