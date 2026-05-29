#!/usr/bin/env bash
# harness: post-write — architecture check + test companion check after file write
set -euo pipefail

INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

if [ -z "$FILE_PATH" ]; then
  exit 0
fi

REL_PATH=$(realpath --relative-to="$CLAUDE_PROJECT_DIR" "$FILE_PATH" 2>/dev/null || echo "$FILE_PATH")

CONTEXT=""

# 1. Import violation check (silent, non-blocking — context feedback only)
SYNC_RESULT=$(npx harness sync --check --root "$CLAUDE_PROJECT_DIR" 2>&1) || true
if echo "$SYNC_RESULT" | grep -q "violation"; then
  CONTEXT="$CONTEXT\n⚠️ Import violation detected after writing $REL_PATH:\n$SYNC_RESULT"
fi



if [ -n "$CONTEXT" ]; then
  echo -e "$CONTEXT"
fi

exit 0
