# Vendored LibreOfficeKit Java classes

이 디렉터리의 4개 파일은 [LibreOffice/core](https://github.com/LibreOffice/core)의
`android/Bootstrap/src/org/libreoffice/kit/` 에서 그대로 복사한 것입니다.

| 파일 | 출처 |
|---|---|
| `LibreOfficeKit.java` | LibreOffice core, `libreoffice-26-2` |
| `Office.java` | LibreOffice core, `libreoffice-26-2` |
| `Document.java` | LibreOffice core, `libreoffice-26-2` |
| `DirectBufferAllocator.java` | LibreOffice core, `libreoffice-26-2` |

(현재 `libreoffice-26-2` 와 `master` 의 이 4개 파일은 동일하지만 우리 빌드 산출물과
브랜치를 맞추기 위해 26-2 명시.)

## 라이선스

**Mozilla Public License v2.0** (MPL 2.0).
원본 헤더 보존. 수정 없음.

## 왜 패키지 경로가 `org.libreoffice.kit`?

LibreOffice의 C++ JNI 구현(`sal/android/lo-bootstrap.c`, `desktop/source/lib/`)이
이 정확한 클래스/메서드 시그니처에 바인딩됩니다. 패키지 경로나 메서드 이름을
바꾸면 `.so` 가 함수를 찾지 못해 `UnsatisfiedLinkError` 가 납니다.

## 업데이트

LO 버전 업그레이드 시 마스터에서 다시 복사:

```bash
for f in LibreOfficeKit Office Document DirectBufferAllocator; do
  curl -fsSL "https://raw.githubusercontent.com/LibreOffice/core/master/android/Bootstrap/src/org/libreoffice/kit/${f}.java" \
    -o "app/src/main/java/org/libreoffice/kit/${f}.java"
done
```
