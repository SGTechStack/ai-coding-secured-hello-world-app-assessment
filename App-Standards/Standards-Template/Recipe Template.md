# Recipe Format Template

Use this structure for all recipe guides so they remain consistent and easy to follow.

## 1. Title
Short, descriptive, and specific to the implementation goal.

Example: `Structured Logging and Trace Correlation in Spring Boot`

## 2. Introduction
1 to 3 short paragraphs that answer:
- What the recipe covers
- Why it matters
- What the reader will achieve by the end

## 3. Prerequisites
List any required versions, frameworks, or infrastructure.

Example:
- Spring Boot 3.4+
- Access to a trace backend such as Zipkin

## 4. Steps
Numbered sections, each with:
- A short heading that describes the goal
- A concise explanation of why the step is needed
- A code block with a clear file label comment

Example:

    ## 2. Add the tracing dependencies
    Explain why tracing dependencies are needed and what they enable.

    ```xml
    # File: pom.xml
    <dependencies>
        ...
    </dependencies>
    ```

## 5. Examples
Show end-to-end or commonly used flows. Each example should have:
- A clear title
- 1 to 2 sentences of context
- One or more code blocks

## 6. Verification
Describe how to confirm the setup works in practice. Keep it short and practical.

## 7. Conclusion
Summarize what was implemented and the practical outcome for the reader.

## 8. References
List the official sources used to validate the steps. Include links.
