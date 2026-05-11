# LibreOffice 통합 (다중 세션 진행)

WookViewer 가 Office 파일을 100% Office와 동일하게 렌더링하기 위한 LibreOfficeKit (LOK) 통합. 단일 세션에서 끝낼 수 없는 작업이라 단계적으로 진행한다.

## 단계별 로드맵

| 세션 | 산출물 | 소요 |
|---|---|---|
| **S1** ✅ | 스캐폴딩: `LokDocumentRenderer` 스텁, `LokAvailability` 런타임 체크, `RendererRegistry` 동적 라우터, 설정 토글, 이 문서, LO 빌드 워크플로 | 1시간 |
| **S2** | `.github/workflows/build-libreoffice-android.yml` 수동 트리거. 첫 ABI(arm64-v8a) 빌드 성공할 때까지 반복 — autogen 옵션 조정 필요할 수 있음. 산출물 `liblo-native-code.so` (~100MB) | 4-8시간 CI/회 |
| **S3** | JNI 바인딩 작성, `LokOffice/LokDocument` Kotlin wrapper, 단일 페이지 비트맵 렌더 PoC | 1-2세션 |
| **S4** | 다중 페이지, 텍스트 검색, 비트맵 캐싱, 메모리 관리 | 1-2세션 |
| **S5** | APK 슬림화 (`splits.abi { isEnable = true; include(...) }`), 폰트 번들, 마무리 | 1세션 |

## 현재 동작 (S1 완료 시점)

1. **설정 → "LibreOffice로 Office 파일 렌더링 (베타)"** 토글이 노출됨
2. 토글 켜도 `liblo-native-code.so` 가 APK에 없으므로 `LokAvailability.isAvailable() = false`
3. `RendererRegistryImpl` 가 LOK 라우팅을 건너뛰고 기존 ZIP+XML 렌더러로 폴백
4. 사용자에게 보이는 동작은 v0.7.7 과 동일 — 회귀 없음

## S2 가이드: LibreOffice native lib 빌드

### 자동 (GitHub Actions)

리포지토리 → Actions → "Build LibreOffice for Android" → "Run workflow":
- `abis`: `arm64-v8a` (대다수 최신 기기), 추가하려면 `arm64-v8a,armeabi-v7a`
- `lo_branch`: 안정 브랜치 권장 (예: `libreoffice-7-6`)

빌드 완료 후 artifact 다운로드 → `app/src/main/jniLibs/<abi>/liblo-native-code.so` 위치에 풀어두면 `LokAvailability` 가 true 를 반환한다.

### 수동 (로컬 빌드)

beefy 머신(40GB+ 디스크, 16GB+ RAM 권장) 필요:

```bash
git clone --depth 1 --branch libreoffice-7-6 https://git.libreoffice.org/core lo-core
cd lo-core
./autogen.sh \
  --with-distro=LibreOfficeAndroid \
  --with-android-ndk=$ANDROID_NDK_HOME \
  --with-android-sdk=$ANDROID_HOME \
  --with-android-api-level=28 \
  --host=aarch64-linux-android \
  --disable-debug --enable-release-build
make android-source -j$(nproc)
# 산출물:
find . -name "liblo-native-code.so"
```

## 파일/디렉토리

```
app/src/main/
├── java/com/wook/viewer/
│   ├── data/lok/LokAvailability.kt        # 런타임 lib 가용성 체크
│   └── render/lok/LokDocumentRenderer.kt  # 스텁 — S3에서 JNI 연결
├── jniLibs/                               # ← S2 산출물 여기로 (gitignore)
│   ├── arm64-v8a/liblo-native-code.so
│   ├── armeabi-v7a/liblo-native-code.so
│   └── ...
.github/workflows/
└── build-libreoffice-android.yml          # workflow_dispatch
docs/
└── LIBREOFFICE_INTEGRATION.md             # 이 파일
```

## 라이선스 메모

LibreOfficeKit 은 **MPL 2.0** 라이선스. 정적 링크 시 LO 파생 코드 공개 의무 없음 (앱은 그대로 자체 라이선스). 다만 LO 바이너리 동봉 시 NOTICE/LICENSE 명시 필요.

## APK 사이즈 영향

| | 사이즈 |
|---|---|
| 현재 v0.7.7 | ~38MB |
| + LO native (arm64-v8a only) | ~140MB 예상 |
| + LO native (모든 ABI) | ~400MB |

S5 에서 ABI split 으로 사용자 기기별 ABI만 다운로드되도록 구성 예정.

## 위험 요소

- **LO Android 빌드의 안정성**: 공식 LO 프로젝트가 자체 모바일 앱을 위해서만 유지보수 — 외부 통합 시 빌드 옵션 변경/실패 가능. autogen 실패 시 issue 추적 필요.
- **NDK 버전 의존성**: NDK r25 까지가 가장 안정. r26 이상은 LO 7.x 와 호환성 검증 필요.
- **메모리**: LO 초기화는 100MB+ 힙 사용 — 저사양 기기에서 OOM 가능. 백그라운드 LOK 로딩 + 캐시 정리 필요 (S4).
