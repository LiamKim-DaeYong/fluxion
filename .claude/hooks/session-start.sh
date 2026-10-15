#!/usr/bin/env bash
# SessionStart 훅: 오르카 워크트리 표시 이름으로 역할을 판별해 .claude/roles/<role>.md를 주입한다.
# Hub, Harness는 /clear 전에 남긴 인수인계가 있으면 함께 주입한다.
# Hub면 Mailbox 터미널과 우편함 Run을 찾고(없으면 만들고) 현재 값을 덧붙인다.
# 오르카나 node가 없거나 호출이 실패하면 아무것도 출력하지 않고 exit 0.
# SESSION_HOOK_DRY_RUN=1이면 터미널과 Run을 만들지 않는다 (테스트용).

. "$(dirname "$0")/lib.sh"
cat > /dev/null
cd "$project_dir" 2>/dev/null || exit 0
command -v orca >/dev/null 2>&1 || exit 0
command -v node >/dev/null 2>&1 || exit 0

wt_json="$(orca worktree current --json 2>/dev/null)" || exit 0
name="$(printf '%s' "$wt_json" | jq_js 'r.worktree.displayName')" || exit 0

case "$name" in
  Hub) role=hub ;;
  Harness) role=harness ;;
  *) role=worker ;;
esac

role_file="$harness_dir/roles/$role.md"
[ -f "$role_file" ] || exit 0
cat "$role_file"

# Stop, UserPromptSubmit 훅이 orca를 다시 부르지 않도록 역할을 기록한다.
mkdir -p "$handoff_dir" && echo "$role" > "$handoff_dir/role"
if [ -n "$term" ]; then
  rm -f "$handoff_dir/$term.over"
  if [ -f "$handoff_dir/$term.restore" ]; then
    rm -f "$handoff_dir/$term.restore"
    if [ -s "$handoff_dir/$role.md" ]; then
      printf '\n## 인수인계 (/clear 전 세션이 남김)\n\n'
      cat "$handoff_dir/$role.md"
    fi
  fi
fi
[ "$role" = hub ] || exit 0

# --- Hub 우편함 부트스트랩 ---
wt_id="$(printf '%s' "$wt_json" | jq_js 'r.worktree.id')" || exit 0
tmp="$(mktemp -d)" || exit 0
trap 'rm -rf "$tmp"' EXIT

orca terminal list --json > "$tmp/terms" 2>/dev/null &
orca orchestration run-list --json > "$tmp/runs" 2>/dev/null &
orca terminal list --worktree name:Harness --json > "$tmp/harness" 2>/dev/null &
wait

# Mailbox 판별: Hub 워크트리의 에이전트가 아닌 터미널 중
# 1) 제목이 Mailbox이거나 시작 문구가 보이는 터미널, 2) 그런 터미널이 없으면 Run의 coordinator인 터미널.
# git-bash 프롬프트가 탭 제목을 덮어쓸 수 있어서 제목만으로는 찾지 않는다.
# orphan: coordinator 터미널이 사라진 "Hub mailbox" Run. Mailbox를 새로 만들면 여기에 다시 묶어 Run ID를 유지한다.
read -r mailbox run orphan < <(WT_ID="$wt_id" node -e '
  const fs = require("fs"), d = process.argv[1];
  const load = f => { try { const j = JSON.parse(fs.readFileSync(d + "/" + f, "utf8")); return j.ok ? j.result : null; } catch { return null; } };
  const tl = load("terms"), rl = load("runs");
  if (!tl || !rl) { console.log("ERR ERR ERR"); process.exit(0); }
  const live = new Set((tl.terminals || []).map(t => t.handle));
  const terms = (tl.terminals || [])
    .filter(t => t.worktreeId === process.env.WT_ID && !t.agentIdentity && t.connected !== false);
  const runs = (rl.runs || []).filter(r => !r.legacy && r.coordinator_handle);
  const marked = terms.filter(t => t.title === "Mailbox" || (t.preview || "").includes("Hub mailbox"));
  const runOf = h => runs.find(r => r.coordinator_handle === h);
  let mb = marked.find(t => runOf(t.handle)) || marked[0];
  if (!mb) mb = terms.find(t => runOf(t.handle));
  const run = mb && runOf(mb.handle);
  const orphan = runs.find(r => /^Hub mailbox/.test(r.objective || "") && !live.has(r.coordinator_handle));
  console.log([mb ? mb.handle : "-", run ? run.id : "-", orphan ? orphan.id : "-"].join(" "));
' "$tmp")

if [ "$mailbox" = ERR ] || [ -z "$mailbox" ]; then
  echo; echo "(SessionStart 훅: 오르카 목록 조회 실패. 우편함 확인 생략)"; exit 0
fi
dry() { [ "$SESSION_HOOK_DRY_RUN" = 1 ] && echo "(dry-run: $1 생성 생략)" >&2; }

if [ "$mailbox" = "-" ]; then
  dry "Mailbox 터미널 (재연결할 Run: $orphan)" && exit 0
  mailbox="$(orca terminal create --worktree current --title Mailbox --shell git-bash \
    --command "echo 'Hub mailbox: nudge sink. Do not type here.'; cat > /dev/null" --json 2>/dev/null \
    | jq_js "$(find_str '/^term_/')")"
  run="-"
fi
[ -n "$mailbox" ] || { echo; echo "(SessionStart 훅: Mailbox 터미널을 만들지 못했다. 수동 확인 필요)"; exit 0; }

if [ -z "$run" ] || [ "$run" = "-" ]; then
  dry "우편함 Run" && exit 0
  [ "$orphan" != "-" ] && orca orchestration run-use --id "$orphan" --from "$mailbox" --json 2>/dev/null \
    | jq_js 'true' >/dev/null && run="$orphan"
fi
if [ -z "$run" ] || [ "$run" = "-" ]; then
  run="$(orca orchestration run-create --objective "Hub mailbox" --from "$mailbox" --json 2>/dev/null \
    | jq_js "$(find_str '/^run_/')")"
fi

harness="$(jq_js '(r.terminals||[]).filter(t=>t.agentIdentity).map(t=>t.handle)[0]' < "$tmp/harness" 2>/dev/null)"

# 안 읽은 메시지: check --peek이 우선. 다른 터미널이라 막히면(consumer_fenced) inbox로 센다. heartbeat는 뺀다.
# 두 조회를 동시에 돌려 시간을 줄인다.
unread=""
if [ -n "$run" ]; then
  orca orchestration check --run "$run" --terminal "$mailbox" --peek --json > "$tmp/peek" 2>/dev/null &
  orca orchestration inbox --terminal "run:$run" --limit 200 --json > "$tmp/inbox" 2>/dev/null &
  wait
  unread="$(jq_js '(r.messages||[]).filter(m=>m.type!=="heartbeat").length' < "$tmp/peek")" \
  || unread="$(jq_js '(r.messages||[]).filter(m=>!m.read&&m.type!=="heartbeat").length' < "$tmp/inbox")"
fi

cat <<EOF

## 현재 세션 값 (SessionStart 훅이 찾음)
- 우편함 Run: ${run:-찾지 못함}
- Mailbox 터미널: $mailbox
- Harness 터미널: ${harness:-없음}
- 안 읽은 메시지: ${unread:-?}건 (heartbeat 제외)
EOF
exit 0
