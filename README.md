# WookViewer

PDF · DOCX · PPTX · HWP/HWPX 문서를 모바일에서 보기 위한 Android 뷰어 앱.

## 현재 상태 (v0.3.0 — DOCX 추가)

| 포맷 | 상태 |
|---|---|
| PDF | ✅ FULL — 원본과 동일 (`PdfRenderer`) |
| HWP / HWPX | ✅ TEXT_ONLY — 텍스트 미리보기 모드 |
| DOCX | ✅ TEXT_ONLY — **외부 라이브러리 0개**, 자체 ZIP+XML 파싱 |
| DOC (구형) | 🚫 명시적 미지원 — UI에서 "구형 바이너리 형식" 메시지 |
| PPTX | ⏳ 예정 (v0.4) |

### v0.3 변경사항
- **DOCX 텍스트 추출** — Apache POI 미사용. `java.util.zip` + `javax.xml SAX`만으로 구현 → APK 사이즈 +0KB
- 표 → 셀 tab + 행 newline으로 평탄화
- `.doc` 파일은 명시적으로 `UnsupportedVariant` 던지기 (혼동 방지)
- HWP에 있던 페이지네이션/Bitmap 렌더 코드를 `render/text/`로 추출 — 모든 TEXT_ONLY 포맷이 공유
- v0.2 안내 배너가 DOCX에도 자동 적용

### v0.2 변경사항 (Option A)
- HWP/DOCX 뷰어 상단에 **"텍스트 미리보기 모드"** 안내 배너
- "자세히" 다이얼로그 — 표/이미지/서식/페이지 한계 4개 항목 명시
- 분류된 에러 메시지 (암호화/손상/I/O/지원불가/알수없음)
- 포맷별 로딩 메시지

상세는 [HWP PoC 보고서](docs/HWP_POC_REPORT.md) 참조.

## 빌드

```bash
./gradlew :app:assembleDebug
```

요구사항: JDK 17, Android SDK 35, Gradle 8.9.

## 아키텍처

Clean Architecture + MVVM (Jetpack Compose).

```
presentation/  ← Compose UI, ViewModels
domain/        ← 순수 Kotlin: 모델, UseCase, Repository 인터페이스
data/          ← Room, SAF, Repository 구현
render/        ← 포맷별 DocumentRenderer 구현 (격리)
di/            ← Hilt 모듈
```

핵심은 `DocumentRenderer` 인터페이스 (domain/repository)입니다. 새 포맷을 추가할 때:

1. `render/<format>/` 에 `DocumentRenderer` 구현체 작성
2. `RendererModule.kt` 에 `@Binds @IntoSet` 으로 등록
3. `DocumentFormat` enum 에 항목이 이미 있는지 확인 (없으면 추가)

→ 통합 뷰어 화면은 자동으로 새 포맷을 인식합니다.

## 핵심 라이브러리

- Kotlin 2.0 / Coroutines
- Jetpack Compose (Material 3, BOM 2024.10)
- Hilt (DI), Room (recent docs)
- Storage Access Framework (스코프드 스토리지 정책 준수)

## 로드맵

- **v0.1**: PDF + SAF + 최근 목록 + 다크모드  ← 현재
- **v0.2**: HWP/HWPX (한국 사용자 핵심 기능)
- **v0.3**: DOCX
- **v0.4**: PPTX
- **v0.5**: 검색 / 북마크 / 클라우드 연동

## 알려진 제한 (MVP)

- PDF 암호화 문서 미지원 (PdfRenderer 한계 — PdfiumAndroid 도입 시 해결 예정)
- 텍스트 선택/검색 미구현
- Gradle Wrapper 스크립트(`gradlew`/`gradlew.bat`) 및 jar 미포함 — `gradle wrapper` 명령으로 한 번 생성 필요

## 라이선스

내부 프로젝트.
