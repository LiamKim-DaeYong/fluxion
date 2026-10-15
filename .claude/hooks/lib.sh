# 훅 공통: 경로, 설정값, JSON 헬퍼. 각 훅이 source한다.

harness_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
handoff_dir="$harness_dir/handoff"
project_dir="${CLAUDE_PROJECT_DIR:-$PWD}"
command -v cygpath >/dev/null 2>&1 && project_dir="$(cygpath -u "$project_dir")"

# 인수인계 임계치(토큰). settings의 env 또는 환경변수 HANDOFF_TOKEN_LIMIT로 바꾼다.
token_limit="${HANDOFF_TOKEN_LIMIT:-150000}"
# 이 세션이 도는 오르카 터미널. 플래그 파일 이름에 써서 같은 워크트리의 다른 세션과 섞이지 않게 한다.
term="${ORCA_TERMINAL_HANDLE:-}"

# stdin의 orca JSON에서 result에 JS 식($1)을 적용해 출력한다. ok가 아니거나 파싱 실패면 exit 1.
jq_js() {
  node -e '
    let s = ""; process.stdin.on("data", d => s += d).on("end", () => {
      try {
        const j = JSON.parse(s); if (!j.ok) process.exit(1);
        const v = (new Function("r", "return " + process.argv[1]))(j.result);
        if (v != null && v !== "") console.log(v);
      } catch (e) { process.exit(1); }
    });' "$1"
}

# 객체 안에서 정규식에 맞는 첫 문자열 값을 찾는 JS 식 (생성 명령의 응답 형식에 의존하지 않으려고)
find_str() { echo "(function f(o){if(typeof o==='string')return ${1}.test(o)?o:null;if(o&&typeof o==='object')for(const k in o){const v=f(o[k]);if(v)return v}return null})(r)"; }

# SessionStart가 기록한 역할(hub, harness, worker). 인수인계 대상이면 0을 반환한다.
handoff_role() {
  role="$(cat "$handoff_dir/role" 2>/dev/null)"
  [ -n "$term" ] && { [ "$role" = hub ] || [ "$role" = harness ]; }
}
