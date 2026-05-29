#!/usr/bin/env bash
# harness: scaffold-guard — nudges AI to use 'harness generate' for new files
set -euo pipefail

INPUT=$(cat)
TOOL_NAME=$(echo "$INPUT" | jq -r '.tool_name // empty')
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

# Only check Write (new file creation), not Edit (existing file modification)
if [ "$TOOL_NAME" != "Write" ] || [ -z "$FILE_PATH" ]; then
  exit 0
fi

# If file already exists, it's an overwrite — allow it
if [ -f "$FILE_PATH" ]; then
  exit 0
fi

REL_PATH=$(realpath --relative-to="$CLAUDE_PROJECT_DIR" "$FILE_PATH" 2>/dev/null || echo "$FILE_PATH")

# Check if the new file is in a scaffoldable directory
SUGGESTION=$(node -e "
const dirs = {"src/components":{"type":"component"},"src/hooks":{"type":"hook"},"src/utils":{"type":"util"},"src/services":{"type":"service"},"src/models":{"type":"model"}};
const rel = process.argv[1];
for (const [dir, info] of Object.entries(dirs)) {
  if (rel.startsWith(dir + '/')) {
    const name = rel.split('/').pop().replace(/\.[^.]+$/, '');
    console.log('Use: harness generate ' + info.type + ' ' + name);
    process.exit(0);
  }
}
" "$REL_PATH" 2>/dev/null || true)

if [ -n "$SUGGESTION" ]; then
  echo "harness: Instead of creating '$REL_PATH' manually, use the scaffolder:" >&2
  echo "  $SUGGESTION" >&2
  echo "" >&2
  echo "This ensures correct directory structure, naming conventions, and barrel exports." >&2
  exit 2
fi

exit 0
