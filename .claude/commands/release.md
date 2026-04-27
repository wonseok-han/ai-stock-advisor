다음 단계를 순서대로 실행하여 GitHub Release를 생성하라.

## 0. 환경 주의사항

- **scm_breeze 충돌**: `git` 명령은 `/usr/bin/git` 절대경로 사용
- **gh CLI 필수**: `gh auth status`로 인증 상태 확인. 미인증이면 사용자에게 안내 후 중단

## 1. develop → main PR 생성 및 머지

현재 브랜치가 develop인지 확인한다. 아니면 develop으로 전환한다.

```bash
/usr/bin/git checkout develop
/usr/bin/git pull origin develop
```

main 대비 develop에 새 커밋이 있는지 확인한다:
```bash
/usr/bin/git log origin/main..origin/develop --oneline
```

- 새 커밋이 없으면: "main에 머지할 변경사항이 없습니다"를 알리고 **중단**
- 새 커밋이 있으면: PR 생성

```bash
gh pr create --base main --head develop \
  --title "release: v{version}" \
  --body "develop → main 릴리즈 머지"
```

사용자에게 PR URL을 보여주고, 머지 진행 여부를 확인받는다. 승인 후:

```bash
gh pr merge <PR번호> --merge
```

머지 후 main을 pull 한다:
```bash
/usr/bin/git checkout main
/usr/bin/git pull origin main
```

## 2. changelog.md 확인 (SoR = Single Source of Truth)

`docs/04-report/changelog.md`를 읽는다.

- 파일이 없거나 비어있으면: "changelog.md를 먼저 작성해주세요"를 알리고 **중단**
- 최상단 `## [x.x.x...]` 섹션을 파싱하여 **릴리즈 대상 버전**과 **본문**을 추출한다
- 해당 버전의 태그가 이미 존재하면: "이 버전은 이미 릴리즈되었습니다"를 알리고 **중단**

파싱 규칙:
- `## [0.3.0-beta] - 2026-05-01` 형태에서 버전 = `0.3.0-beta`, 날짜 = `2026-05-01`
- 본문 = 해당 `##` 부터 다음 `## [` 또는 `---` 직전까지의 모든 내용

## 3. 릴리즈 노트 생성

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

## 4. 사용자 확인

릴리즈 전 아래 내용을 보여주고 확인을 받는다:
- 릴리즈 버전: `v{version}`
- 현재 브랜치 (main이 아니면 경고)
- 릴리즈 노트 미리보기 (핵심 내용 요약)

## 5. 태그 생성 및 릴리즈

```bash
/usr/bin/git tag -a "v{version}" -m "v{version}"
/usr/bin/git push origin "v{version}"
gh release create "v{version}" \
  --title "v{version}" \
  --notes-file /tmp/release-notes.md \
  --latest
```

## 6. 완료 확인

- `gh release view v{version}`으로 릴리즈 생성 확인
- 릴리즈 URL을 사용자에게 출력

## 7. 주의사항

- **changelog.md가 SoR**: 릴리즈 노트를 git 커밋에서 생성하지 않는다. 반드시 changelog.md 기반
- main 브랜치가 아닌 곳에서 실행하면 경고하고 사용자 확인 후 진행
- `--prerelease` 플래그는 사용하지 않음 (beta 접미사만으로 충분)
- 이미 존재하는 태그와 충돌하면 사용자에게 알리고 중단
- push는 태그만 한다 (코드 push 아님)
