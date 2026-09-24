#!/bin/bash
# track-token-count.sh
#
# Stop hook: fires after the model finishes responding to a prompt.
# Scans the transcript for /skill invocations and logs each one
# with token usage to the analytics JSONL file.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="${SKILL_TRACKER_LOG_DIR:-$SCRIPT_DIR/../analytics}"
LOG_FILE="$LOG_DIR/skill-invocations.jsonl"

mkdir -p "$LOG_DIR"

INPUT=$(cat)

SESSION_ID=$(echo "$INPUT" | jq -r '.session_id // empty')
TRANSCRIPT_PATH=$(echo "$INPUT" | jq -r '.transcript_path // empty')

if [[ -z "$TRANSCRIPT_PATH" || ! -f "$TRANSCRIPT_PATH" ]]; then
  exit 0
fi

# Quick check: skip heavy jq parsing if no skill invocations exist in transcript
if ! grep -q '<command-name>/' "$TRANSCRIPT_PATH" 2>/dev/null; then
  exit 0
fi

# Extract all skill invocations from user messages as uuid|skill pairs.
# Each <command-name> tag message has a unique .uuid in the transcript.
# Done in a single jq pass to avoid control-character issues when piping content through shell.
ALL_INVOCATIONS=$(jq -r '
  select((.type == "user" or .type == "human") and (.message.content | type == "string"))
  | .uuid as $uuid
  | .message.content
  | if test("<command-name>/[a-zA-Z0-9_-]+</command-name>") then
      capture("<command-name>/(?<skill>[a-zA-Z0-9_-]+)</command-name>"; "g") | "\($uuid)|\(.skill)"
    elif test("^/[a-zA-Z0-9_-]+") then
      capture("^/(?<skill>[a-zA-Z0-9_-]+)"; "g") | "\($uuid)|\(.skill)"
    else empty
    end
' "$TRANSCRIPT_PATH" 2>/dev/null)

if [[ -z "$ALL_INVOCATIONS" ]]; then
  exit 0
fi

# Filter out invocations already logged (by prompt_id in jsonl)
LOGGED_IDS=$(jq -r '.prompt_id // empty' "$LOG_FILE" 2>/dev/null || true)

MODEL=$(jq -r '
  select(.message.model) | .message.model
' "$TRANSCRIPT_PATH" 2>/dev/null | tail -1)
MODEL="${MODEL:-unknown}"

# Extract usage from transcript — full session cumulative
TOTAL_USAGE=$(jq -s -c '
  [ .[] | select(.message.id and .message.usage) | {id: .message.id, usage: .message.usage} ]
  | group_by(.id)
  | map(last.usage)
  | reduce .[] as $u (
      {input_tokens: 0, output_tokens: 0, cache_creation_input_tokens: 0, cache_read_input_tokens: 0};
      {
        input_tokens: (.input_tokens + ($u.input_tokens // 0)),
        output_tokens: (.output_tokens + ($u.output_tokens // 0)),
        cache_creation_input_tokens: (.cache_creation_input_tokens + ($u.cache_creation_input_tokens // 0)),
        cache_read_input_tokens: (.cache_read_input_tokens + ($u.cache_read_input_tokens // 0))
      }
    )
' "$TRANSCRIPT_PATH" 2>/dev/null || echo '{}')

TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Log each new invocation, skip if prompt_id already in jsonl
while IFS='|' read -r PROMPT_ID COMMAND_NAME; do
  [[ -z "$COMMAND_NAME" ]] && continue

  # Deduplicate by prompt_id
  if echo "$LOGGED_IDS" | grep -qF "$PROMPT_ID" 2>/dev/null; then
    continue
  fi

  RECORD=$(jq -n -c \
    --arg ts "$TIMESTAMP" \
    --arg session "$SESSION_ID" \
    --arg prompt_id "$PROMPT_ID" \
    --arg skill "$COMMAND_NAME" \
    --arg model "$MODEL" \
    --argjson usage "$TOTAL_USAGE" \
    '{timestamp: $ts, session_id: $session, prompt_id: $prompt_id, skill: $skill, model: $model, usage: $usage}')

  echo "$RECORD" >> "$LOG_FILE"

  echo "$RECORD"
done <<< "$ALL_INVOCATIONS"

exit 0
