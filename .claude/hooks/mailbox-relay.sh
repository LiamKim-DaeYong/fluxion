#!/usr/bin/env bash
# Mailbox 터미널에서 도는 릴레이. Hub가 mb.sh로 넣은 요청을 이 터미널 이름으로 `orca orchestration <요청>`으로 실행한다.
# orca는 명령을 실행한 터미널을 인증해 coordinator를 판별하고 --from으로 다른 터미널을 대신할 수 없다.
# 그래서 Run 생성, worker-start, check/ack, reply는 coordinator인 이 터미널 안에서 실행해야 한다.
# 시작할 때 Run을 묶는다: 이미 묶였으면 그대로, 없으면 coordinator가 사라진 "Hub mailbox" Run을 다시 쓰고, 그것도 없으면 만든다.
# 키 입력은 읽어서 버린다. SessionStart 훅이 이 터미널을 만들거나 기존 Mailbox를 이 스크립트로 바꾼다.

. "$(dirname "$0")/lib.sh"
mkdir -p "$mailbox_dir" || exit 1
echo "Hub mailbox: orchestration relay. Do not type here."

if ! orca orchestration run-current --json 2>/dev/null | jq_js 'r.run.id' >/dev/null; then
  orphan="$(node -e '
    const { execFileSync } = require("child_process");
    const get = a => { const j = JSON.parse(execFileSync("orca", a, { encoding: "utf8", shell: true })); return j.ok ? j.result : {}; };
    const live = new Set((get(["terminal", "list", "--json"]).terminals || []).map(t => t.handle));
    const runs = (get(["orchestration", "run-list", "--json"]).runs || [])
      .filter(r => !r.legacy && /^Hub mailbox/.test(r.objective || "") && !live.has(r.coordinator_handle))
      .sort((a, b) => (b.updated_at || "").localeCompare(a.updated_at || ""));
    if (runs[0]) console.log(runs[0].id);' 2>/dev/null)"
  if [ -n "$orphan" ]; then
    orca orchestration run-use --id "$orphan" --json > /dev/null 2>&1
  else
    orca orchestration run-create --objective "Hub mailbox" --json > /dev/null 2>&1
  fi
fi
orca orchestration run-current --json 2>/dev/null | jq_js 'r.run.id'

# 요청: <id>.req (NUL로 구분한 인자). 결과: <id>.code(종료 코드) 다음 <id>.out(출력).
# 명령이 도는 동안에도 alive를 갱신해 긴 worker-start 중에 다른 요청이 릴레이를 죽었다고 보지 않게 한다.
idle() { read -r -t 0.3 _; [ $? -eq 1 ] && sleep 0.3; touch "$mailbox_dir/alive"; }
while :; do
  idle
  for req in "$mailbox_dir"/*.req; do
    [ -e "$req" ] || continue
    id="${req%.req}"
    mapfile -d '' -t args < "$req"
    rm -f "$req"
    orca orchestration "${args[@]}" > "$id.tmp" 2>&1 &
    pid=$!
    while kill -0 "$pid" 2>/dev/null; do idle; done
    wait "$pid"; echo $? > "$id.code"
    mv "$id.tmp" "$id.out"
  done
done
