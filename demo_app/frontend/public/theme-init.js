// Applies the saved theme before first paint so dark mode never flashes light. A static
// same-origin file loaded as a blocking classic script in <head>, because the CSP allows no
// inline script. Keep in step with ThemeProvider (localStorage key "ui-theme").
(function () {
  let theme = "system";
  try {
    theme = localStorage.getItem("ui-theme") ?? "system";
  } catch {
    // Storage blocked: follow the OS preference.
  }
  const dark =
    theme === "dark" ||
    (theme === "system" && matchMedia("(prefers-color-scheme: dark)").matches);
  document.documentElement.classList.toggle("dark", dark);
})();
