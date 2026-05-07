package com.wook.viewer.domain.error

/**
 * 도메인 레이어 에러.
 * presentation 레이어에서 strings.xml 메시지로 매핑된다.
 *
 * cause는 디버깅용 — 사용자에게 노출하지 않는다.
 */
sealed class DocumentError(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    /** 파일을 SAF/파일시스템에서 읽지 못함 (권한 만료, 삭제됨, 네트워크 끊김 등). */
    class IoError(cause: Throwable? = null) : DocumentError("io", cause)

    /** 암호로 보호된 문서. */
    class PasswordProtected(cause: Throwable? = null) : DocumentError("password", cause)

    /** 파일이 손상되었거나 라이브러리가 해석하지 못함. */
    class Corrupted(cause: Throwable? = null) : DocumentError("corrupted", cause)

    /** 포맷은 알겠으나 해당 변형/버전을 지원하지 않음 (예: HWP 3.0). */
    class UnsupportedVariant(val variant: String, cause: Throwable? = null) :
        DocumentError("unsupported_variant", cause)

    /** 위 분류로 못 잡은 일반 오류. */
    class Unknown(cause: Throwable? = null) : DocumentError("unknown", cause)
}
