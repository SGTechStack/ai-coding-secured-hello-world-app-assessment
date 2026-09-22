# Report Programmatic Design — Design Choices and Implementation Questions

Technical requirement questions to resolve with the team before starting implementation. Questions in the core standard (`Report_Core_Questions.md`) must also be resolved — this document covers only the additional questions specific to the programmatic design approach.


### Page layout

1. Is A4 portrait the correct page size for all reports, or are any reports expected to use landscape orientation or a different paper size?
2. Are all columns expected to fit within the 555 pt usable width at default margins? If not, what is the widest expected column set?

### Paragraph content

3. Do any reports require free-text narrative content (e.g. an introduction, a summary, a disclaimer) in addition to the tabular data?
4. If yes: is the paragraph text static (same for all instances of the report) or dynamic (supplied by the caller or derived from data at fill time)?
5. If caller-supplied paragraph text: what validation is in place upstream to prevent injection of `$P{}`/`$V{}`/`$F{}` strings before they reach the report integration?


