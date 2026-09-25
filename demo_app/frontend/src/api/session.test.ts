import { describe, expect, it } from "vitest";
import { ApiError } from "./client";
import { retryUnlessUnauthorized } from "./session";

describe("retryUnlessUnauthorized", () => {
  it("never retries a 401: the session is gone", () => {
    const unauthorized = new ApiError("unauthorized", "Unauthorized", {
      status: 401,
    });
    expect(retryUnlessUnauthorized(0, unauthorized)).toBe(false);
  });

  it("retries any other failure up to 3 times", () => {
    const unavailable = new ApiError("unavailable", "Network request failed");
    expect(retryUnlessUnauthorized(0, unavailable)).toBe(true);
    expect(retryUnlessUnauthorized(2, unavailable)).toBe(true);
    expect(retryUnlessUnauthorized(3, unavailable)).toBe(false);
  });
});
