# CLAUDE.md

## 프로젝트
Fluxion은 실시간 시세/체결 데이터 수집·처리 플랫폼이다.
Kotlin / Java 25 / Spring Boot 4.1.1 / Spring Modulith 2.1.1 / JPA+Hibernate /
PostgreSQL 18 / Flyway / Gradle Kotlin DSL / Docker Compose / Micrometer+Actuator.
Kafka, Redis, AWS는 실제 문제가 확인된 뒤에만 단계적으로 도입한다. 처음부터 마이크로서비스로 쪼개지 않는다.

## 역할 분담
- **코드 작성은 개발자가 직접 한다.** Claude는 애플리케이션 코드/인프라 설정을 명시적 요청 없이 작성·수정하지 않는다.
- **설계는 Claude가 추천안으로 정하고 바로 진행한다.** 되돌리기 어렵거나 비용이 드는 결정만 개발자에게 묻고, 개발자는 언제든 결정을 뒤집을 수 있다. 진행 절차는 `.claude/roles/hub.md` 설계 논의 절 참고.
- **반복적인 GitHub 작업("노가다")은 Claude가 주도한다.** 설계 논의가 한 단위로 마무리되면, 별도 확인 없이 바로 Issue를 생성한다 (Issue Template 사용). 단, Issue Close / PR 생성·병합 / Git commit·push는 매번 승인을 받는다 (예외: `.claude/roles/hub.md` 자율 위임).
- GitHub 라벨은 최소 체계(`type:feature` / `type:bug` / `type:infra`)로 유지하고, 실제로 여러 이슈가 쌓여 우선순위 구분이 필요해질 때 `priority:*`를 추가한다.

## 문서 체계 (과하게 만들지 않기)
- `README.md`, `.github/ISSUE_TEMPLATE/*`, `.github/PULL_REQUEST_TEMPLATE.md`만 먼저 유지한다.
- `docs/adr/`, `docs/BLOGGING.md`, `docs/blog/`는 실제로 ADR 또는 핵심 사례(블로그) 후보가 확정됐을 때 그 시점에 만든다. 미리 빈 폴더를 만들지 않는다.
- 포트폴리오 산출물 목표는 `README.md`와 핵심 사례 3~5개(프로젝트 전체)다. 읽는 순서는 README(1~2분) → 핵심 사례(사례당 5분) → 이슈·PR 원본. 원본은 이슈 코멘트에 두고, 수집과 후보 판단은 `.claude/roles/hub.md` 증거 수집 절을 따른다.
- ADR은 문제, 대안 비교, 선택과 이유, 수용한 단점, 검증 방법 순서로 작성한다.

## 품질 기준
- 구현 전 완료 조건과 실패 시나리오를 정의한다.
- 데이터 정합성 관련 기능은 자동화 테스트(가능하면 Testcontainers) 우선.
- 성능 실험은 조건(하드웨어/부하/데이터량/설정)을 기록하고, 개선 전후를 동일 조건에서 비교한다.
- 실험하지 않은 성능 수치나 겪지 않은 장애를 경험처럼 작성하지 않는다.

## 최신 기준과 조사
- 도구, 라이브러리, 이미지 버전, 설정 키, 규칙 이름은 학습한 지식으로 정하지 않는다. 실제 릴리스 목록, 공식 문서의 최신 버전 페이지, 저장소 활동을 조회해 확인한다.
- 버전은 출시 2주 이상 지난 최신 안정판으로 고정한다. 폐기(deprecated)된 설정 키나 구형 형식은 쓰지 않는다.
- 설계 쟁점의 선택지와 추천은 웹 조사(공식 문서, 릴리스 노트, 최근 1년 안의 실사용 사례, 유명 오픈소스 프로젝트의 설정)로 만든다.
- 설정과 설계는 표준을 따른다. 우선순위는 (1) 공식 표준과 공식 가이드(언어와 프레임워크 공식 문서, 명세), (2) 표준이 없는 부분만 사실상 표준(가장 널리 채택된 기본값)이다. 오픈소스 사례는 참고 자료다.
- 표준에서 벗어나는 커스텀은 최소화하고, 벗어날 때마다 이유를 남긴다.
- 비교 표와 출처(URL, 확인 날짜)는 이슈 본문에 남긴다. 조사로 확인하지 못한 부분은 "미확인"이라고 밝힌다.

