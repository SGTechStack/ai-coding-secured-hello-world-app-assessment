import { allure } from 'allure-playwright';

// Checked when a spec loads, so a campaign run without its suite name fails before any test starts.
const campaignSuite = process.env.E2E_CAMPAIGN_SUITE;
if (!campaignSuite) throw new Error('E2E_CAMPAIGN_SUITE is required');

/**
 * Labels an acceptance-criterion test for the campaign report: campaign, feature, story, then the criterion, and the
 * persona acting in it when given.
 */
export async function acceptance(story: string, criterion: string, persona?: string) {
  await allure.parentSuite(campaignSuite!);
  await allure.feature('Feature: Login Flow');
  await allure.suite(story);
  await allure.subSuite(criterion);
  await allure.parameter('e2e_configuration', 'isolated E2E fixture: local profile, fresh in-memory H2');
  if (persona !== undefined) await allure.parameter('persona', persona);
}

/** One reported assertion, with the expected and observed evidence as a nested step. */
export async function check(title: string, expected: string, observed: string, assertion: () => Promise<void>) {
  await allure.step(title, async () => allure.step(`Evidence: expected: ${expected} | observed: ${observed}`, assertion));
}
