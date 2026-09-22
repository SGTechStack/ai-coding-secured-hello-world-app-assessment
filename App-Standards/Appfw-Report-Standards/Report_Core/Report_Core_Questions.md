# Report Core — Implementation Questions

Questions to resolve with the team or product owner before starting implementation.

---

### Report content and format

1. Is a `.jrxml` template already available for this report, or does it need to be created?
2. Which output formats are required — PDF, XLSX, CSV, or a combination?
3. Is the column schema fixed across all requests, or does it vary per call?
4. Are there any reports that require grouping, sub-reports, charts, or complex banding? If so, JRXML is required.
5. Will any reports include free-text narrative content (e.g. summary paragraphs, disclaimers) in addition to tabular data?
6. Does the report need a title, and is it static or caller-supplied per request?

### Security classification

7. What is the security classification and sensitivity level of each report? Are all reports the same classification, or does it vary per report instance?
8. For `RESTRICTED`+ reports: is there a designated access-controlled output directory already provisioned, or does one need to be set up?

### Data source

9. What is the datasource for each report — a JDBC `ResultSet`, a CSV file, or an in-memory map?
10. If CSV: are the CSV files produced by another system? Is the header row format stable and known at design time?
11. If `Map<String, List<Object>>` datasource: who constructs the map, and are the keys guaranteed to match the declared column names exactly (case-sensitive)?
12. If `ResultSet` datasource: are the column aliases in the SQL query guaranteed to match the declared column names exactly?
13. Are there any reports where the datasource may be empty (zero rows)? What should the output look like in that case?

### Output and delivery

14. Are reports written to disk for later retrieval, streamed directly to the browser, or both?
15. If written to disk: what is the base output directory, and who is responsible for provisioning it and managing permissions?
16. If streamed to the browser: is there a maximum report size beyond which streaming would be problematic (e.g. memory pressure, connection timeout)?
17. Should the integration create output subdirectories automatically, or should it fail if the directory does not exist?

### Storage and retention

18. Are generated report files retained after serving, or deleted immediately?
19. If retained: what is the required retention period, and how is automated cleanup implemented?
20. Is S3 backup required for any classification level? If so, which bucket, prefix structure, and retention policy apply?

### Access control

21. Who is permitted to trigger a report export — any authenticated user, a specific role, or only MFA-verified sessions?
22. Are there any reports that should be accessible only to certain roles but not others (i.e. per-report access control)?

### Identity

23. What type of user identifier does Spring Security expose as the principal name for this application — SSO ID, employee number, email address, or another format? This determines the value hashed for audit logs.

### Operational

24. What is the expected report generation volume — requests per minute, peak load, and typical datasource row count?
25. Is there a latency SLA for report generation (e.g. must complete within 10 seconds)?
26. Who is the on-call owner for report generation failures in production?
