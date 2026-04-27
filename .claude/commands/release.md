다음 단계를 순서대로 실행하여 GitHub Release를 생성하라.

## 0. 환경 주의사항

- **scm_breeze 충돌**: `git` 명령은 `/usr/bin/git` 절대경로 사용
- **gh CLI 필수**: `gh auth status`로 인증 상태 확인. 미인증이면 사용자에게 안내 후 중단

## 1. 변경 내역 확인

현재 브랜치가 develop인지 확인한다. 아니면 develop으로 전환한다.

```bash
/usr/bin/git checkout develop
/usr/bin/git pull origin develop
/usr/bin/git fetch origin main
```

main 대비 develop에 새 커밋이 있는지 확인한다:
```bash
/usr/bin/git log origin/main..origin/develop --oneline
```

- 새 커밋이 없으면: "main에 머지할 변경사항이 없습니다"를 알리고 **중단**
- 새 커밋이 있으면: 변경 내역을 사용자에게 보여주고 Step 2로 진행

## 2. 버전 결정 + changelog.md 작성

### 2-1. 이전 태그 이후 변경 내역 분석

```bash
LATEST_TAG=$(/usr/bin/git describe --tags --abbrev=0 2>/dev/null || echo "v0.0.0")
/usr/bin/git log "$LATEST_TAG"..origin/develop --oneline
/usr/bin/git diff --shortstat "$LATEST_TAG"..origin/develop
```

커밋 목록을 분석하여 카테고리별로 분류한다:
- **Added**: `feat` 접두사 커밋
- **Changed**: `refactor`, `perf`, `style`, `docs`, `chore`, `ci` 접두사 커밋
- **Fixed**: `fix` 접두사 커밋

### 2-2. 버전 결정

시맨틱 버전 규칙:
- `feat` 커밋이 있으면 **minor** bump (0.2.0 → 0.3.0)
- `feat`이 없으면 **patch** bump (0.2.0 → 0.2.1)
- 모든 버전에 `-beta` 접미사 붙임

결정된 버전을 사용자에게 보여주고, 변경을 원하면 수정할 수 있도록 한다.

### 2-3. changelog.md 업데이트

`docs/04-report/changelog.md`를 읽고, 기존 최상단 `## [x.x.x...]` 섹션 **위에** 새 버전 섹션을 삽입한다.

형식 (기존 changelog.md 스타일과 동일하게):
```markdown
## [x.x.x-beta] - YYYY-MM-DD

한 줄 요약. N files changed, +N / -N lines.

### Added

- **기능명** — PR #N
  - 세부 변경 1
  - 세부 변경 2

### Changed

- **변경 항목** — PR #N
  - 세부 변경

### Fixed

- 수정 내용

### Statistics

| 항목 | 수치 |
|------|------|
| 변경 파일 | N |
| 추가 라인 | +N |
| 삭제 라인 | -N |
| 머지된 PR | #N ~ #N (N개) |
```

규칙:
- 커밋 메시지를 그대로 나열하지 말고, **의미 단위로 그룹핑**하여 사람이 읽기 좋게 작성
- PR 번호가 있으면 포함
- 빈 카테고리(해당 커밋이 없는)는 생략
- Statistics의 파일/라인 수치는 `git diff --shortstat`에서 가져옴
- 작성 후 사용자에게 changelog 초안을 보여주고 확인을 받는다
- 승인 후 파일에 저장하고, develop에 커밋 + push 한다

## 3. develop → main PR 머지 진행

Step 1에서 생성한 PR을 머지한다 (changelog 커밋이 포함된 상태).

만약 Step 1에서 PR을 아직 생성하지 않았다면 (changelog 작성이 먼저 필요했으므로), 이 시점에 PR을 생성하고 머지한다.

```bash
gh pr merge <PR번호> --merge
/usr/bin/git checkout main
/usr/bin/git pull origin main
```

## 4. 릴리즈 노트 생성 (changelog → GitHub Release)

changelog.md에서 추출한 본문을 기반으로 `/tmp/release-notes.md`를 생성한다.

형식:
```markdown
## 지금이니?! v{version}

{changelog.md에서 추출한 본문 — ### Added, ### Changed, ### Fixed, ### Statistics 등 그대로 유지}

**Full Changelog**: https://github.com/wonseok-han/ai-stock-advisor/compare/{이전태그}...v{version}
```

- changelog.md 본문을 **있는 그대로** 사용한다 (재작성/요약 금지)
- `**Full Changelog**` 링크만 하단에 추가한다
- 이전 태그는 `/usr/bin/git describe --tags --abbrev=0`으로 가져온다

## 5. 사용자 확인

릴리즈 전 아래 내용을 보여주고 확인을 받는다:
- 릴리즈 버전: `v{version}`
- 현재 브랜치 (main이 아니면 경고)
- 릴리즈 노트 미리보기 (핵심 내용 요약)

## 6. 태그 생성 및 릴리즈

```bash
/usr/bin/git tag -a "v{version}" -m "v{version}"
/usr/bin/git push origin "v{version}"
gh release create "v{version}" \
  --title "v{version}" \
  --notes-file /tmp/release-notes.md \
  --latest
```

## 7. 완료 확인

- `gh release view v{version}`으로 릴리즈 생성 확인
- 릴리즈 URL을 사용자에게 출력

## 8. 주의사항

- **changelog.md가 SoR**: 릴리즈 노트를 git 커밋에서 생성하지 않는다. 반드시 changelog.md 기반
- main 브랜치가 아닌 곳에서 실행하면 경고하고 사용자 확인 후 진행
- `--prerelease` 플래그는 사용하지 않음 (beta 접미사만으로 충분)
- 이미 존재하는 태그와 충돌하면 사용자에게 알리고 중단
- push는 태그만 한다 (코드 push 아님)