## 세션 관리
- 대화 컨텍스트가 길어지면(긴 디버깅, 여러 주제 전환 등) Claude가 먼저 `/clear` 또는 새 세션 시작을 권장한다.

## 멀티 세션 하네스
- 오르카에서 세션을 Hub(사용자와 대화하는 유일한 세션), 고정 모듈(보드 `Always On` 칸, 현재 Harness), 일회성 모듈(`orca orchestration worker-start`로 띄우고 완료 후 release)로 나눠 쓴다.
- 통신은 orchestration Run 큐로 한다. Run의 coordinator는 Mailbox 터미널이라 워커 알림이 Hub 대화에 끼어들지 않는다. Hub는 Mailbox에서 도는 릴레이에 `.claude/hooks/mb.sh`로 명령을 보낸다 (`hub.md` 참고).
- 역할별 절차는 `.claude/roles/<hub|harness|worker>.md`에 있다. SessionStart 훅(`.claude/hooks/session-start.sh`)이 워크트리 표시 이름으로 역할을 판별해 주입하고, Hub에는 Run ID, Mailbox handle, 안 읽은 메시지 건수도 넣는다.
- Hub와 Harness는 컨텍스트가 상한(기본 150k 토큰, `HANDOFF_TOKEN_LIMIT`)을 넘으면 작업 단위가 끝날 때 `handoff` 스킬 형식으로 인수인계를 남긴다. 훅이 `/clear`를 보내고 새 세션에 인수인계를 주입한다. Harness는 상한과 별개로 worker_done마다 clear한다 (`harness.md`).
- Harness는 `CLAUDE.md`와 `.claude/`만 수정한다. 레포 파일은 PR로, 사용자 레벨 설정(`~/.claude`)은 적용 전에 승인을 받는다.
- Run ID, 터미널 handle처럼 세션마다 바뀌는 값은 어떤 파일에도 적지 않는다.

## 하네스 파일 관리
- 크기 상한: `CLAUDE.md` 100줄, `MEMORY.md` 50줄, 메모리 파일 1개 30줄, 출력 스타일·스킬·역할 파일 1개 150줄.
- 한 사실은 한 곳에만 둔다. 레포에 있는 내용은 메모리에 다시 적지 않는다.
- 매번 필요한 규칙만 이 파일에 두고, 역할별 절차는 역할 파일로, 가끔 쓰는 절차는 스킬로 옮긴다.
- 하네스 파일을 고칠 때마다 `harness-curate` 스킬로 크기, 중복, 낡은 내용을 점검한다.

## Git / GitHub
- 흐름: Issue → 설계 논의 → Branch → 구현(개발자) → Test → PR → Review → Merge.
- 브랜치 전략, 커밋 메시지 규칙(Conventional Commits + 이슈 번호), PR/머지 규칙은 `CONTRIBUTING.md` 참고.
- PR을 만들기 전에 `./gradlew ktlintFormat`을 실행한다. 바뀐 파일이 있으면 `style: apply ktlint format`으로 따로 커밋하고 `./gradlew ktlintCheck`를 통과시킨다. `gh pr create`는 PreToolUse 훅(`pr-lint.sh`)이 check 실패 시 막는다.
- `main`은 Squash merge만 허용, 머지 후 브랜치 자동 삭제로 설정되어 있다.
- 머지 후 로컬 정리: 작업 트리 clean과 로컬 tip == PR head 확인 → `git fetch --prune` → `git switch --detach origin/main` (`main`은 E:/Project/Fluxion이 점유) → `git branch -D <브랜치>` (squash라 `-d`는 미머지로 판정) → E:/Project/Fluxion에서 `git pull --ff-only`.
