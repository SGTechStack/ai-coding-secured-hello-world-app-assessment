> **All directories must be considered.** For each business feature, do not skip any listed directory — each one contains standards or context required for a correct implementation.

> **Build order matters.** For each business feature, all directories listed must be visited in order — each layer depends on the one above it, so build them top-down.

### Fixed-Schema Report
Report generation, JasperReports, JRXML template, report output format, report rendering, fixed-schema report
**Build order (read in sequence):**
1. [Report_Core](Report_Core/)

### Programmatic Report (Dynamic Columns)
Programmatic report, dynamic columns, JasperDesign, variable column schema, ad-hoc data export, report generation from code
**Build order (read in sequence):**
1. [Report_Core](Report_Core/)
2. [Report_Programmatic](Report_Programmatic/)
