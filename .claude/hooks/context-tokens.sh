#!/usr/bin/env bash
# 현재 컨텍스트 토큰 수를 출력한다.
# 마지막 메인 assistant 응답의 usage(input + cache read + cache creation) 합이다.
# 사용: context-tokens.sh <transcript.jsonl>  또는  훅 입력 JSON을 stdin으로 (transcript_path를 읽음)
# 값을 못 구하면 exit 1.

node -e '
  const fs = require("fs");
  const measure = p => {
    if (!p) process.exit(1);
    const fd = fs.openSync(p, "r"), size = fs.fstatSync(fd).size;
    const len = Math.min(size, 1 << 20), buf = Buffer.alloc(len);
    fs.readSync(fd, buf, 0, len, size - len);
    const lines = buf.toString("utf8").split("\n");
    for (let i = lines.length - 1; i >= 0; i--) {
      let e; try { e = JSON.parse(lines[i]); } catch { continue; }
      const u = e.type === "assistant" && !e.isSidechain && e.message && e.message.usage;
      if (!u) continue;
      console.log((u.input_tokens || 0) + (u.cache_read_input_tokens || 0) + (u.cache_creation_input_tokens || 0));
      return;
    }
    process.exit(1);
  };
  try {
    if (process.argv[1]) measure(process.argv[1]);
    else { let s = ""; process.stdin.on("data", d => s += d).on("end", () => {
      try { measure(JSON.parse(s).transcript_path); } catch { process.exit(1); } }); }
  } catch { process.exit(1); }
' "$1"
