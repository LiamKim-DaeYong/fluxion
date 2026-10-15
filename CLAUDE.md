# CLAUDE.md

## 프로젝트
Fluxion — 실시간 시세/체결 데이터 수집·처리 플랫폼.
Kotlin / Java 25 / Spring Boot 4.1.1 / Spring Modulith 2.1.1 / JPA+Hibernate /
PostgreSQL 18 / Flyway / Gradle Kotlin DSL / Docker Compose / Micrometer+Actuator.
Kafka, Redis, AWS는 실제 문제가 확인된 뒤에만 단계적으로 도입한다. 처음부터 마이크로서비스로 쪼개지 않는다.

## 역할 분담
- **코드 작성은 개발자가 직접 한다.** Claude는 애플리케이션 코드/인프라 설정을 명시적 요청 없이 작성·수정하지 않는다.
- **설계 판단과 논의는 함께 한다.** 완성 코드보다 개념·대안·트레이드오프를 먼저 제시하고, 최종 결정은 개발자가 내린다.
- **반복적인 GitHub 작업("노가다")은 Claude가 주도한다.** 설계 논의가 한 단위로 마무리되면, 별도 확인 없이 바로 Issue를 생성한다 (Issue Template 사용). 단, Issue Close / PR 생성·병합 / Git commit·push는 매번 승인을 받는다.
- GitHub 라벨은 최소 체계(`type:feature` / `type:bug` / `type:infra`)로 유지하고, 실제로 여러 이슈가 쌓여 우선순위 구분이 필요해질 때 `priority:*`를 추가한다.

## 문서 체계 (과하게 만들지 않기)
- `README.md`, `.github/ISSUE_TEMPLATE/*`, `.github/PULL_REQUEST_TEMPLATE.md`만 먼저 유지한다.
- `docs/adr/`, `docs/BLOGGING.md`, `docs/blog/`는 실제로 ADR 또는 블로그 후보가 생겼을 때 그 시점에 만든다. 미리 빈 폴더를 만들지 않는다.
- ADR은 "문제 — 대안 비교 — 선택과 이유 — 수용한 단점 — 검증 방법" 구조로 작성한다.

## 품질 기준
- 구현 전 완료 조건과 실패 시나리오를 정의한다.
- 데이터 정합성 관련 기능은 자동화 테스트(가능하면 Testcontainers) 우선.
- 성능 실험은 조건(하드웨어/부하/데이터량/설정)을 기록하고, 개선 전후를 동일 조건에서 비교한다.
- 실험하지 않은 성능 수치나 겪지 않은 장애를 경험처럼 작성하지 않는다.

## 세션 관리
- 대화 컨텍스트가 길어지면(긴 디버깅, 여러 주제 전환 등) Claude가 먼저 `/clear` 또는 새 세션 시작을 권장한다.

## Git / GitHub
- 흐름: Issue → 설계 논의 → Branch → 구현(개발자) → Test → PR → Review → Merge.
- 브랜치 전략, 커밋 메시지 규칙(Conventional Commits + 이슈 번호), PR/머지 규칙은 `CONTRIBUTING.md` 참고.
- `main`은 Squash merge만 허용, 머지 후 브랜치 자동 삭제로 설정되어 있다.
