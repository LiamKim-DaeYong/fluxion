#!/usr/bin/env bash
# Hub가 Mailbox(coordinator) 이름으로 orchestration 명령을 실행한다.
#   bash .claude/hooks/mb.sh <orchestration 하위 명령> [인자...]
# 요청을 파일로 넘기면 Mailbox 터미널의 mailbox-relay.sh가 실행하고, 출력과 종료 코드를 그대로 돌려준다.
# 릴레이가 안 돌면 exit 3, MB_TIMEOUT(초, 기본 300)을 넘기면 exit 124.

. "$(dirname "$0")/lib.sh"
[ $# -gt 0 ] || { echo "usage: mb.sh <orchestration 하위 명령> [인자...]" >&2; exit 2; }
relay_alive || { echo "mb.sh: Mailbox 릴레이가 돌지 않는다. /clear하면 SessionStart 훅이 다시 띄운다." >&2; exit 3; }

id="$mailbox_dir/$(date +%s%N)-$$"
printf '%s\0' "$@" > "$id.new" && mv "$id.new" "$id.req" || exit 1
end=$(( SECONDS + ${MB_TIMEOUT:-300} ))
until [ -e "$id.out" ]; do
  [ "$SECONDS" -lt "$end" ] || { echo "mb.sh: ${MB_TIMEOUT:-300}초 안에 결과가 없다 ($id)" >&2; exit 124; }
  sleep 0.1
done
cat "$id.out"
code="$(cat "$id.code" 2>/dev/null)"
rm -f "$id.out" "$id.code"
exit "${code:-1}"
