#!/usr/bin/env bash
# harness: config-sync — auto-syncs guardrails when harness.config.json changes
set -euo pipefail

echo "harness.config.json changed — auto-syncing guardrails..."
npx harness sync --root "$CLAUDE_PROJECT_DIR" 2>&1 | tail -5
echo "Guardrails synchronized."

exit 0
