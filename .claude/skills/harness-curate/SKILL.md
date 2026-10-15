---
name: harness-curate
description: 하네스 파일(CLAUDE.md, .claude/ 아래 역할·스킬·출력 스타일·훅, 프로젝트 메모리)의 크기를 재고 중복과 낡은 내용을 정리한다. Harness가 하네스 파일을 고칠 때마다, 또는 크기·중복 점검을 요청받았을 때 쓴다.
---

# harness-curate

하네스 파일을 작고 중복 없이 유지하는 점검 절차다. 파일을 고친 뒤 매번 끝까지 돈다.

## 대상과 상한

| 대상 | 경로 | 상한 |
|---|---|---|
| 프로젝트 규칙 | `CLAUDE.md` | 100줄 |
| 역할 파일 | `.claude/roles/*.md` | 150줄 |
| 스킬 | `.claude/skills/*/SKILL.md` | 150줄 |
| 출력 스타일 | `.claude/output-styles/*.md` | 150줄 |
| 훅 스크립트 | `.claude/hooks/*` | 실행 2초 이내 |
| 메모리 색인 | `C:/Users/clown/.claude/projects/E--Project-Fluxion/memory/MEMORY.md` | 50줄 |
| 메모리 파일 | 같은 폴더의 나머지 `*.md` | 30줄 |

메모리는 읽고 비교만 한다. 수정과 삭제는 Hub에 제안한다 (`~/.claude` 수정 금지).

## 1. 크기 측정

```sh
wc -l CLAUDE.md .claude/roles/*.md .claude/skills/*/SKILL.md .claude/output-styles/*.md
wc -l C:/Users/clown/.claude/projects/E--Project-Fluxion/memory/*.md
time bash .claude/hooks/session-start.sh > /dev/null
```

상한을 넘으면 이 순서로 줄인다.
1. 중복 제거 (2단계)
2. 가끔 쓰는 절차를 스킬로, 역할별 절차를 역할 파일로 옮김
3. 문장 압축

## 2. 중복 탐지와 병합

1. 이번에 고친 파일의 규칙마다 핵심어 두세 개를 뽑아 전체 대상에서 찾는다.
   ```sh
   grep -rn "<핵심어>" CLAUDE.md CONTRIBUTING.md .claude/ C:/Users/clown/.claude/projects/E--Project-Fluxion/memory/
   ```
2. 같은 사실이 두 곳 이상에 있으면 아래 기준으로 남길 곳을 하나 정한다.

   | 내용 | 둘 곳 |
   |---|---|
   | 모든 세션에 매번 필요한 규칙 | `CLAUDE.md` |
   | 브랜치, 커밋, PR, 작성 언어 규칙 | `CONTRIBUTING.md` |
   | 역할별 절차와 명령 | `.claude/roles/<역할>.md` |
   | 가끔 쓰는 절차 | 스킬 |
   | 답변 말투와 형식 | 출력 스타일 |
   | 레포로 알 수 없는 사용자 배경, 선호, 진행 맥락 | 메모리 |
   | Run ID, 터미널 handle 같은 세션별 값 | 적지 않음 (훅이 찾음) |

3. 남길 곳에 내용을 합치고 나머지에서는 지운다. 다른 파일이 그 규칙을 알아야 하면 한 줄 참조(`CONTRIBUTING.md 참고`)만 남긴다.
4. 지울 곳이 메모리면 직접 고치지 않고 제안 목록에 넣는다.

## 3. 낡은 내용 삭제

- 경로, 명령, 플래그가 아직 있는지 확인한다 (`ls`, `orca <cmd> --help`). 없으면 지우거나 고친다.
- 끝난 작업의 진행 상태, 이미 검증한 "다음 단계", 바뀐 결정은 지운다.
- 날짜가 붙은 현재 값(ID, handle, 상태)은 지운다.

## 4. 문체 점검

출력 스타일 `concise-korean.md`의 금지 규칙을 지켰는지 본다. 출력 스타일 파일은 예시로 금지어를 담고 있으니 빼고 검사한다.

```sh
grep -nE '에 대해|를 통해|에 있어|되어진|결론적으로|요약하면|—' CLAUDE.md .claude/roles/*.md .claude/skills/*/SKILL.md
```

## 5. 보고

worker_done 본문에 다음을 넣는다.
- 상한 초과 파일 (없으면 "초과 없음")
- 정리한 중복과 옮긴 위치
- 메모리 수정 제안: 파일명, 항목, 이유 (삭제/이동)
- 훅 실행 시간
