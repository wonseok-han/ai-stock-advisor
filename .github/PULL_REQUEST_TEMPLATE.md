## Feature / Change

<!-- 이 PR이 다루는 bkit feature 이름 또는 변경 성격 -->
- **bkit feature:** `<feature-name>` (해당 시)
- **Phase:** 0 / 1 / 2 / 3 / 4 / hotfix / chore

## Related Docs

<!-- bkit PDCA 문서 경로 또는 기획 문서 링크 -->
- Plan: `docs/01-plan/features/<feature>.plan.md`
- Design: `docs/02-design/features/<feature>.design.md`
- 기획 근거: `docs/planning/<해당 문서>.md`

## Changes

- [ ] **FE** (`apps/web`)
- [ ] **BE** (`apps/api`)
- [ ] **Docs** (`docs/`)
- [ ] **Infra / CI** (`.github/`, `bkit.config.json`, etc.)

### Summary

<!-- 한두 문장. "무엇을 왜" -->

## PDCA Checklist

- [ ] **Plan** — 계획 문서 작성/리뷰 완료
- [ ] **Design** — 설계 문서 작성 (bkit `requireDesignDoc: true`)
- [ ] **Do** — 구현 완료
- [ ] **Check** — merge 후 `/pdca analyze <feature>` 예정
- [ ] **Archive** — matchRate ≥ 90% 또는 명시적 완료 시 `docs/archive/` 로 이동

## Legal / Disclaimer 영향도

<!-- 본 서비스는 투자 자문이 아닌 참고용 도구. 관련 UI/API/문구가 바뀌면 체크. -->
- [ ] 사용자에게 노출되는 투자 관련 문구가 바뀜 → `docs/planning/07-legal-compliance.md` 재확인함
- [ ] 해당 없음

## Test / Verification

<!-- 어떻게 동작을 확인했는지 -->
- [ ] Unit test
- [ ] Integration test (BE)
- [ ] 수동 확인 (어떻게 확인했는지 적기)
- [ ] `/zero-script-qa` (해당 시)

## Screenshots / Logs (선택)

<!-- FE 변경 스크린샷 또는 BE 로그/응답 예시 -->

## Notes for Reviewer (1인 개발자 셀프 리뷰 시 생략 가능)

<!-- 특별히 봐야 할 부분, trade-off, 추후 과제 -->
