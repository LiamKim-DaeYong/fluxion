#!/usr/bin/env bash
# SessionStart 훅: 오르카 워크트리 표시 이름으로 역할을 판별해 .claude/roles/<role>.md를 주입한다.
# Hub, Harness는 /clear 전에 남긴 인수인계가 있으면 함께 주입한다.
# Hub면 Mailbox 터미널과 릴레이를 확인하고(없으면 띄우고) 우편함 Run 등 현재 값을 덧붙인다.
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
  if [ -f "$handoff_dir/$term.clearing" ]; then
    rm -f "$handoff_dir/$term.clearing" "$handoff_dir/$term.restore"
    if [ -s "$handoff_dir/$role.md" ]; then
      printf '\n## 인수인계 (/clear 전 세션이 남김)\n\n'
      cat "$handoff_dir/$role.md"
    fi
  fi
fi
[ "$role" = hub ] || exit 0


# --- Hub 우편함 부트스트랩 ---
# coordinator는 Mailbox 터미널이다. orca는 명령을 실행한 터미널을 인증하므로 Hub는 Mailbox 이름으로 직접 명령할 수 없다.
# Mailbox에서 mailbox-relay.sh가 돌고 Hub는 mb.sh로 요청한다. Run 묶기도 릴레이가 시작할 때 한다.
wt_id="$(printf '%s' "$wt_json" | jq_js 'r.worktree.id')" || exit 0
tmp="$(mktemp -d)" || exit 0
trap 'rm -rf "$tmp"' EXIT

orca terminal list --json > "$tmp/terms" 2>/dev/null &
orca orchestration run-list --json > "$tmp/runs" 2>/dev/null &
orca terminal list --worktree name:Harness --json > "$tmp/harness" 2>/dev/null &
wait

# Mailbox 판별: Hub 워크트리의 에이전트가 아닌 터미널 중
# 1) 제목이 Mailbox이거나 시작 문구가 보이는 터미널(Run의 coordinator인 것 우선), 2) 없으면 Run의 coordinator인 터미널.
# git-bash 프롬프트가 탭 제목을 덮어쓸 수 있어서 제목만으로는 찾지 않는다.
read -r mailbox run < <(WT_ID="$wt_id" node -e '
  const fs = require("fs"), d = process.argv[1];
  const load = f => { try { const j = JSON.parse(fs.readFileSync(d + "/" + f, "utf8")); return j.ok ? j.result : null; } catch { return null; } };
  const tl = load("terms"), rl = load("runs");
  if (!tl || !rl) { console.log("ERR -"); process.exit(0); }
  const terms = (tl.terminals || [])
    .filter(t => t.worktreeId === process.env.WT_ID && !t.agentIdentity && t.connected !== false);
  const runs = (rl.runs || []).filter(r => !r.legacy && r.coordinator_handle);
  const runOf = h => runs.find(r => r.coordinator_handle === h);
  const marked = terms.filter(t => t.title === "Mailbox" || (t.preview || "").includes("Hub mailbox"));
  const mb = marked.find(t => runOf(t.handle)) || marked[0] || terms.find(t => runOf(t.handle));
  const run = mb && runOf(mb.handle);
  console.log((mb ? mb.handle : "-") + " " + (run ? run.id : "-"));
' "$tmp")

if [ "$mailbox" = ERR ] || [ -z "$mailbox" ]; then
  echo; echo "(SessionStart 훅: 오르카 목록 조회 실패. 우편함 확인 생략)"; exit 0
fi
dry() { [ "$SESSION_HOOK_DRY_RUN" = 1 ] && echo "(dry-run: $1 생략)" >&2; }

relay="bash .claude/hooks/mailbox-relay.sh"
if [ "$mailbox" = "-" ]; then
  dry "Mailbox 터미널 생성" && exit 0
  rm -f "$mailbox_dir/alive"; run="-"
  mailbox="$(orca terminal create --worktree current --title Mailbox --shell git-bash --command "$relay" --json 2>/dev/null \
    | jq_js "$(find_str '/^term_/')")"
  [ -n "$mailbox" ] || { echo; echo "(SessionStart 훅: Mailbox 터미널을 만들지 못했다. 수동 확인 필요)"; exit 0; }
elif ! relay_alive || [ "$run" = "-" ]; then
  # 예전 형식(cat > /dev/null), 릴레이가 멈춘 Mailbox, 묶인 Run이 없는 Mailbox: 중단하고 그 터미널에서 릴레이를 다시 띄운다.
  dry "Mailbox 릴레이 재시작" && exit 0
  orca terminal send --terminal "$mailbox" --interrupt --json >/dev/null 2>&1
  orca terminal send --terminal "$mailbox" --text "exec $relay" --enter --json >/dev/null 2>&1
  rm -f "$mailbox_dir/alive"; run="-"
fi
# 릴레이를 새로 띄웠을 때만: 릴레이가 Run을 묶고 alive를 갱신하기를 기다려 묻는다.
if [ "$run" = "-" ]; then
  for _ in $(seq 50); do relay_alive && break; sleep 0.1; done
  run="$(MB_TIMEOUT=2 bash "$harness_dir/hooks/mb.sh" run-current --json 2>/dev/null | jq_js 'r.run.id')"
fi

harness="$(jq_js '(r.terminals||[]).filter(t=>t.agentIdentity).map(t=>t.handle)[0]' < "$tmp/harness" 2>/dev/null)"

# 안 읽은 메시지: inbox 읽기는 Hub에서 된다. heartbeat는 뺀다.
unread=""
[ -n "$run" ] && unread="$(orca orchestration inbox --terminal "run:$run" --limit 200 --json 2>/dev/null \
  | jq_js '(r.messages||[]).filter(m=>!m.read&&m.type!=="heartbeat").length')"

cat <<EOT

## 현재 세션 값 (SessionStart 훅이 찾음)
- 우편함 Run: ${run:-찾지 못함 (Mailbox 터미널 확인)}
- Mailbox 터미널: $mailbox
- Harness 터미널: ${harness:-없음}
- 안 읽은 메시지: ${unread:-?}건 (heartbeat 제외)
EOT
exit 0
