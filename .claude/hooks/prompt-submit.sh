#!/usr/bin/env bash
# UserPromptSubmit 훅 (Hub, Harness만): 컨텍스트가 상한을 넘었으면 인수인계 예고를 주입한다.

. "$(dirname "$0")/lib.sh"
cat > /dev/null
handoff_role || exit 0
over="$handoff_dir/$term.over"
[ -f "$over" ] || exit 0
[ -f "$handoff_dir/$term.flag" ] && exit 0

cat <<EOF
[인수인계 예고] 컨텍스트가 $(cat "$over")토큰으로 상한 ${token_limit}을 넘었다.
이번 요청은 그대로 처리한다. 작업 단위가 끝나는 응답에서 인수인계를 남긴다. 설계 논의 중이면 지금 쟁점이 정리될 때까지 미룬다.
인수인계 방법: handoff 스킬 형식으로 \`.claude/handoff/$role.md\`를 쓰고, 빈 파일 \`.claude/handoff/$term.flag\`를 만든다.
그 응답이 끝나면 훅이 /clear를 보내고, 새 세션에 인수인계를 주입한다. 사용자에게는 한 줄로만 알린다.
EOF
exit 0
