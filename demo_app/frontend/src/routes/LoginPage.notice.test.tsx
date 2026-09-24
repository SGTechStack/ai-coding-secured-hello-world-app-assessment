import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { renderApp } from "../test/renderApp";

const REGISTERED = "Account created. Please log in.";

describe("/login one-shot notice", () => {
  it("shows the notice named by the search param, then drops the param from the URL", async () => {
    const { router } = renderApp("/login?notice=registered");

    expect(await screen.findByRole("status")).toHaveTextContent(REGISTERED);
    await waitFor(() => expect(router.state.location.searchStr).toBe(""));
    expect(screen.getByRole("status")).toHaveTextContent(REGISTERED);
  });

  it.each(["<img src=x onerror=alert(1)>", "Account hacked", "toString"])(
    "ignores a notice outside the fixed set (%s)",
    async (notice) => {
      renderApp(`/login?notice=${encodeURIComponent(notice)}`);

      await screen.findByRole("heading", { name: "Log in" });
      expect(screen.queryByRole("status")).not.toBeInTheDocument();
      expect(screen.queryByText(notice)).not.toBeInTheDocument();
      expect(document.querySelector("img")).toBeNull();
    },
  );

  it("shows nothing without the param", async () => {
    renderApp("/login");

    await screen.findByRole("heading", { name: "Log in" });
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("goes away once the user submits the form", async () => {
    const { user } = renderApp("/login?notice=registered");
    await screen.findByRole("status");

    await user.type(screen.getByLabelText("Username"), "johndoe");
    await user.type(screen.getByLabelText("Password"), "wrong-password");
    await user.click(screen.getByRole("button", { name: "Log in" }));

    await waitFor(() =>
      expect(screen.queryByRole("status")).not.toBeInTheDocument(),
    );
  });
});
