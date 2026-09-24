import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { renderApp } from "../test/renderApp";

describe("theme toggle", () => {
  afterEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove("dark");
  });

  it("switches between light and dark and remembers the choice", async () => {
    const { user } = renderApp("/login");
    const toggle = await screen.findByRole("button", { name: "Toggle theme" });
    const root = document.documentElement;
    expect(root).not.toHaveClass("dark");

    await user.click(toggle);
    expect(root).toHaveClass("dark");
    expect(localStorage.getItem("ui-theme")).toBe("dark");

    await user.click(toggle);
    expect(root).not.toHaveClass("dark");
    expect(localStorage.getItem("ui-theme")).toBe("light");
  });

  it("restores a saved theme on load", async () => {
    localStorage.setItem("ui-theme", "dark");
    renderApp("/login");

    await screen.findByRole("button", { name: "Toggle theme" });
    expect(document.documentElement).toHaveClass("dark");
  });
});
