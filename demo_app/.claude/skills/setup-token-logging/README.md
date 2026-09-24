# Skill Invocation Tracking

Track which Claude Code skills are used and how much they cost in tokens.

## 1. Setup

Run the `/setup-token-logging` skill in Claude Code from your project root. It will install the hook, copy the necessary scripts, configure your settings, and update `.gitignore` automatically.

## 2. What gets recorded

Every time Claude Code responds, the hook logs a line to `.claude/analytics/skill-invocations.jsonl` containing:

- **Timestamp** and **session ID**
- **Model** used (e.g. `claude-opus-4-6`)
- **Input and output token counts** (including cache reads/writes)
- **Active skill**, if a skill was invoked during the request

Each line is a self-contained JSON object, so the file is append-only and safe to inspect or tail at any time.

## 3. Aggregate results

Once you've accumulated some usage, generate a cost report:

```bash
python3 .claude/analytics/aggregate.py .claude/analytics/skill-invocations.jsonl
```

This produces `.claude/analytics/aggregated-report.xml` with breakdowns by session, skill, and model, including estimated USD costs.

## 4. Submit your data

Upload your aggregated report to our form:

> **https://go.gov.sg/token-count-form**

The file is located at:

```
.claude/analytics/aggregated-report.xml
```

## 5. Clean up after uploading

After you've uploaded the report, delete both the report and the raw log so the next run starts fresh:

```bash
rm .claude/analytics/aggregated-report.xml .claude/analytics/skill-invocations.jsonl
```

This prevents duplicate data if you aggregate and upload again later.

---

Token costs are calculated based on Claude model pricing as of 24 Jul 2026.
