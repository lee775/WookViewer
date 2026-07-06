#!/usr/bin/env bash
# 릴리즈 서명 keystore 를 **로컬에서** 생성한다.
#
# 왜 CI 가 아니라 로컬인가:
#   서명 개인키는 절대 CI 로그·아티팩트에 노출되면 안 된다. 이전 generate-keystore.yml
#   워크플로는 개인키 base64 와 평문 암호를 아티팩트(keystore-bundle.txt)로 업로드했고,
#   그 파일이 저장소에 커밋되어 서명키가 유출된 사고가 있었다. 이 스크립트는 키를
#   오프라인에서 만들고, 어디에도 업로드하지 않는다.
#
# 사용법:
#   bash scripts/generate-release-keystore.sh [alias] [validity_years]
#   (기본: alias=wookviewer, validity=30년)
#
# 실행 후:
#   1. 출력된 keystore 파일을 저장소 밖 안전한 곳(비밀번호 관리자, 오프라인 백업)에 보관.
#      분실 시 같은 키로 업데이트 APK 를 만들 수 없다.
#   2. 출력된 base64 와 password 를 GitHub repo Secret 3개로 등록:
#        RELEASE_KEYSTORE_BASE64      (아래 base64 값)
#        RELEASE_KEYSTORE_PASSWORD    (아래 password)
#        RELEASE_KEY_ALIAS            (alias, 기본 wookviewer)
#      → Settings → Secrets and variables → Actions → New repository secret
#   3. 이 스크립트가 만든 out/ 산출물은 커밋하지 말 것 (.gitignore 로 차단됨).
#
set -euo pipefail

ALIAS="${1:-wookviewer}"
VALIDITY_YEARS="${2:-30}"
DNAME="CN=WookViewer, OU=Wook, O=Wook, L=Seoul, ST=Seoul, C=KR"

OUT_DIR="$(cd "$(dirname "$0")/.." && pwd)/keystore"   # .gitignore 에 /keystore/ 포함됨
mkdir -p "$OUT_DIR"
JKS="$OUT_DIR/wookviewer-release.jks"

if [[ -e "$JKS" ]]; then
  echo "이미 존재: $JKS — 덮어쓰지 않는다. 새로 만들려면 먼저 안전하게 백업 후 삭제할 것." >&2
  exit 1
fi

# 강력한 랜덤 암호 (32자, 영숫자)
PW="$(LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 32)"

keytool -genkeypair -v \
  -keystore "$JKS" \
  -storetype PKCS12 \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 \
  -validity "$(( VALIDITY_YEARS * 365 ))" \
  -storepass "$PW" -keypass "$PW" \
  -dname "$DNAME"

B64="$(base64 -w0 "$JKS")"

echo
echo "==================================================================="
echo " keystore 생성 완료: $JKS"
echo " 이 값들은 화면에만 표시된다. 안전하게 옮긴 뒤 터미널 기록을 지울 것."
echo "==================================================================="
echo
echo "RELEASE_KEY_ALIAS       = $ALIAS"
echo "RELEASE_KEYSTORE_PASSWORD = $PW"
echo
echo "RELEASE_KEYSTORE_BASE64 ="
echo "$B64"
echo
echo "-------------------------------------------------------------------"
echo " 다음: 위 3개를 GitHub Secret 으로 등록하고, jks 파일을 오프라인 백업."
echo " keystore/ 디렉터리는 .gitignore 로 커밋이 차단되어 있다."
echo "-------------------------------------------------------------------"
