# WookViewer

PDF · DOCX · PPTX · HWP/HWPX 문서를 모바일에서 보기 위한 Android 뷰어 앱.

## 현재 상태 (v0.4.9 — 욱뷰어 브랜드 + 이미지 + xlsx)

앱 이름: **욱뷰어**, 아이콘: 강아지 🐕

| 포맷 | 상태 |
|---|---|
| PDF | ✅ FULL — 원본 그대로 + 핀치/더블탭 줌 |
| HWP / HWPX | ✅ TEXT_ONLY — 길게 눌러 선택/복사 |
| DOCX | ✅ TEXT_ONLY — 외부 라이브러리 0개 |
| PPTX | ✅ TEXT_ONLY — 슬라이드 1개 = 페이지 1개 |
| **XLSX** | ✅ TEXT_ONLY — 시트 1개 = 페이지 1개, 셀 탭/행 newline |
| Markdown | ✅ TEXT_ONLY — `.md`/`.markdown` 평문 |
| 일반 텍스트 | ✅ TEXT_ONLY — `.txt`/`.csv`/`.json`/`.xml`/`.yaml`/... 13종 |
| **이미지** | ✅ FULL — `.jpg`/`.png`/`.gif`/`.webp`/`.heic`/`.bmp`, EXIF 회전 자동, 줌 |
| DOC / PPT / XLS (구형) | 🚫 명시적 미지원 |

### v0.4.9 변경
- 앱 아이콘 + 이름 "욱뷰어"로 변경
- 이미지 뷰어 추가 (단일 페이지, 줌, EXIF 회전)
- XLSX 추가 (sharedStrings + sheet*.xml 자체 파싱, 외부 라이브러리 0개)
- 파일 연결 강화 — 외부 앱 "공유/열기" 메뉴에 욱뷰어 표시되도록 intent-filter 정비 + Android 11+ `<queries>` 추가

### v0.4 변경사항
- **PPTX 슬라이드별 추출** — `ppt/slides/slideN.xml`을 자연수 순(slide1<slide2<…<slide10<slide11)으로 정렬
- 슬라이드 1개를 페이지 1개로 매핑 (PPTX의 자연스러운 구조)
- 거대 슬라이드(>1500자)는 `TextPaginator` fallback으로 추가 분할
- 슬라이드 마스터/레이아웃/노트는 추출 대상 아님 (필터링)
- `.ppt` 구형 OLE 바이너리 명시적 거부
- v0.2 안내 배너의 "페이지 번호는 원본과 다를 수 있음" 항목이 슬라이드 재배치 케이스도 커버

### v0.3 변경사항
- **DOCX 텍스트 추출** — Apache POI 미사용. `java.util.zip` + `javax.xml SAX`만으로 구현 → APK 사이즈 +0KB
- 표 → 셀 tab + 행 newline으로 평탄화
- `.doc` 파일은 명시적으로 `UnsupportedVariant` 던지기
- HWP에 있던 페이지네이션/Bitmap 렌더 코드를 `render/text/`로 추출 — 모든 TEXT_ONLY 포맷이 공유

### v0.2 변경사항 (Option A)
- HWP/DOCX 뷰어 상단에 **"텍스트 미리보기 모드"** 안내 배너
- "자세히" 다이얼로그 — 표/이미지/서식/페이지 한계 4개 항목 명시
- 분류된 에러 메시지 (암호화/손상/I/O/지원불가/알수없음)
- 포맷별 로딩 메시지

상세는 [HWP PoC 보고서](docs/HWP_POC_REPORT.md) 참조.

## 실기기 검증

설치 + 4종 포맷별 체크리스트: [docs/REAL_DEVICE_TESTING.md](docs/REAL_DEVICE_TESTING.md)

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
