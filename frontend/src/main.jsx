import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App.jsx";
import Background from "./components/Background.jsx";
import "./index.css";

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <Background />
    <App />
  </StrictMode>,
);
