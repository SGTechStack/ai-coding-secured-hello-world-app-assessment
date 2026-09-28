import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";
import { App } from "./App";
import { AuthProvider } from "./auth/AuthProvider";
import "./index.css";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // A 401 or 403 is an answer, not a hiccup. Retrying it just delays showing the user what
      // happened, and on the session endpoint it would delay the whole first paint.
      retry: false,
      refetchOnWindowFocus: false,
    },
  },
});

const rootElement = document.getElementById("root");
if (!rootElement) {
  throw new Error("index.html is missing the #root element");
}

createRoot(rootElement).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        {/* Inside the router because the guards it feeds use routing hooks. */}
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
