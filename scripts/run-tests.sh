#!/usr/bin/env bash
# AEGL task verify 默认入口（PowerAi）。仅跑 Smart RAG 相关 JVM 门禁，避免全量过慢。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
./gradlew \
  :feature:search-chat:testDebugUnitTest \
  --tests 'com.example.powerai.feature.searchchat.SmartRagFixedEvalTest' \
  --tests 'com.example.powerai.feature.searchchat.SmartRagSeedEvalTest' \
  --tests 'com.example.powerai.feature.searchchat.SmartAnswerDecisionEngineTest' \
  --tests 'com.example.powerai.feature.searchchat.SmartSemanticClaimGuardTest' \
  --tests 'com.example.powerai.feature.searchchat.SmartEvidenceExcerptTest' \
  --tests 'com.example.powerai.feature.searchchat.DeepSeekPromptManagerTest' \
  --console=plain \
  "$@"
