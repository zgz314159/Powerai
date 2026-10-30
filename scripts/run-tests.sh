#!/usr/bin/env bash
# AEGL task verify 默认入口（PowerAi）。仅跑 Smart RAG 相关 JVM 门禁，避免全量过慢。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# aegl-task 传入 `--root "$PWD"`；吃掉以免误传给 Gradle。
while [ $# -gt 0 ]; do
  case "$1" in
    --root)
      shift
      [ $# -gt 0 ] && shift
      ;;
    --root=*)
      shift
      ;;
    *)
      break
      ;;
  esac
done
cd "$ROOT"

# AEGL sandbox：GRADLE_USER_HOME 常指向空的 task gradle-home，且无外网。
# 若尚无已解压的 gradle-9.1.0，从宿主机 ~/.gradle 预热（完整目录，含 .ok）。
if [ -n "${GRADLE_USER_HOME:-}" ]; then
  need_dist=1
  if find "${GRADLE_USER_HOME}/wrapper/dists/gradle-9.1.0-bin" -type d -name 'gradle-9.1.0' 2>/dev/null | grep -q .; then
    need_dist=0
  fi
  if [ "$need_dist" = 1 ] && [ -d "${HOME}/.gradle/wrapper/dists/gradle-9.1.0-bin" ]; then
    mkdir -p "${GRADLE_USER_HOME}/wrapper/dists" "${GRADLE_USER_HOME}/caches"
    rm -rf "${GRADLE_USER_HOME}/wrapper/dists/gradle-9.1.0-bin"
    cp -a "${HOME}/.gradle/wrapper/dists/gradle-9.1.0-bin" "${GRADLE_USER_HOME}/wrapper/dists/"
  fi
  if [ -d "${HOME}/.gradle/caches/modules-2" ] && [ ! -d "${GRADLE_USER_HOME}/caches/modules-2" ]; then
    mkdir -p "${GRADLE_USER_HOME}/caches"
    cp -a "${HOME}/.gradle/caches/modules-2" "${GRADLE_USER_HOME}/caches/"
  fi
fi
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"

OFFLINE_ARGS=()
if ! getent hosts services.gradle.org >/dev/null 2>&1; then
  OFFLINE_ARGS+=(--offline)
fi

# 不含 DeepSeekPromptManagerTest：沙箱内 Mockito MockMaker/ByteBuddy 常被拒；
# 该套件在宿主机 GRADLE_USER_HOME=~/.gradle 下单独验证。
./gradlew \
  "${OFFLINE_ARGS[@]}" \
  --no-daemon \
  :feature:search-chat:testDebugUnitTest \
  --tests 'com.example.powerai.feature.searchchat.SmartRagFixedEvalTest' \
  --tests 'com.example.powerai.feature.searchchat.SmartRagSeedEvalTest' \
  --tests 'com.example.powerai.feature.searchchat.SmartAnswerDecisionEngineTest' \
  --tests 'com.example.powerai.feature.searchchat.SmartSemanticClaimGuardTest' \
  --tests 'com.example.powerai.feature.searchchat.SmartEvidenceExcerptTest' \
  --console=plain \
  "$@"
