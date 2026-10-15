#!/usr/bin/env bash
# Stop 훅 (Hub, Harness만): 턴이 끝날 때마다 컨텍스트 토큰을 잰다.
# 상한을 넘으면 <term>.over를 남겨 UserPromptSubmit 훅이 예고하게 한다.
# 인수인계 플래그(<term>.flag)가 있으면 <term>.restore로 바꾸고 /clear를 백그라운드로 예약한다.

. "$(dirname "$0")/lib.sh"
input="$(cat)"
handoff_role || exit 0
mkdir -p "$handoff_dir" || exit 0

if [ -f "$handoff_dir/$term.flag" ]; then
  [ -s "$handoff_dir/$role.md" ] || exit 0
  mv -f "$handoff_dir/$term.flag" "$handoff_dir/$term.restore"
  rm -f "$handoff_dir/$term.over"
  nohup bash "$harness_dir/hooks/clear-later.sh" "$term" >/dev/null 2>&1 </dev/null &
  exit 0
fi

tokens="$(printf '%s' "$input" | bash "$harness_dir/hooks/context-tokens.sh")" || exit 0
if [ "$tokens" -gt "$token_limit" ]; then
  echo "$tokens" > "$handoff_dir/$term.over"
else
  rm -f "$handoff_dir/$term.over"
fi
exit 0
