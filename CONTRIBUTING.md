# Contributing

Fluxion은 1인 개발 + Claude Code 협업 프로젝트지만, 포트폴리오로서 실제 팀 워크플로우를 따른다.

## 브랜치 전략

```
<type>/<issue-number>-<short-description>
```

- `type`: `feature` / `fix` / `infra` / `refactor` / `docs` / `test`
- 예: `feature/12-websocket-reconnect`, `fix/18-duplicate-trade-id`, `infra/21-prometheus-setup`
- `master`에 직접 커밋하지 않는다. 모든 변경은 PR을 통해 들어온다.

## 커밋 메시지

[Conventional Commits](https://www.conventionalcommits.org/)를 따른다.

```
<type>(<scope>): <subject>

<body (선택)>

Refs #<issue-number>
```

- `type`: `feat`, `fix`, `chore`, `refactor`, `docs`, `test`, `perf`
- 이슈와 연결되는 커밋은 본문에 `Refs #<번호>` 또는 PR에서 `Closes #<번호>`를 사용한다.
- 예: `feat(ingestion): add upbit websocket client (Refs #12)`

## PR 규칙

- PR 템플릿(`.github/PULL_REQUEST_TEMPLATE.md`)을 채운다.
- 관련 Issue를 `Closes #<번호>`로 연결한다 — 머지 시 이슈가 자동으로 닫힌다.
- 머지 방식은 **Squash merge만 허용**한다 (커밋 히스토리를 깔끔하게 유지).
- 머지 후 브랜치는 자동으로 삭제된다 (저장소 설정: `delete branch on merge`).

## 이슈

- Issue Template(`.github/ISSUE_TEMPLATE/`)을 사용한다 — Feature / Bug / Infra.
- 라벨: `type:feature`, `type:bug`, `type:infra` (필요 시 `priority:*` 추가).
