# Ticket 32 dedup (working file)

## Map
| key | action | target | reason |
|---|---|---|---|
| S01-01 | merge | S16-64 | 01:144 CSP example ≡ 16:401 §5:498 other-headers row (as-9 no-unsafe facet pulled in via Edits) |
| S01-02 | merge | S16-63 | 01:144 HSTS max-age ≡ 16:401 HSTS secure-only |
| S01-03 | merge | S08-22 | 01:144 session timeout value ≡ 08:769 idle timeout (≤ 15 min bound pulled in via Edits) |
| S01-04 | merge | S09-01 | 01:144 per-IP 429 at N+1 (amended by 16) ≡ 09:178 |
| S01-05 | merge | S09-03 | 01:144 per-account 429 on mixed traffic ≡ 09:183 |
| S01-06 | merge | S06-06 | 01:144 generic error body ≡ 06:301 /error producer |
| S01-07 | merge | S06-05 | 01:306 403 on role violation ≡ 06:300 AccessDeniedHandler ACCESS_DENIED |
| S02-01 | merge | S09-07 | 02:198 observation window ≡ 09:702 staleness reset |
| S02-02 | merge | S07-03 | 02:342 ≡ 07:465 MAX_BYTES rule |
| S02-03 | merge | S20-03 | 02:395 cookie attributes ≡ 20:159 raw Set-Cookie |
| S03-01 | merge | S13-02 | 03:394 event schema ≡ 13:500 catalogue consistency |
| S03-02 | merge | S13-14 | 03:394 C2 ≡ 13:566 unresolved half; resolved half inverted by 13 |
| S03-03 | merge | S13-09 | 03:394 C5 ≡ 13:561 key absence |
| S03-04 | merge | S13-01 | 03:467 ≡ 13:292 first-implementation test |
| S03-05 | merge | S13-17 | 03:635 ≡ 13:603 retention |
| S03-06 | merge | S27-02 | 03:691 ≡ 27:208 baggage-off test |
| S04-01 | keep |  |  |
| S04-02 | merge | S12-13 | 04:225 tombstone username ≡ 12:290 tombstone blocks both identifiers |
| S04-03 | merge | S04-01 | 04:225 duplicate email ≡ 04:224 duplicate username (one USER_EXISTS test) |
| S04-04 | retire | 06:481 | No generic user-update endpoint exists (11:209–218), so Standard §5:473's immutability case is N/A by construction. |
| S04-05 | retire | 06:481 | No generic user-update endpoint exists (11:209–218), so there is no path to change a password through it. |
| S04-06 | retire | 06:481 | 06:481 records §5:473's "lock via generic update" half as N/A by construction. |
| S04-07 | keep |  |  |
| S04-08 | merge | S04-07 | 04:227 self-delete ≡ actor ≠ subject guard test |
| S04-09 | merge | S04-07 | 04:227 self-unlock ≡ actor ≠ subject guard test |
| S04-10 | keep |  |  |
| S04-11 | merge | S04-10 | 04:228 deletedById ≡ tombstone-write test |
| S04-12 | merge | S08-14,S08-15 | 04:230 ≡ 08:211 disable and 08:212 role-change replay rows |
| S04-13 | merge | S11-13 | 04:231 forced-change allowlist ≡ 11:967 (parameterised via Edits) |
| S04-14 | merge | S16-63,S16-64 | 04:299 headers ≡ 16:401 §5:498 rows |
| S04-15 | merge | S08-06 | 04:299 no XSRF-TOKEN cookie ≡ 08:402 |
| S04-16 | merge | S05-05 | 04:300 session id rotation ≡ 05:606 Route C rotation |
| S04-17 | merge | S16-74,S16-55 | 04:300 lock at threshold and auto-lift ≡ 16:547 Story 3 (iii) and 16:545 Story 3 (i) |
| S04-18 | merge | S08-08 | 04:301 absolute timeout ≡ 08:449 |
| S04-19 | retire | 23:291 | Role hierarchy is out of scope; ticket 23 asserts no RoleHierarchy bean exists instead. |
| S04-20 | merge | S11-12 | 04:301 YAML-only role change ≡ 11:965 matrix change on next load |
| S04-21 | keep |  |  |
| S04-22 | merge | S11-14 | 04:302 IDOR bounded to self ≡ 11:968 / Std §5:479 |
| S04-23 | merge | S11-04 | 04:302 no credential fields ≡ 11:418 API-wide absence |
| S04-24 | merge | S16-58 | 04:303 recipe plaintext password replaced by reset token (10); ≡ 16:384 canary scan |
| S04-25 | merge | S07-05 | 04:304 history rotation ≡ 07:465 history-length boundary |
| S04-26 | merge | S09-10 | ruling 2: 04:305 timing ≡ 09§R-5 matches() count |
| S04-27 | merge | S20-05,S14-02 | 04:524 SPA CSP ≡ 20:227 header/meta and 14:98 zero violations |
| S05-01 | keep |  |  |
| S05-02 | keep |  |  |
| S05-03 | merge | S08-07,S08-03 | 05:413 ≡ 08:449 max-one-session and 08:240 PRINCIPAL_NAME |
| S05-04 | merge | S08-08 | 05 risk 3 (restated on the clock, 16 §9) ≡ 08:449 absolute lifetime |
| S05-05 | keep |  |  |
| S05-06 | keep |  |  |
| S05-07 | merge | S20-04 | 05:716 ≡ 20:173 profile-scoped cookie test (rule 12) |
| S05-08 | merge | S08-06 | 05:722 ≡ 08:402 no CSRF cookie |
| S05-09 | keep |  |  |
| S05-10 | keep |  |  |
| S05-11 | merge | S09-03 | 05:1180 ≡ 09:183 429-not-401 |
| S05-12 | merge | S09-02 | 05:1186 ≡ 09:178 per-account throw leaves counter |
| S05-13 | merge | S09-04 | 05:1192 ≡ 09:397 |
| S05-14 | merge | S16-53 | 05:1205 ≡ 16:533 clock ban |
| S06-01 | keep |  |  |
| S06-02 | keep |  |  |
| S06-03 | keep |  |  |
| S06-04 | keep |  |  |
| S06-05 | keep |  |  |
| S06-06 | keep |  |  |
| S06-07 | keep |  |  |
| S06-08 | keep |  |  |
| S07-01 | keep |  |  |
| S07-02 | keep |  |  |
| S07-03 | keep |  |  |
| S07-04 | keep |  |  |
| S07-05 | keep |  |  |
| S07-06 | merge | S10-05 | 07:551 ≡ 10:594 canonicaliser never on passwords |
| S08-01 | merge | S20-03 | 08:78 ≡ 20:159 SameSite=Strict raw Set-Cookie |
| S08-02 | merge | S20-04 | 08:82 ≡ 20:173 profile-scoped cookie test (rule 12) |
| S08-03 | keep |  |  |
| S08-04 | keep |  |  |
| S08-05 | keep |  |  |
| S08-06 | keep |  |  |
| S08-07 | keep |  |  |
| S08-08 | keep |  |  |
| S08-09 | keep |  |  |
| S08-10 | keep |  |  |
| S08-11 | keep |  |  |
| S08-12 | keep |  |  |
| S08-13 | keep |  |  |
| S08-14 | keep |  |  |
| S08-15 | keep |  |  |
| S08-16 | keep |  |  |
| S08-17 | keep |  |  |
| S08-18 | keep |  |  |
| S08-19 | keep |  |  |
| S08-20 | keep |  |  |
| S08-21 | keep |  |  |
| S08-22 | keep |  |  |
| S08-23 | merge | S29-15 | ruling 8: 08:769 ≡ 29:458 dedicated cron-enabled test |
| S10-01 | keep |  |  |
| S10-02 | keep |  |  |
| S10-03 | keep |  |  |
| S10-04 | keep |  |  |
| S10-05 | keep |  |  |
| S10-06 | keep |  |  |
| S10-07 | keep |  |  |
| S10-08 | keep |  |  |
| S10-09 | keep |  |  |
| S10-10 | keep |  |  |
| S10-11 | merge | S09-16 | 10:760 ≡ 09§R-6 ArchUnit |
| S09-01 | keep |  |  |
| S09-02 | keep |  |  |
| S09-03 | keep |  |  |
| S09-04 | keep |  |  |
| S09-05 | keep |  |  |
| S09-06 | keep |  |  |
| S09-07 | keep |  |  |
| S09-08 | keep |  |  |
| S09-09 | keep |  |  |
| S09-10 | keep |  |  |
| S09-11 | merge | S09-10 | ruling 2: 16 §5 the matches() count catches a UserCache hit |
| S09-12 | keep |  |  |
| S09-13 | keep |  |  |
| S09-14 | keep |  |  |
| S09-15 | keep |  |  |
| S09-16 | keep |  |  |
| S09-17 | merge | S08-09 | 09§R-7 ≡ 08:694 reconciliation sweep |
| S09-18 | keep |  |  |
| S09-19 | keep |  |  |
| S09-20 | keep |  |  |
| S09-21 | keep |  |  |
| S09-22 | keep |  |  |
| S09-23 | keep |  |  |
| S09-24 | keep |  |  |
| S09-25 | keep |  |  |
| S09-26 | keep |  |  |
| S09-27 | keep |  |  |
| S09-28 | keep |  |  |
| S09-29 | keep |  |  |
| S09-30 | keep |  |  |
| S16-01 | merge | S06-01 | ruling 3: 16:30 ≡ 06:175 |
| S16-02 | merge | S06-03,S06-04,S06-05,S06-06 | ruling 3: 16:36 ≡ 06:299–301 producers |
| S16-03 | merge | S06-07 | ruling 3: 16:36 ≡ 06:303 no sendError |
| S16-04 | keep |  |  |
| S16-05 | merge | S20-05 | 16:52 ≡ 20:227 document CSP under vite preview |
| S16-06 | merge | S20-03 | 16:57 ≡ 20:159 SameSite=Strict raw Set-Cookie |
| S16-07 | merge | S20-04 | 16:60 ≡ 20:173 profile-scoped cookie name |
| S16-08 | merge | S20-01 | 16:63 ≡ 20:141 CORS allow-listed preflight |
| S16-09 | merge | S20-02 | 16:63 ≡ 20:143 CORS non-allow-listed |
| S16-10 | keep |  |  |
| S16-11 | merge | S25-01,S25-02 | 16:173 ≡ 25:664 jar and bundle assertions |
| S16-12 | merge | S25-04 | 16:188 ≡ 25:491 drift gate |
| S16-13 | retire | 25:956 | No runner token exists (28 reads the password from stdin); ASVS 6.4.1 (L1) single-use limb has no subject. |
| S16-14 | merge | S28-10 | ruling 1: 16:207 ≡ 28 test 9 runner emits no secret |
| S16-15 | merge | S25-03 | ruling 7: 16:209 ≡ 25:415 reserved-name refusal |
| S16-16 | merge | S11-08 | ruling 5: 15 #1 ≡ 11:888 factor matrix |
| S16-17 | merge | S23-09 | ruling 5: 15 #2 ≡ 23:1144 tier-2 lock order |
| S16-18 | merge | S26-05 | 16:236 (15 #3) ≡ 26:510 miss budget on every route |
| S16-19 | merge | S27-03,S27-04,S27-05,S27-06 | ruling 4: 15 row 4 restated by 27:256 |
| S16-20 | merge | S28-09 | ruling 6: 16 row 5 argument gating ≡ 28 test 8 |
| S16-21 | keep |  |  |
| S16-22 | merge | S15-07 | 16:240 ≡ 15:190 TM-06 |
| S16-23 | merge | S15-08 | 16:241 ≡ 15:193 TM-07 |
| S16-24 | merge | S28-01 | ruling 6: 16:261 ≡ 28 test 1 |
| S16-25 | merge | S28-02 | ruling 6: 16:262 ≡ 28 test 2 |
| S16-26 | merge | S28-03 | ruling 6: 16:263 ≡ 28 test 3 |
| S16-27 | merge | S28-04 | ruling 6: 16:264 ≡ 28 test 4 |
| S16-28 | merge | S28-05 | ruling 6: 16:265 ≡ 28 test 5 (refusals) |
| S16-29 | merge | S28-06 | ruling 6: 16:265 ≡ 28:245 lost-authenticator admission |
| S16-30 | merge | S28-07 | ruling 6: 16:268 ≡ 28 test 6 |
| S16-31 | merge | S28-08 | ruling 6: 16:270 ≡ 28 test 7 |
| S16-32 | merge | S28-09 | ruling 6: 16:273 ≡ 28 test 8 |
| S16-33 | merge | S28-10 | ruling 1: 16:275 ≡ 28 test 9 |
| S16-34 | merge | S28-11 | ruling 6: 16:277 ≡ 28 test 10 |
| S16-35 | merge | S28-12 | ruling 6: 16:278 ≡ 28 test 11 |
| S16-36 | merge | S24-21,S24-22,S24-23 | ruling 6: 16 §9 28 test 12 duplicates 24's validator entries |
| S16-37 | merge | S29-01 | ruling 8: 16:293 ≡ 29 §9 test 1 |
| S16-38 | merge | S29-02 | ruling 8: 16:295 ≡ 29 §9 test 2 |
| S16-39 | merge | S29-05 | ruling 8: 16:297 ≡ 29 §9 test 5 |
| S16-40 | merge | S29-10 | ruling 8: 16:298 ≡ 29 §9 test 10 |
| S16-41 | merge | S31-11 | 16:310 ≡ 31:345 ladder startup floor |
| S16-42 | merge | S30-01 | ruling 7: 16:321 ≡ 30:195 |
| S16-43 | merge | S30-02 | ruling 7: 16:322 ≡ 30:196 |
| S16-44 | merge | S30-03 | ruling 7: 16:323 ≡ 30:197 |
| S16-45 | merge | S30-04 | ruling 7: 16:324 ≡ 30:198 |
| S16-46 | merge | S30-05 | ruling 7: 16:328 ≡ 30:201 |
| S16-47 | merge | S30-06 | ruling 7: 16:329 ≡ 30:202 |
| S16-48 | merge | S30-07 | ruling 7: 16:331 ≡ 30:203 |
| S16-49 | merge | S30-08 | ruling 7: 16:334 ≡ 30:206 |
| S16-50 | keep |  |  |
| S16-51 | keep |  |  |
| S16-52 | keep |  |  |
| S16-53 | keep |  |  |
| S16-54 | keep |  |  |
| S16-55 | keep |  |  |
| S16-56 | keep |  |  |
| S16-57 | merge | S08-10,S08-11,S08-12,S08-13,S08-14,S08-15,S08-16,S08-17,S08-18,S08-19,S08-20,S08-21 | 16 §5 session replay ≡ 08:761 per-trigger rows |
| S16-58 | keep |  |  |
| S16-59 | merge | S14-02 | 16:597 ≡ 14:656 zero securitypolicyviolation (rule 12) |
| S16-60 | merge | S11-09 | 16:561 §5:424 ≡ 11:957 |
| S16-61 | keep |  |  |
| S16-62 | merge | S11-11 | 16:562 / 32:59 ≡ 11:961 §5:425 denyAll 403 |
| S16-63 | keep |  |  |
| S16-64 | keep |  |  |
| S16-65 | merge | S29-15 | ruling 8: 16:522 ≡ 29:458 cron-enabled test |
| S16-66 | keep |  |  |
| S16-67 | merge | S06-08 | 16:605 ≡ 06:484 enum-schema contract test |
| S16-68 | keep |  |  |
| S16-69 | merge | S29-14 | ruling 8: 16:450 ≡ 29:457 cron binding |
| S16-70 | merge | S31-08 | 16:558 merged unparseable test ≡ 31:341 |
| S16-71 | keep |  |  |
| S16-72 | keep |  |  |
| S16-73 | keep |  |  |
| S16-74 | keep |  |  |
| S16-75 | merge | S11-13 | 16:566 §5:485 ≡ 11:967 |
| S16-76 | merge | S11-14 | 16:566 §5:479 ≡ 11:968 |
| S16-77 | merge | S08-04 | 16:598 ≡ 08:760 Clear-Site-Data browser assertion |
| S16-78 | keep |  |  |
| S16-79 | keep |  |  |
| S16-80 | keep |  |  |
| S16-81 | keep |  |  |
| S32-01 | merge | S11-10 | 32:58 anonymous 401 ≡ 11:958 |
| S11-01 | keep |  |  |
| S11-02 | keep |  |  |
| S11-03 | keep |  |  |
| S11-04 | keep |  |  |
| S11-05 | merge | S09-04 | 11:587 keep-the-assertion ≡ 09:397 |
| S11-06 | merge | S10-05 | 11:658 ≡ 10:594 |
| S11-07 | merge | S23-01 | 11:682 ≡ 23:291 (ticket 23 adds it) |
| S11-08 | keep |  |  |
| S11-09 | keep |  |  |
| S11-10 | keep |  |  |
| S11-11 | keep |  |  |
| S11-12 | keep |  |  |
| S11-13 | keep |  |  |
| S11-14 | keep |  |  |
| S12-01 | keep |  |  |
| S12-02 | keep |  |  |
| S12-03 | keep |  |  |
| S12-04 | keep |  |  |
| S12-05 | keep |  |  |
| S12-06 | keep |  |  |
| S12-07 | keep |  |  |
| S12-08 | keep |  |  |
| S12-09 | keep |  |  |
| S12-10 | keep |  |  |
| S12-11 | keep |  |  |
| S12-12 | keep |  |  |
| S12-13 | keep |  |  |
| S12-14 | keep |  |  |
| S12-15 | keep |  |  |
| S12-16 | merge | S23-06 | 12:797 JDBC round trip of FactorGrantedAuthority ≡ 23:445 serialisation test |
| S12-17 | keep |  |  |
| S12-18 | merge | S29-05 | ruling 8: 12:1112 ≡ 29 §9 test 5 |
| S13-01 | keep |  |  |
| S13-02 | keep |  |  |
| S13-03 | keep |  |  |
| S13-04 | keep |  |  |
| S13-05 | keep |  |  |
| S13-06 | keep |  |  |
| S13-07 | keep |  |  |
| S13-08 | keep |  |  |
| S13-09 | keep |  |  |
| S13-10 | keep |  |  |
| S13-11 | keep |  |  |
| S13-12 | keep |  |  |
| S13-13 | merge | S26-04 | 13:565 raw-URI cap ≡ 26:540 (26 owns truncation) |
| S13-14 | keep |  |  |
| S13-15 | keep |  |  |
| S13-16 | keep |  |  |
| S13-17 | keep |  |  |
| S13-18 | keep |  |  |
| S13-19 | keep |  |  |
| S13-20 | keep |  |  |
| S13-21 | merge | S09-05 | 13:807 ≡ 09:586 converter sets WebAuthenticationDetails |
| S13-22 | merge | S28-10 | ruling 1: 13:856 ≡ 28 test 9 |
| S13-23 | merge | S28-14,S28-12 | 13:877 ≡ 28:334 uid build check and 28:309 absence fallback |
| S14-01 | keep |  |  |
| S14-02 | keep |  |  |
| S14-03 | keep |  |  |
| S14-04 | keep |  |  |
| S14-05 | keep |  |  |
| S14-06 | keep |  |  |
| S14-07 | keep |  |  |
| S14-08 | keep |  |  |
| S14-09 | keep |  |  |
| S14-10 | keep |  |  |
| S14-11 | keep |  |  |
| S14-12 | keep |  |  |
| S14-13 | keep |  |  |
| S14-14 | keep |  |  |
| S14-15 | keep |  |  |
| S14-16 | keep |  |  |
| S14-17 | keep |  |  |
| S14-18 | keep |  |  |
| S14-19 | keep |  |  |
| S14-20 | keep |  |  |
| S14-21 | keep |  |  |
| S14-22 | keep |  |  |
| S14-23 | keep |  |  |
| S14-24 | keep |  |  |
| S14-25 | keep |  |  |
| S14-26 | keep |  |  |
| S14-27 | merge | S08-04 | 14:655 ≡ 08:760 Clear-Site-Data browser assertion |
| S14-28 | merge | S06-08 | 14:657 ≡ 06:484 enum-schema contract test |
| S15-01 | merge | S11-08 | ruling 5: 15 #1 ≡ 11:888 |
| S15-02 | merge | S23-09 | ruling 5: 15 #2 ≡ 23:1144 |
| S15-03 | merge | S26-05 | 15 #3 (TM-01) ≡ 26:510 miss budget |
| S15-04 | merge | S27-03,S27-04,S27-05,S27-06 | ruling 4: 15 row 4 restated by 27:256 |
| S15-05 | merge | S28-09 | ruling 6: 15:375 argument gating ≡ 28 test 8 |
| S15-06 | merge | S16-21 | 15 #6 (TM-13) ≡ 16:588 §7 structural test |
| S15-07 | keep |  |  |
| S15-08 | keep |  |  |
| S19-01 | keep |  |  |
| S19-02 | merge | S30-05,S30-06 | 19:310 two-admin invariant ≡ 30:201 exemption and 30:202 refusals |
| S20-01 | keep |  |  |
| S20-02 | keep |  |  |
| S20-03 | keep |  |  |
| S20-04 | keep |  |  |
| S20-05 | keep |  |  |
| S20-06 | merge | S14-01 | 20:305 ≡ 14:92 built index.html check |
| S21-01 | keep |  |  |
| S21-02 | keep |  |  |
| S21-03 | keep |  |  |
| S21-04 | keep |  |  |
| S21-05 | keep |  |  |
| S21-06 | keep |  |  |
| S21-07 | keep |  |  |
| S21-08 | keep |  |  |
| S21-09 | merge | S09-09 | 21:396 ≡ 09:1014 recordStats (third structure pulled in via Edits) |
| S21-10 | keep |  |  |
| S21-11 | merge | S09-21 | 21:575 ≡ 09:1596 alert-threshold binding |
| S21-12 | keep |  |  |
| S21-13 | retire | 21:615 | Harness configuration, not a test: an inert registry makes every meter assertion fail, so the trap self-detects. |
| S21-14 | merge | S09-10 | 16 §9: 21:627 ≡ 09§R-5 matches() count |
| S21-15 | merge | S25-01 | 21:722 filenames ≡ 25:283 jar-content test |
| S22-01 | keep |  |  |
| S22-02 | keep |  |  |
| S22-03 | merge | S14-20 | 22:547 ported case 3 ≡ 14:548 clear on every Verify |
| S22-04 | keep |  |  |
| S22-05 | keep |  |  |
| S22-06 | keep |  |  |
| S22-07 | keep |  |  |
| S22-08 | merge | S14-10 | 22:553 object-URL lifecycle ≡ 14:504 revoke-count test |
| S22-09 | keep |  |  |
| S22-10 | keep |  |  |
| S22-11 | keep |  |  |
| S22-12 | keep |  |  |
| S22-13 | keep |  |  |
| S22-14 | keep |  |  |
| S22-15 | keep |  |  |
| S22-16 | merge | S10-04 | 22:764 ≡ 10:400 redemption clears password lockout, never TOTP |
| S23-01 | keep |  |  |
| S23-02 | keep |  |  |
| S23-03 | keep |  |  |
| S23-04 | keep |  |  |
| S23-05 | keep |  |  |
| S23-06 | keep |  |  |
| S23-07 | merge | S12-07 | 23:966 ≡ 12:287 totp_key width checks |
| S23-08 | merge | S12-02 | 23:977 ≡ 12:276 BINARY retype caught |
| S23-09 | keep |  |  |
| S23-10 | merge | S11-08 | ruling 5: 23:1167 ≡ 11:888 |
| S24-01 | keep |  |  |
| S24-02 | keep |  |  |
| S24-03 | merge | S20-04 | 24:504 ≡ 20:173 profile-scoped cookie test (rule 10: ctx-nondev over 24:573) |
| S24-04 | merge | S20-04 | 24:505 ≡ 20:173 profile-scoped cookie test |
| S24-05 | keep |  |  |
| S24-06 | keep |  |  |
| S24-07 | keep |  |  |
| S24-08 | keep |  |  |
| S24-09 | keep |  |  |
| S24-10 | keep |  |  |
| S24-11 | keep |  |  |
| S24-12 | keep |  |  |
| S24-13 | keep |  |  |
| S24-14 | keep |  |  |
| S24-15 | keep |  |  |
| S24-16 | keep |  |  |
| S24-17 | keep |  |  |
| S24-18 | merge | S13-05 | 24:884 ≡ 13:540 Jackson source redaction outcome |
| S24-19 | merge | S13-10 | 24:919 adopted verbatim into 13's negative list ≡ 13:562 |
| S24-20 | merge | S21-08 | 24:931 ≡ 21:352 OTLP dormant (absent beans) |
| S24-21 | keep |  |  |
| S24-22 | keep |  |  |
| S24-23 | keep |  |  |
| S24-24 | keep |  |  |
| S24-25 | merge | S28-11 | 24:1078 ≡ 16:277 / 28:307 test |
| S24-26 | merge | S28-12 | 24:1079 ≡ 16:278 / 28:309 test |
| S24-27 | keep |  |  |
| S25-01 | keep |  |  |
| S25-02 | keep |  |  |
| S25-03 | keep |  |  |
| S25-04 | keep |  |  |
| S25-05 | merge | S16-21 | 25:870 TM-13 ≡ 16:588 §7 structural test |
| S25-06 | merge | S28-10 | ruling 1: 25:897 ≡ 28 test 9 |
| S26-01 | keep |  |  |
| S26-02 | keep |  |  |
| S26-03 | keep |  |  |
| S26-04 | keep |  |  |
| S26-05 | keep |  |  |
| S26-06 | keep |  |  |
| S26-07 | keep |  |  |
| S26-08 | keep |  |  |
| S26-09 | keep |  |  |
| S26-10 | keep |  |  |
| S26-11 | keep |  |  |
| S26-12 | keep |  |  |
| S26-13 | keep |  |  |
| S26-14 | keep |  |  |
| S26-15 | keep |  |  |
| S27-01 | keep |  |  |
| S27-02 | keep |  |  |
| S27-03 | keep |  |  |
| S27-04 | keep |  |  |
| S27-05 | keep |  |  |
| S27-06 | keep |  |  |
| S28-01 | keep |  |  |
| S28-02 | keep |  |  |
| S28-03 | keep |  |  |
| S28-04 | keep |  |  |
| S28-05 | keep |  |  |
| S28-06 | keep |  |  |
| S28-07 | keep |  |  |
| S28-08 | keep |  |  |
| S28-09 | keep |  |  |
| S28-10 | keep |  |  |
| S28-11 | keep |  |  |
| S28-12 | keep |  |  |
| S28-13 | merge | S24-21,S24-22,S24-23 | ruling 6: 16 §9 28 test 12 duplicates 24's validator entries |
| S28-14 | keep |  |  |
| S29-01 | keep |  |  |
| S29-02 | keep |  |  |
| S29-03 | keep |  |  |
| S29-04 | keep |  |  |
| S29-05 | keep |  |  |
| S29-06 | keep |  |  |
| S29-07 | keep |  |  |
| S29-08 | keep |  |  |
| S29-09 | keep |  |  |
| S29-10 | keep |  |  |
| S29-11 | keep |  |  |
| S29-12 | keep |  |  |
| S29-13 | keep |  |  |
| S29-14 | keep |  |  |
| S29-15 | keep |  |  |
| S30-01 | keep |  |  |
| S30-02 | keep |  |  |
| S30-03 | keep |  |  |
| S30-04 | keep |  |  |
| S30-05 | keep |  |  |
| S30-06 | keep |  |  |
| S30-07 | keep |  |  |
| S30-08 | keep |  |  |
| S31-01 | keep |  |  |
| S31-02 | keep |  |  |
| S31-03 | keep |  |  |
| S31-04 | keep |  |  |
| S31-05 | keep |  |  |
| S31-06 | keep |  |  |
| S31-07 | keep |  |  |
| S31-08 | keep |  |  |
| S31-09 | keep |  |  |
| S31-10 | keep |  |  |
| S31-11 | keep |  |  |
| S31-12 | keep |  |  |

## Edits
| key | column | value | reason |
|---|---|---|---|
| S16-64 | assertion | X-Content-Type-Options, X-Frame-Options and Content-Security-Policy are present on every API response, with the API policy distinct from the document policy; the API policy's script-src (or its default-src fallback) contains no unsafe-inline, unsafe-eval or wildcard source. | merged S01-01 carries IM8 as-9's FAIL condition |
| S16-64 | clause | Std §5:498; IM8 as-9 | merged S01-01 |
| S16-64 | isolation | keyed | makes requests; 16 §5 allocator hands out source keys |
| S16-63 | assertion | Strict-Transport-Security with max-age at least 31536000 is present on secure requests and absent on plain-HTTP requests. | merged S01-02 carries IM8 as-10's max-age |
| S16-63 | clause | Std §5:498; IM8 as-10 | merged S01-02 |
| S16-63 | isolation | keyed | makes requests |
| S08-22 | assertion | The effective spring.session.timeout is at most 15 minutes; aging the SPRING_SESSION row's LAST_ACCESS_TIME beyond it (cron disabled with -) makes the next request return 401 AUTHENTICATION_FAILED, never a redirect. | merged S01-03 (IM8 as-11 value bound) |
| S08-22 | clause | Std §3.5:344; IM8 as-11; 08 §1; 06 expiry 401 | merged S01-03 |
| S09-01 | clause | Std §5:452; PRD Story 3; IM8 as-4; 09 §1 | merged S01-04 |
| S09-03 | clause | Std §5:452; IM8 as-4; 09 §1 | merged S01-05 |
| S06-06 | clause | Std §3.2:244; IM8 as-13; 06 §13 | merged S01-06 |
| S06-05 | clause | Std §3.2:244; IM8 ac-1; IM8 as-7; 06 §13; 06 §14 | merged S01-07 |
| S06-01 | assertion | Parameterised over account states (unknown, wrong password, locked, disabled, grace-period-disabled, not-yet-activated, and for login the lazy 30-day forced-change expiry) × three endpoints (login, password-reset-request, registration): status and body equal one exact literal per endpoint contract, with traceId the only member permitted to vary and instance a constant (not the request URI). | 06:364 adds the forced-change expiry refusal as a must-be-identical 401 |
| S09-07 | assertion | Failed logins spaced further apart than app.security.lockout.observation-window (shared clock advanced) do not accumulate: the next wrong password sets the counter to 1, including the first failure after a lock auto-lifts past locked_until, and does not re-lock; the same count inside the window locks. | merged S02-01 |
| S09-07 | clause | 09 §2; 02 §3 | merged S02-01 |
| S20-03 | assertion | The raw Set-Cookie for the session cookie carries SameSite=Strict, HttpOnly, Path=/ and no Domain attribute; the test carries a comment naming the SameSite=Strict ADR so the deliberate failure of the Standard's Lax test reads as a decision. | merged S08-01, S16-06, S02-03 |
| S20-04 | assertion | One profile-scoped test: under the non-dev profile the raw Set-Cookie is named __Host-SESSION and carries Secure, HttpOnly, SameSite=Strict, Path=/ and no Domain, bound from server.servlet.session.cookie.* in the non-dev configuration; under the dev profile the name is SESSION and Secure is false. | rule 12 and 20:173; merged S05-07, S08-02, S16-07, S24-03, S24-04 |
| S20-04 | clause | Std §5:499; 20 §3; 20 ADR 6; 24 §8.2 | merged twins |
| S20-01 | isolation | keyed | makes requests |
| S20-02 | isolation | keyed | makes requests |
| S04-01 | control | Duplicate username or email refused on admin create | merged S04-03; no later row covers live duplicates (tombstone half is S12-13) |
| S04-01 | assertion | Admin create with a username or an email already held by a live account returns 400 USER_EXISTS and creates nothing. | restated on 11's endpoint set (11:209) |
| S04-01 | clause | 11 endpoint set; Std §2 Failure Paths 12–16 | 11 owns the decision |
| S04-07 | control | Admin guard refuses self-directed actions | merged S04-08, S04-09; no later row covers actor ≠ subject except factor reset |
| S04-07 | assertion | Parameterised over role change, disable, delete and unlock: an admin targeting their own account is refused by AdminActionGuard (actor ≠ subject) and the account is unchanged. | restated on 11's guard (11:246) |
| S04-07 | clause | 11 §Central guard; Std §2 Failure Paths 12–16; IM8 ac-1 | 11 owns the decision |
| S04-10 | assertion | Admin delete writes the deleted_users tombstone (user_id, username, email_hmac, deleted_at, deleted_by_id equal to the acting admin) in the same transaction as the users delete; a failure injected after the tombstone write commits neither. | merged S04-11; tombstone shape per 12 |
| S04-10 | clause | 11 §Soft delete; 12 tombstone; Std §2 Failure Paths 12–16 | 11 and 12 own the decision |
| S04-21 | control | Terminal denyAll for unmatched paths | kept: no later row tests the terminal rule |
| S04-21 | assertion | An authenticated USER request to a path matched by no authorization-matrix rule gets 403 ACCESS_DENIED from the terminal anyRequest().denyAll(), and an anonymous one gets 401. | statuses per 16 §7 |
| S04-21 | clause | 11 §Authorization matrix; 16 §7; IM8 ac-1; IM8 as-7 | 11 owns the decision |
| S11-13 | control | Forced-change filter allowlist | merged S04-13, S16-75 |
| S11-13 | assertion | Parameterised over endpoints: a user with forcePasswordChange set (including after an admin re-enable) gets 403 PASSWORD_CHANGE_REQUIRED on every endpoint outside the five-path forced-change allowlist, and the five allowlisted paths remain reachable. | merged S04-13 is the parameterised form |
| S11-13 | clause | Std §5:485; IM8 as-15; IM8 ac-6 | merged S04-13 |
| S11-14 | control | Self-read bounded to the caller | merged S16-76, S04-22 |
| S11-14 | assertion | GET /api/profile returns only the caller's own record, and a USER calling GET /api/admin/users/{uuid} for another user gets 403. | merged S16-76 carries the own-record half |
| S11-14 | clause | Std §5:479; IM8 ac-1 | merged S04-22 |
| S08-08 | assertion | With activity inside the idle window, a request just under the configured absolute lifetime after AUTH_INSTANT succeeds and one past it returns 401 AUTHENTICATION_FAILED with the session invalidated; the lifetime runs from authentication, not session creation (session created via /csrf and the clock advanced before login); a POST on the expired session gets 401, not 403 CSRF_TOKEN_INVALID. Shared forward-only clock, no Thread.sleep. | merged S05-04 boundary facets; 08:412 ordering invariant |
| S08-08 | clause | Std §3.5:345; 08 §2; 08 §12; 16 §4 | merged S05-04 |
| S07-02 | pillar | CRED | pillar by control proved (sole encode site); ARCH only for rules with no other home |
| S09-05 | control | Converter sets WebAuthenticationDetails | row 3's second assertion is carried by ticket 31's equivalence-class row (31:326) |
| S09-05 | assertion | After login attempts the Authentication carries non-null SourceKeyAuthenticationDetails set by the Route C converter, and failed-login audit rows from two distinct source keys carry non-null, distinct source.ip_hash values. | merged S13-21; equivalence classes moved to 31 per 16 §9 |
| S09-05 | clause | 09 §R.7; 09 §12; 13 §4 | merged S13-21 |
| S09-08 | assertion | The effective app.security.client-ip.source defaults to socket, and source=proxy with absent or empty trusted-proxies fails refresh; the framework forward-headers refusal is carried by the validator's own per-entry test. | framework half is 24's entry (24:552 one assertion per entry) |
| S09-08 | level | U | rule 10: configuration binding and validation only (ApplicationContextRunner) |
| S09-08 | context | none | rule 10 |
| S09-09 | assertion | Both Caffeine bucket maps and the per-source cardinality set are registered via CaffeineCacheMetrics.monitor with recordStats(), and cache.gets for each is non-zero after exercising the limiter. | merged S21-09; 21:682 adds the third structure |
| S09-09 | clause | 09 §R input 2; 21 §5; IM8 lm-16 | merged S21-09 |
| S13-10 | assertion | A present but malformed or wrong-length key, and any other rejected configuration value, refuses startup with our exception carrying only the observed length; the value and its origin appear in no log line, appender, stdout/stderr or throwable at any level (ApplicationContextRunner with captured output). | merged S24-19 is the precise statement; normal-run key material is covered by the canary scan |
| S13-10 | level | U | rule 10: failing-startup validation |
| S13-10 | context | none | rule 10 |
| S13-10 | clause | 13 §10; 24 §7 | merged S24-19 |
| S13-18 | level | U | rule 10: startup must fail, so no shared context; ApplicationContextRunner |
| S13-18 | context | none | rule 10 |
| S13-19 | context | restart | needs ApplicationReadyEvent with a LOGGING_LEVEL override outside dev |
| S13-19 | isolation | own-DB | restart harness |
| S13-20 | isolation | keyed | makes requests |
| S14-10 | assertion | Across a simulated StrictMode remount, QR replacement, unmount, the error path and the isVerified flip, URL.revokeObjectURL is called once per URL.createObjectURL and the img never points at a revoked URL. | merged S22-08 facets |
| S14-10 | clause | 14 §11; 22 §11 | merged S22-08 |
| S14-20 | clause | 14 §13; FE-STD §8.1:442; FE-STD §7.3 | merged S22-03 (ported case 3) |
| S19-01 | control | Admin TOTP reset removes factor rows and audits | session kill is the replay row and the factor gate is the matrix row |
| S19-01 | assertion | DELETE /api/admin/users/{uuid}/totp by an ADMIN deletes the target's totp_user_details row and any pending_totp row and emits one audit row with event.action totp-remove carrying user.target.id. | narrowed to facets no other row covers |
| S19-01 | clause | IM8 ac-2; 19 §Answer; 11 endpoint set | owner lines |
| S21-01 | isolation | keyed | makes requests |
| S21-02 | isolation | keyed | makes requests |
| S21-03 | isolation | keyed | makes requests |
| S21-04 | isolation | keyed | makes requests |
| S21-08 | assertion | The effective management.otlp.metrics.export.enabled is false and the OtlpMeterRegistry, OtlpConfig and OtlpMetricsConnectionDetails beans are absent (never asserted on the resolved URL). | merged S24-20 |
| S21-08 | clause | 21 §3; 24 amendment from 21 | merged S24-20 |
| S23-06 | clause | 23 §3; 23 §4; 12 §12 | merged S12-16 |
| S23-09 | pillar | MFA | ruling 5: one pillar for the lock order; the trip is the TOTP factor's tier-2 path (TM-03, ticket 23) |
| S26-09 | isolation | keyed | makes requests |
| S27-02 | context | restart | rule 10: baggage probe override |
| S27-02 | isolation | own-DB | restart harness |
| S27-03 | isolation | keyed | makes requests |
| S27-04 | isolation | keyed | makes requests |
| S27-05 | context | restart | rule 10: sampling 0.0 override |
| S27-05 | isolation | own-DB | restart harness |
| S27-06 | isolation | keyed | makes requests |
| S28-01 | isolation | own-DB | rule 11: runner rows run against their own database |
| S28-09 | isolation | own-DB | rule 11 |
| S28-14 | isolation | own-DB | rule 11 |
| S28-14 | assertion | In runner mode the audit row's process.real_user.name equals the OS user that ProcessHandle.current().info().user() reports for the runner process on the build JDK, recording which uid the JDK reports. | merged S13-23; absence half is the identity-fallback row |
| S29-03 | assertion | A CSRF-less unsafe request with no session still gets 403 CSRF_TOKEN_INVALID (audit reason CSRF_MISSING) and creates no SPRING_SESSION row, because the wrapper inherits loadDeferredToken. | CSRF_MISSING is an audit reason (13 row 13), not an envelope code |
| S29-06 | isolation | keyed | creates a session |
| S29-09 | context | restart | rule 10: FileStore stubs |
| S29-09 | isolation | own-DB | restart harness |
| S29-10 | isolation | delta | counts COUNT(*) queries on a shared table |
| S29-13 | context | restart | own-DB row needs its own file |
| S29-15 | context | restart | rule 10: cron enabled |
| S29-15 | isolation | own-DB | restart harness |
| S30-05 | context | restart | exact admin count needs its own database |
| S30-06 | context | restart | exact admin count needs its own database |
| S30-07 | context | restart | exact admin count needs its own database |
| S16-68 | context | restart | rule 10: runs without ctx-default's capture override |
| S16-68 | isolation | own-DB | restart harness |
| S16-58 | assertion | Fixed client-secret canaries (including the runner's stdin password) plus per-test registered server secrets captured from responses (session id, CSRF token, reset and activation tokens, TOTP secret and codes, the environment AES key), in every encoded form, appear in no logger, stdout/stderr, audit file or runner output. | review: ticket 28 removed the runner token (28:205, 28:212); 16:554's "rebind tokens" is moot |
| S05-06 | assertion | POST /api/login with a non-JSON or unparseable body returns an application/problem+json envelope with a closed-enum code, never a 500 or a BasicErrorController body. | review: path fixed by 11:354's exhaustive exempt list |
| S05-05 | assertion | POST /api/login with a JSON body and a valid CSRF token for a valid account succeeds (JSON success body, no redirect) and the session id after login differs from the pre-login session id. | review: path per 11:354 |
| S05-09 | control | CSRF enforced on POST /api/login | review: path per 11:354 |
| S05-09 | assertion | POST /api/login with a valid JSON body but no or a wrong CSRF token is refused with 403 CSRF_TOKEN_INVALID and no session is authenticated. | review: path per 11:354 |
| S05-02 | control | /api/csrf token endpoint is uncacheable | review: path per 11:354 |
| S05-02 | assertion | GET /api/csrf response carries Cache-Control: no-cache, no-store, max-age=0, must-revalidate, Pragma: no-cache and Expires: 0 (Spring Security's default cache-control writer, not a handler-set header). | review: path per 11:354 |
| S20-04 | context | restart | review: one test spans two profiles, so two out-of-cache runs on a real port, one per profile |
| S20-04 | isolation | own-DB | restart harness |
| S06-08 | assertion | Every frontend MSW error fixture validates against the JSON Schema generated from the backend's closed code enum; an unknown code or a missing member fails. | review: Vitest cannot run the backend half, which moves to the no-sendError row (16:605 gives the fixtures their own ID) |
| S06-07 | assertion | Across error responses from every producer, no body has the BasicErrorController shape (timestamp/error/message/path); every error body carries type and code and validates against the JSON Schema generated from the closed code enum. | review: backend half of 06:484's contract test |
| S06-07 | clause | Std §3.2:244; Std §5:509; 06 §13; 06 amendment from 16 | review: 06:484 backend half |
| S32-03 | assertion | Parameterised over app.mfa.totp.encryption.key, app.security.hmac.tombstone.key, app.security.hmac.log.key, app.origins.spa, app.origins.api, app.mfa.totp.encryption.key-version and app.security.hmac.tombstone.version: with the property absent, context refresh fails; a blank app.mfa.totp.issuer likewise refuses startup. | review: a U-level ApplicationContextRunner cannot observe port binding |
| S28-01 | polarity | pos | review: presence check |
| S14-03 | clause | 06 amendment from 23 §2; 14 §2 | review: 06 §406 was a line number |
| LOG-03 | assertion | After a request completes, the MDC on the executing thread holds no user.id, session.hash or trace fields (thread state inspected directly after the filter chain returns). | review: the following-request half is T-AUD-026's (13:567) |

## Minted
| key | pillar | control | assertion | level | context | isolation | clause | source | polarity | dup | note |
|---|---|---|---|---|---|---|---|---|---|---|---|
| S32-02 | CFG | Key material shape validated at startup | Parameterised over app.mfa.totp.encryption.key, app.security.hmac.tombstone.key and app.security.hmac.log.key: a value that fails strict padded Base64 decoding, decodes to other than exactly 32 bytes, decodes to all-printable-ASCII bytes, or decodes to all-identical bytes refuses startup in the @Bean factory; a valid key starts and emits one fingerprint line of 8 hex characters over the decoded bytes. | U | none | none | 24 §7 | 24:356; 24:391 | neg |  | minted by 32: 24 §7 states these rules as enforced but names a test only for the failure-message rule. ApplicationContextRunner. |
| S32-03 | CFG | Required secrets and origins refuse startup when absent | Parameterised over app.mfa.totp.encryption.key, app.security.hmac.tombstone.key, app.security.hmac.log.key, app.origins.spa, app.origins.api, app.mfa.totp.encryption.key-version and app.security.hmac.tombstone.version: with the property absent, context refresh fails before the port binds; a blank app.mfa.totp.issuer likewise refuses startup. | U | none | none | 24 §8.1; 24 §8.2; IM8 as-8 | 24:412; 24:496; 24:509 | neg |  | minted by 32: 24's inventory states refuse-to-start per row with no test. app.admin.* absence is 30's bootstrap test. The app.origins.api cross-check (24:497) is not minted: 24 names no predicate for it. |
| S32-04 | ADM | Role YAML and roles table agree at startup | Against a database whose seeded roles table disagrees with app.security.roles (a role missing on either side), context refresh fails in the refresh-phase validator before the port binds. | C | restart | own-DB | 11 §Role model; 24 §8.2 | 11:155; 24:510 | neg |  | minted by 32: decided startup validator with no named test (only the §5:424 duplicate check has one). |
| S32-05 | RL | Miss-budget window binding | With capacity and window read from app.security.rate-limit.session-miss.capacity and app.security.rate-limit.session-miss.window, a source key that has spent its miss budget is refused until the shared clock advances by the bound window and is admitted after it. | C | ctx-default | keyed | 26 §10; TM-01 | 26:511 | pos |  | minted by 32: 26's seam rule (26:501) requires a binding test per constant; the window's cell is "—". |
| S32-06 | CFG | Shedding floor and lead_days non-negative | The startup validator refuses a negative floor or a negative lead_days for ticket 29's disk-reserve shedding (context refresh fails); zero and the planning values start. | U | none | none | 29 §3 | 29:198 | neg |  | minted by 32: validator behaviour stated with no test. Property keys are 29's configuration keys (29 does not spell them). |
| S32-07 | AUTH | CompromisedPasswordChecker withheld as a bean | The context contains no CompromisedPasswordChecker bean, so DaoAuthenticationProvider never raises CompromisedPasswordException on login. | C | ctx-default | none | 07 §4; 06 §3 | 07:265 | neg |  | minted by 32: 07 calls the withheld bean the control (an oracle otherwise); no test named. |
| S32-08 | AUTH | No WWW-Authenticate on 401 | The entry point's 401 (anonymous JSON request to a protected path) and the failure handler's 401 (failed JSON login) carry no WWW-Authenticate header. | C | ctx-default | keyed | 06 §9 | 06:244 | neg |  | minted by 32: decided in 06 §9; no test named. |
| S32-09 | AUD | Audit session strategy ordered inside the composite | A second login by U that displaces U's first session emits row 10 for the displaced session at displacement time, and the login-success row carries the pre-rotation session.hash that joins it to the pre-login rows, not the post-rotation hash. | C | ctx-default | keyed | 13 ordering rule; 08 §3 | 13:453 | pos |  | minted by 32: 13 says misordering fails no test; the ordering is decided and application-enforced. |
