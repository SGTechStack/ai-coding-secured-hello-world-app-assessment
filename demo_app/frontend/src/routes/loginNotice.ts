/**
 * The one-shot notices the login page can show, passed as its `notice` search param. The param
 * only selects one of these fixed texts, so a crafted link can never put its own words on the page.
 */
const LOGIN_NOTICES = {
  registered: "Account created. Please log in.",
  "password-updated": "Password updated. Please log in.",
} as const;

export type LoginNotice = keyof typeof LOGIN_NOTICES;

export type LoginSearch = { notice?: LoginNotice };

/**
 * The login route's search params: a known `notice`, or none. The key is always returned, because
 * the router merges this result over the raw params: leaving it out would let any raw value through.
 */
export function parseLoginSearch(search: Record<string, unknown>): LoginSearch {
  const notice = (Object.keys(LOGIN_NOTICES) as LoginNotice[]).find(
    (known) => known === search.notice,
  );
  return { notice };
}

export function loginNoticeText(notice: LoginNotice): string {
  return LOGIN_NOTICES[notice];
}
