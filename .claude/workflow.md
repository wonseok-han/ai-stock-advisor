## Development Workflow

### Commands

루트의 `Makefile`을 통해 BE/FE 명령을 통합 실행합니다. (pnpm workspace / turbo / nx 도입 안 함 정책에 따라 단순 cd 래퍼.)

```bash
make help            # 사용 가능한 모든 타깃
make install         # FE pnpm install + BE 툴체인 확인

# Dev
make dev             # FE + BE 동시 기동 (Ctrl+C로 둘 다 종료)
make web-dev         # FE만
make api-dev         # BE만 (./gradlew bootRun)

# Build
make build           # FE + BE
make web-build       # pnpm build
make api-build       # ./gradlew build -x test

# Check (CI 동등)
make check           # FE typecheck/lint + BE check
make web-check       # tsc --noEmit + pnpm lint
make api-check       # ./gradlew check (테스트 + 정적 분석)

# Test / Lint / Clean
make test | make lint | make clean
```

> 개별 명령을 직접 실행하려면 `cd apps/web && pnpm <cmd>` 또는 `cd apps/api && ./gradlew <task>` 도 가능. CI(`.github/workflows/ci.yml`)는 Make 의존을 피해 개별 명령을 직접 호출합니다.

---

## PDCA Auto Behavior (bkit)

### 신규 기능 요청 시

```
사용자: "종목 검색 API 만들어줘"
Claude:
  1. docs/02-design/features/stock-search.design.md 확인
  2. 없으면 bkit-templates 로 design 문서 생성
  3. 설계 기반 구현 (apps/api + apps/web)
  4. 완료 후 gap 분석(/pdca analyze stock-search) 제안
```

### 버그 수정 / 리팩터링 시

```
Claude:
  1. 코드 ↔ design 문서 비교
  2. 원인 파악 후 수정
  3. 설계 문서 업데이트 필요 여부 점검
```

### 구현 완료 후 검증 (필수)

```
Claude:
  1. make web-check (FE typecheck + lint)
  2. make api-check (BE check — 테스트 + 정적 분석)
  3. 가능하면 dev 서버 기동(make dev)하여 브라우저에서 기능 확인
  4. 검증 통과 후에만 커밋/PR 제안
```

> **중요:** 구현 후 `make check` 없이 바로 커밋하지 말 것. CI 등가 검증을 로컬에서 먼저 수행.

---

## Project Structure

```
nowini/
├── apps/
│   ├── web/               # Next.js 16 (FE)
│   └── api/               # Spring Boot 3 (BE)
├── changelogs/            # 버전별 릴리즈 changelog (vX.Y.Z.md)
├── docs/
│   ├── planning/          # 초기 기획 고정본 (01 ~ 07)
│   ├── 01-plan/           # bkit PDCA: 기능별 Plan 문서
│   ├── 02-design/         # bkit PDCA: 기능별 Design 문서
│   ├── 03-analysis/       # bkit PDCA: Gap Analysis
│   ├── 04-report/         # bkit PDCA: 완료 리포트
│   └── archive/           # bkit PDCA: 완료/아카이브
├── .bkit/                 # bkit 런타임 상태 (state / runtime / snapshots)
├── bkit.config.json       # bkit 프로젝트 설정 (Level: Dynamic)
├── CLAUDE.md              # (메인 설정)
└── README.md
```

> **구조 확정:**
> - **Monorepo** — 단일 repo, `apps/web` + `apps/api` 네이티브 빌드 (pnpm workspace / turbo / nx **도입 안 함**)
> - **형상관리:** GitHub **Public** repo + **Trunk-based** (main 보호) + feature 브랜치 (`feat/<bkit-feature>`)
> - **개발 형태:** 1인 개발
> - **Vercel** Root Directory = `apps/web`, **Render** = `apps/api/Dockerfile`

### Git 브랜치 워크플로 (필수 준수)

```
main (배포) ← develop (통합) ← feat/xxx (작업)
```

1. **작업 브랜치 생성**: 항상 `develop` 기준으로 생성 (`git checkout -b feat/xxx develop`)
2. **PR 생성**: 항상 `--base develop` 으로 생성. **절대 main 대상 PR을 임의로 만들지 않는다.**
3. **develop 머지**: squash merge
4. **main 머지**: 사용자가 "main에 머지해", "배포하자" 등 **명시적으로 요청할 때만** develop → main PR 생성

> **금지사항:**
> - main 직접 커밋 금지
> - 사용자 요청 없이 main 대상 PR 생성 금지
> - 사용자 요청 없이 main에 머지/push 금지

---

## 문서 구조 규칙

### docs/planning/ (초기 기획 고정본)
- 서비스 포지셔닝, 아키텍처, 데이터 소스, AI 전략, 로드맵, 법적 고지
- **수정은 신중하게** — 주요 결정 변경 시에만 업데이트
- 기능별 상세 설계는 이쪽이 아님 → `docs/02-design/`

### docs/01-plan/ ~ 04-report/ (bkit PDCA)
- 기능 단위 Plan → Design → Do → Analyze → Report 사이클
- 완료되면 `docs/archive/{date}/{feature}` 로 이동

### docs/archive/ (히스토리)
- 완료된 기능 PDCA 문서들
- 읽기 전용 (수정 금지)

### 아카이브 트리거
- Gap analysis 매칭률 ≥ 90% 달성 OR 사용자 명시적 완료 선언
- → `docs/archive/` 로 이동

---

## Release Workflow

```
/changelog → changelogs/vX.Y.Z.md 생성 + develop 커밋/push
/pr        → develop → main Release PR 생성
PR 머지    → GitHub Actions 자동 태그 + 릴리즈 발행
```

1. **`/changelog`**: develop 브랜치에서 실행. main 대비 변경사항 분석 → `changelogs/vX.Y.Z.md` 생성 → develop에 커밋+push
2. **`/pr`**: base 브랜치 자동 감지 (feat→develop, develop→main). Release PR 생성 시 changelog 내용을 본문에 포함
3. **GitHub Actions** (`.github/workflows/release.yml`): main에 changelogs 파일 머지 시 자동으로 태그 생성 + GitHub Release 발행

> **SoR**: `changelogs/vX.Y.Z.md`가 릴리즈 노트의 Single Source of Record. GitHub Release는 이 파일에서 자동 생성됨.

---

## Key Commands

### 개발 커맨드 (Claude 슬래시)

| 명령 | 설명 |
|---|---|
| `/commit` | staged 변경사항 커밋 (승인 없이 즉시 실행) |
| `/pr` | GitHub PR 생성 (base 브랜치 자동 감지) |
| `/changelog` | 릴리즈용 changelog 생성 (`changelogs/vX.Y.Z.md`) |

### bkit PDCA 커맨드

| 명령 | 설명 |
|---|---|
| `/pdca status` | 현재 PDCA 상태 |
| `/pdca pm {feature}` | PM Agent Team으로 사전 제품 분석 |
| `/pdca plan {feature}` | 기능 플랜 작성 |
| `/pdca design {feature}` | 기능 설계 문서 작성 |
| `/pdca do {feature}` | 구현 가이드 |
| `/pdca analyze {feature}` | 설계 vs 구현 Gap 분석 |
| `/pdca report {feature}` | 완료 리포트 생성 |
| `/development-pipeline status` | 9단계 파이프라인 현재 위치 |
| `/code-review <path>` | 코드 리뷰 |
| `/zero-script-qa` | 로그 기반 QA |
