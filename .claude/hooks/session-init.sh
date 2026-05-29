#!/usr/bin/env bash
# harness: session-init — injects project context on session start
set -euo pipefail

CONFIG="$CLAUDE_PROJECT_DIR/harness.config.json"

if [ ! -f "$CONFIG" ]; then
  exit 0
fi

GLOSSARY="$CLAUDE_PROJECT_DIR/domain-glossary.json"

echo "harness-core active. Reading project configuration..."
echo ""

# Output project summary
FRAMEWORK=$(jq -r '.project.framework' "$CONFIG")
LANGUAGE=$(jq -r '.project.language' "$CONFIG")
ARCH=$(jq -r '.architecture.style' "$CONFIG")
RUNNER=$(jq -r '.testing.runner' "$CONFIG")
PERSONA=$(jq -r '.agent.persona' "$CONFIG")

echo "Project: $(jq -r '.project.name' "$CONFIG")"
echo "Stack: $FRAMEWORK / $LANGUAGE / $ARCH"
echo "Test runner: $RUNNER | Persona: $PERSONA"
echo ""

# Output allowed scopes
SCOPES=$(jq -r '.agent.allowedScopes[]' "$CONFIG" 2>/dev/null | tr '\n' ', ' | sed 's/,$//')
echo "Allowed scopes: $SCOPES"

# Output forbidden imports
IMPORTS=$(jq -r '.architecture.forbiddenImports | to_entries[] | "  \(.key) → cannot import from \(.value | join(", "))"' "$CONFIG" 2>/dev/null)
if [ -n "$IMPORTS" ]; then
  echo ""
  echo "Import restrictions:"
  echo "$IMPORTS"
fi

# Output domain glossary if exists
if [ -f "$GLOSSARY" ]; then
  TERM_COUNT=$(jq '.terms | length' "$GLOSSARY")
  TERMS=$(jq -r '.terms | keys[]' "$GLOSSARY" | tr '\n' ', ' | sed 's/,$//')
  echo ""
  echo "Domain glossary ($TERM_COUNT terms): $TERMS"
  echo "Reference domain-glossary.json for definitions."
fi

echo ""
echo "=== Harness SDLC Pipeline ==="
echo "0. SETUP: harness init (done)"
echo "1. PLAN: harness plan --scan → --from (features, priorities, milestones)"
echo "2. ANALYZE: harness analyze --scan → --from (domain glossary, feature specs)"
echo "3. DESIGN: harness design --scan → --from (interfaces, mocks, API contracts)"
echo "4. DEVELOP: harness generate <type> <name> (NEVER create files directly)"
echo "5. TEST: harness test (self-healing loop)"
echo ""
echo "=== Current Status ==="

PLAN_EXISTS="no"
GLOSSARY_EXISTS="no"
DESIGN_EXISTS="no"

if [ -f "$CLAUDE_PROJECT_DIR/docs/plan.json" ]; then PLAN_EXISTS="yes"; fi
if [ -f "$CLAUDE_PROJECT_DIR/domain-glossary.json" ]; then GLOSSARY_EXISTS="yes"; fi
if [ -d "$CLAUDE_PROJECT_DIR/docs/designs" ] && [ "$(ls -A "$CLAUDE_PROJECT_DIR/docs/designs" 2>/dev/null)" ]; then DESIGN_EXISTS="yes"; fi

echo "Plan: $PLAN_EXISTS | Glossary: $GLOSSARY_EXISTS | Design: $DESIGN_EXISTS"

if [ "$PLAN_EXISTS" = "no" ]; then
  echo ""
  echo "→ Next step: Run 'harness plan --scan' to start planning."
elif [ "$GLOSSARY_EXISTS" = "no" ]; then
  echo ""
  echo "→ Next step: Run 'harness analyze --scan' for domain analysis."
elif [ "$DESIGN_EXISTS" = "no" ]; then
  echo ""
  echo "→ Next step: Run 'harness design --scan' to create design contracts."
else
  echo ""
  echo "→ Ready for implementation. Use 'harness generate <type> <name>' to create files."
fi

exit 0
