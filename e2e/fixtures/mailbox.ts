import fs from 'node:fs'
import type { Lifecycle } from './lifecycle'

const LINK_LINE = /Password reset link for user '([^']+)': (\S+)/g

function resetLinksFor(lifecycle: Lifecycle, username: string): string[] {
  if (!fs.existsSync(lifecycle.appLog)) return []
  const log = fs.readFileSync(lifecycle.appLog, 'utf8')
  return [...log.matchAll(LINK_LINE)].filter((match) => match[1] === username).map((match) => match[2])
}

/**
 * The test mailbox. In the dev profile the PRD's stub EmailService logs each reset link instead of
 * sending it, so "reading the email" means reading this lifecycle's backend log. Links are issued
 * asynchronously, hence the polling.
 *
 * @param count the number of links the user should have received so far (1 for the first link)
 * @returns the newest link once at least `count` links exist
 */
export async function waitForResetLink(
  lifecycle: Lifecycle,
  username: string,
  { count = 1, timeoutMs = 15_000 }: { count?: number; timeoutMs?: number } = {},
): Promise<string> {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const links = resetLinksFor(lifecycle, username)
    if (links.length >= count) return links[links.length - 1]
    await new Promise((resolve) => setTimeout(resolve, 200))
  }
  throw new Error(`no password reset link #${count} for '${username}' within ${timeoutMs} ms`)
}

/** How many reset links a user has been sent in this lifecycle. */
export function resetLinkCount(lifecycle: Lifecycle, username: string): number {
  return resetLinksFor(lifecycle, username).length
}
