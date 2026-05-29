다음 단계를 순서대로 실행하여 **changelog 생성부터 Release PR까지 한 번에** 진행하라.
(`/changelog` + `/pr`(Release PR) 통합 명령)

## 0. 환경 주의사항

- **scm_breeze 충돌**: `git` 명령은 `/usr/bin/git` 절대경로 사용
- **gh CLI 필수**: `gh auth status`로 인증 상태 확인. 미인증이면 사용자에게 안내 후 중단
- **HEREDOC 미동작**: 커밋 메시지는 임시 파일(`/tmp/commit_msg.txt`) 방식 사용

## 1. 사전 확인

```bash
/usr/bin/git branch --show-current
/usr/bin/git fetch origin main
/usr/bin/git fetch origin develop
```

- **develop 브랜치가 아니면** 사용자에게 알리고 develop으로 전환할지 확인
- staged/unstaged 변경이 있으면 경고하고 계속 진행할지 확인
- main 대비 develop에 새 커밋이 있는지 확인:

```bash
/usr/bin/git log origin/main..origin/develop --oneline
```

- 새 커밋이 없으면: "main 대비 변경사항이 없습니다" 알리고 **중단**

## 2. 버전 결정 + changelog 파일 작성

### 2-1. 이전 태그 이후 변경 내역 분석

```bash
LATEST_TAG=$(/usr/bin/git describe --tags --abbrev=0 2>/dev/null || echo "v0.0.0")
/usr/bin/git log "$LATEST_TAG"..origin/develop --oneline
/usr/bin/git diff --shortstat "$LATEST_TAG"..origin/develop
```

커밋 목록을 카테고리별로 분류한다:
- **Added**: `feat` 접두사 커밋
- **Changed**: `refactor`, `perf`, `style`, `docs`, `chore`, `ci` 접두사 커밋
- **Fixed**: `fix` 접두사 커밋

### 2-2. 버전 결정

시맨틱 버전 규칙:
- `feat` 커밋이 있으면 **minor** bump (0.4.0 → 0.5.0)
- `feat`이 없으면 **patch** bump (0.4.0 → 0.4.1)
- **현재 베타 단계이므로 `-beta` 접미사 유지** (예: `v0.6.0-beta`)

결정된 버전을 사용자에게 보여주고, 변경을 원하면 수정할 수 있도록 한다.

### 2-3. changelog 파일 작성

`changelogs/v{version}.md` 파일을 생성한다 (디렉토리 없으면 자동 생성).

파일 형식:
```markdown
## v{version} - YYYY-MM-DD

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

| Item | Count |
|------|-------|
| Files changed | N |
| Lines added | +N |
| Lines deleted | -N |
| Merged PRs | #N ~ #N (N) |
```

규칙:
- 커밋 메시지를 그대로 나열하지 말고 **의미 단위로 그룹핑**하여 읽기 좋게 작성
- PR 번호가 있으면 포함, 빈 카테고리는 생략
- Statistics 수치는 `git diff --shortstat`에서 가져옴
- 이미 같은 버전 파일이 존재하면 사용자에게 알리고 덮어쓸지 확인

### 2-4. root package.json 버전 동기화

일관된 버전 관리를 위해 root `package.json`의 `version`을 changelog 버전과 일치시킨다.
- **`v` 접두사와 `-beta` 접미사는 그대로 반영** (npm semver는 prerelease를 허용)
- 예: changelog `v0.6.0-beta` → `package.json`의 `"version": "0.6.0-beta"`
- `version` 필드가 없으면 `name` 바로 아래에 추가한다

### 2-5. 사용자 확인 (changelog + 버전)

changelog 초안과 변경될 `package.json` version을 함께 사용자에게 보여주고 확인을 받는다.
수정 요청이 있으면 반영 후 다시 확인.

## 3. develop에 커밋 + push

승인된 changelog 파일과 package.json을 develop에 커밋하고 push한다.

```bash
/usr/bin/git add changelogs/v{version}.md package.json
```

- 커밋 메시지: `chore(release): v{version}` (changelog 추가 + 버전 동기화)
- 임시 파일 방식으로 커밋 (Co-Authored-By 포함)

```bash
/usr/bin/git push origin develop
```

## 4. Release PR 생성 (develop → main)

### 4-1. PR 제목/본문

- **제목**: `Release v{version}`
- **본문**: `changelogs/v{version}.md` 내용을 그대로 포함

### 4-2. 사용자 확인 (PR)

PR 생성 전 아래를 보여주고 확인을 받는다:
- base ← head: `main` ← `develop`
- PR 제목
- PR 본문 미리보기

### 4-3. PR 생성

```bash
gh pr create --base main --head develop --title "Release v{version}" --body-file /tmp/pr-body.md
```

- Write 툴로 `/tmp/pr-body.md` 생성 후 `--body-file` 사용
- 이미 같은 base/head 조합의 열린 PR이 있으면 알리고 중단

## 5. 완료 안내

완료 후 사용자에게 안내한다:

```
Release PR 생성 완료: {PR URL}

PR 머지 시 GitHub Actions(release.yml)가 자동으로:
  - 태그 v{version} 생성
  - GitHub Release 발행 (changelogs/v{version}.md 기반)
```

## 6. 주의사항

- **SoR**: `changelogs/v{version}.md`가 릴리즈 노트의 Source of Record — GitHub Release는 이 파일에서 자동 생성됨
- PR 본문에 비밀 정보가 포함되지 않도록 주의
- changelog push는 develop에만, 태그/릴리즈 발행은 GitHub Actions가 담당 (수동 태그 금지)
- main 직접 커밋/머지 금지 — 머지는 PR을 통해서만
