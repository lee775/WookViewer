# 욱뷰어 (WookViewer)

한국 사용자를 위한 통합 문서 뷰어 / 편집기 — PDF · DOCX · PPTX · XLSX · HWP · HWPX · 텍스트 · 이미지를 한 앱에서.

> **v1.0.3** (2026-05) — 원스토어 정식 출시. 외부 통신 없음 · 광고 없음 · 권한 0개.

[![Privacy Policy](https://img.shields.io/badge/privacy-policy-blue)](https://lee775.github.io/WookViewer/privacy.html)
[![License: LGPL/Apache-2.0](https://img.shields.io/badge/libs-LGPL%20%2F%20Apache--2.0-green)](#-라이선스)

---

## 지원 포맷

| 분류 | 포맷 | 뷰어 | 편집 |
|------|------|:---:|:---:|
| 오피스 | DOCX · PPTX · XLSX | ✅ FULL | ✅ |
| 오피스 (변환) | ODT · ODS · ODP | ✅ FULL | — |
| 한국어 | HWP · HWPX | ✅ FULL | 🚫 (보기 전용) |
| PDF | PDF | ✅ FULL | 🚫 (보기 전용) |
| 텍스트 | TXT · LOG · CSV · TSV · JSON · XML · YAML · INI · TOML · CONF | ✅ | ✅ |
| 마크다운 | MD · MARKDOWN | ✅ | ✅ |
| 이미지 | JPG · PNG · GIF · WebP · BMP · HEIC · HEIF | ✅ FULL | — |

**비밀번호 보호 문서** — PDF, Office (Agile · Standard), HWPX 모두 해제 가능.

---

## 주요 기능

### 📄 뷰어
- LibreOffice 엔진 — 원본 레이아웃·서식 그대로
- 페이지 단위 렌더링 (긴 문서도 끊김 없음)
- 핀치 줌 / 더블탭 줌 / 페이지 썸네일 그리드
- 키워드 검색 + 매치 하이라이트
- PDF 텍스트 모드 (선택·복사·검색)

### ✏️ Office 편집 (DOCX / PPTX / XLSX)
- 한글 입력 (자모 조합)
- 글꼴 변경 + 미리보기, 크기 (8 ~ 72 pt)
- 글자색 / 형광펜 + 색상 미리보기
- 굵게 · 기울임 · 밑줄
- 정렬 (왼쪽 / 가운데 / 오른쪽 / 양쪽)
- 글머리 기호 · 번호 매기기
- 길게 눌러 단어 선택 + 복사 · 붙여넣기 (Android 클립보드)
- 실행 취소 / 다시 실행
- 다른 포맷으로 저장 (PDF · ODT · ODP · ODS · 원본 사본)

### 📝 텍스트 / Markdown 편집
- TXT · MD 직접 편집 + 저장 + 다른 이름으로 저장
- 한국어 인코딩 자동 감지 (EUC-KR · CP949 포함)

### 📑 편의 기능
- 북마크 (페이지 + 메모)
- 최근 문서 목록
- PDF 목차 (Outline) 자동 표시
- XLSX 시트 탭 / PPTX 슬라이드 페이지
- 파일 / 페이지 텍스트 공유 (Share Intent)

---

## 다운로드

- **원스토어**: 검색 "욱뷰어"
- **GitHub Releases**: [최신 APK 다운로드](https://github.com/lee775/WookViewer/releases)

**지원 환경**: Android 8.0 (API 26) 이상, 64비트 ARM 기기.

---

## 개발

### 요구사항
- JDK 17
- Android SDK 35
- Gradle 8.9+ (Wrapper 동봉)

### 디버그 빌드
```bash
./gradlew :app:assembleDebug
```

### 릴리즈 빌드
릴리즈 빌드는 keystore 환경 변수가 필요합니다. 자세한 내용은 `.github/workflows/build-release.yml` 참고.

GitHub Actions 에서 한 번에 빌드:
```bash
gh workflow run "Build Release APK"
gh run download <run-id>
```

### 아키텍처
Clean Architecture + MVVM (Jetpack Compose).

```
presentation/   Compose UI, ViewModels
domain/         순수 Kotlin: 모델, UseCase, Repository 인터페이스
data/           Room, SAF, Repository 구현
render/         포맷별 DocumentRenderer 구현 (격리)
  ├─ pdf/       PdfBox-Android
  ├─ lok/       LibreOfficeKit (DOCX/PPTX/XLSX + ODT/ODS/ODP)
  ├─ hwp/       hwplib / hwpxlib
  ├─ text/      평문 + Markdown
  └─ image/     Coil
di/             Hilt 모듈
app/            Application, 진입점
```

새 포맷 추가 절차:
1. `render/<format>/` 에 `DocumentRenderer` 구현체
2. `RendererModule.kt` 에 `@Binds @IntoSet` 등록
3. `DocumentFormat` enum 항목 확인/추가

→ 통합 뷰어가 자동으로 인식합니다.

---

## 핵심 라이브러리

| 라이브러리 | 버전 | 라이선스 | 용도 |
|-----------|------|---------|------|
| Kotlin | 2.0 | Apache 2.0 | 언어 |
| Jetpack Compose | BOM 2024.10 | Apache 2.0 | UI |
| Hilt | 2.51 | Apache 2.0 | DI |
| Room | 2.6 | Apache 2.0 | 로컬 DB |
| Coil | 2.7 | Apache 2.0 | 이미지 |
| **LibreOfficeKit** | 24.x | **LGPL v3 / MPL v2** | Office 렌더링/편집 |
| Apache POI | 5.x | Apache 2.0 | Office 암호 해제 |
| PdfBox-Android | 2.0 | Apache 2.0 | PDF |
| hwplib · hwpxlib | latest | Apache 2.0 | HWP |
| Bouncy Castle | 1.78 | MIT | 암호 |
| Timber | 5.0 | Apache 2.0 | 로깅 (debug only) |

전체 목록은 앱 내 **설정 → 오픈소스 라이선스** 화면에서 확인 가능.

---

## 🔐 개인정보 / 보안

- 외부 서버에 어떤 데이터도 전송하지 않습니다.
- `INTERNET` 권한을 비롯해 **어떠한 권한도 요청하지 않습니다**.
- 광고 SDK · 분석 도구 · 광고 식별자 미사용.
- 파일 접근은 Android 표준 SAF 일회성 권한.

자세한 사항: [개인정보 처리방침](https://lee775.github.io/WookViewer/privacy.html)

---

## 📜 라이선스

### 본 앱 코드
**© 2026 WookViewer**. 비상업적 개인 프로젝트로 배포됩니다.

### 사용 라이브러리

#### LibreOffice (LGPL v3 / MPL v2)
욱뷰어는 LibreOfficeKit 을 **동적 라이브러리(`.so`)로 링크**하여 사용합니다. LGPL v3 §4 조항에 따라:

- LibreOffice 자체의 소스 코드는 [공식 저장소](https://github.com/LibreOffice/core)에서 받을 수 있습니다.
- 사용자는 LibreOffice 라이브러리를 호환되는 다른 버전으로 교체할 수 있는 권리를 가집니다.
- 욱뷰어의 LibreOffice 빌드 스크립트는 별도 저장소 [`lee775/libreoffice-android-build`](https://github.com/lee775/libreoffice-android-build) 에서 확인 가능합니다.

#### Apache POI · hwplib · PdfBox 등 (Apache License 2.0)
원본 라이선스 표기를 유지하며 사용합니다.

---

## 변경 이력

### v1.0.3 (2026-05-21)
- HWP·HWPX 보기 전용 안내 배너 추가
- README 정비 (포맷 표 · 라이브러리 표 · LGPL 컴플라이언스 문구)

### v1.0.2 (2026-05-20)
- 외부 저장소(Download 폴더)에 로그/크래시 파일 생성 제거
- 개인정보 처리방침과 동작 일치

### v1.0.1 (2026-05-18)
- 오픈소스 라이선스 고지 화면 (LGPL §4 컴플라이언스)

### v1.0.0 (2026-05-17)
- 정식 릴리즈 빌드 (signed) — 원스토어 등록 가능
- ProGuard 규칙 정비

### v0.9.x
- Office 편집 모드 (DOCX/PPTX/XLSX) — 한글 입력, 글꼴/색상/정렬/서식
- 긴 DOCX 페이지별 분할 렌더링
- 다른 포맷으로 저장 (PDF/ODT/ODP/ODS)

### v0.4.9
- 욱뷰어 브랜드 + 강아지 아이콘
- 이미지 뷰어 + XLSX 추가

### v0.1 ~ v0.4
- PDF, HWP/HWPX, DOCX, PPTX 뷰어 단계적 도입

---

## 문의

- **GitHub Issues**: [github.com/lee775/WookViewer/issues](https://github.com/lee775/WookViewer/issues)
- 원스토어 앱 상세 페이지 → 개발자 문의
