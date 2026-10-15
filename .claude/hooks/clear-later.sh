#!/usr/bin/env bash
# stop.sh가 백그라운드로 띄운다. 턴이 끝나 TUI가 쉬고 입력창이 비었을 때 /clear를 보낸다.
# 사용자가 입력 중이면 5초 간격으로 약 2분 기다리고, 그래도 안 되면 플래그를 되돌려 다음 Stop 때 다시 시도한다.
# 기다리는 동안 <term>.restore가 사라지면(prompt-submit이 취소) 보내지 않는다.
# 보내기 직전 <term>.clearing으로 바꿔 /clear 자체의 입력이 취소로 처리되지 않게 한다.
# 사용: clear-later.sh <terminal handle>

. "$(dirname "$0")/lib.sh"
t="$1"
[ -n "$t" ] || exit 0
log="$handoff_dir/clear.log"

# 입력창이 비었는지 화면으로 판별한다. 비어 있으면 프롬프트 줄이 '❯'(또는 'Try "..."' 안내문)뿐이고,
# 아래 안내줄에 '← for agents'가 보인다. 글자를 치는 중에는 이 안내가 사라진다.
prompt_empty() {
  orca terminal read --terminal "$t" --screen --json 2>/dev/null | node -e '
    let s = ""; process.stdin.on("data", d => s += d).on("end", () => {
      try {
        const lines = JSON.parse(s).result.terminal.tail.slice(-8);
        const p = lines.map(l => l.trim()).reverse().find(l => l.startsWith("❯"));
        const ok = p !== undefined && /^❯\s*(Try ".*")?$/.test(p) && lines.some(l => l.includes("← for agents"));
        process.exit(ok ? 0 : 1);
      } catch (e) { process.exit(1); }
    });'
}

sleep 2
canceled() { echo "$(date '+%F %T') $t not sent (canceled)" >> "$log"; exit 0; }
for _ in $(seq 1 24); do
  [ -f "$handoff_dir/$t.restore" ] || canceled
  if orca terminal wait --terminal "$t" --for tui-idle --timeout-ms 30000 >/dev/null 2>&1 && prompt_empty; then
    mv -f "$handoff_dir/$t.restore" "$handoff_dir/$t.clearing" 2>/dev/null || canceled
    # MSYS가 "/clear"를 Windows 경로로 바꾸지 않게 한다.
    MSYS_NO_PATHCONV=1 orca terminal send --terminal "$t" --text "/clear" --enter >/dev/null 2>&1
    echo "$(date '+%F %T') $t /clear sent" >> "$log"
    exit 0
  fi
  sleep 5
done

[ -f "$handoff_dir/$t.restore" ] && mv -f "$handoff_dir/$t.restore" "$handoff_dir/$t.flag"
echo "$(date '+%F %T') $t gave up (input busy)" >> "$log"
