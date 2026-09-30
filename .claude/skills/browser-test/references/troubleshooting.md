# Troubleshooting

| Symptom | Likely Cause | Fix |
|---------|--------------|-----|
| `agent-browser` not found | Browser automation dependency missing | `npm i -g agent-browser && agent-browser install` |
| Docker Compose unavailable | Docker not installed, not running, or project does not use Compose | Start Docker or use the documented non-Compose run command |
| Compose build fails | Missing dependency, invalid Dockerfile, or build context issue | Inspect build output and project setup docs |
| No published port found | Service has no host port mapping or wrong service selected | Re-run with `--service=<name>` or `--base-url=<url>` |
| Health / readiness timeout | App still starting, failed migration, missing env var, or wrong health URL | Check compose logs and increase `--startup-timeout` if startup is legitimately slow |
| Base URL returns blank page | SPA still hydrating or frontend asset error | Wait longer, inspect browser snapshot, and check frontend logs |
| Redirected to login | Auth required and no test session available | Mark affected criteria BLOCKED or provide documented test credentials |
| Expected data missing | Seed data or external dependency unavailable | Mark criterion SKIP or BLOCKED depending on whether data is optional or required |
| Screenshot fails | Invalid output path or permissions | Use a temp or report directory path without shell-sensitive characters |
