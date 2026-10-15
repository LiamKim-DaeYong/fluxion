#!/usr/bin/env bash
# PreToolUse(Bash) 훅: `gh pr create` 직전에 ktlintCheck를 돌리고, 실패하면 exit 2로 막는다.
# settings의 if 필드가 1차로 거르지만 매칭이 best-effort라 여기서 명령을 다시 확인한다.

. "$(dirname "$0")/lib.sh"
input="$(cat)"
[[ "$input" =~ gh[[:space:]]+pr[[:space:]]+create ]] || exit 0
cd "$project_dir" 2>/dev/null || exit 0
[ -x ./gradlew ] || exit 0

if ! out="$(./gradlew ktlintCheck --console=plain 2>&1)"; then
  {
    echo "ktlintCheck 실패로 PR 생성을 막았다. ./gradlew ktlintFormat을 실행하고 바뀐 파일을 'style: apply ktlint format'으로 커밋한 뒤 다시 시도하라."
    # 위반 줄(파일:줄:열)만 보여 준다. 없으면 빌드 실패 원인을 보여 준다.
    printf '%s\n' "$out" | grep -E '\.kts?:[0-9]+:[0-9]+' | head -20 \
      || printf '%s\n' "$out" | grep -v '^WARNING:' | tail -20
  } >&2
  exit 2
fi
exit 0
