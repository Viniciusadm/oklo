package com.archieapps.oklo.core.net

import kotlinx.serialization.json.JsonElement

sealed interface ApiResult<out T> {
    data class Success<T>(
        val data: T,
        val message: String? = null,
        val misc: JsonElement? = null,
    ) : ApiResult<T>

    sealed interface Failure : ApiResult<Nothing> {
        val userMessage: String

        data object Network : Failure {
            override val userMessage: String get() = "Sem conexão com o servidor. Verifique a internet e tente de novo."
        }

        data object Unauthorized : Failure {
            override val userMessage: String get() = "Sua sessão expirou. Entre de novo para sincronizar."
        }

        data object CodeMissing : Failure {
            override val userMessage: String get() = "Informe o código do dia para continuar."
        }

        data object CodeRejected : Failure {
            override val userMessage: String get() = "Código recusado. Confira o código do dia antes de tentar de novo."
        }

        data object CodeHardStopped : Failure {
            override val userMessage: String
                get() = "Por segurança, o app não envia mais códigos hoje. Confira o código e tente amanhã."
        }

        data object NotSignedIn : Failure {
            override val userMessage: String get() = "Entre na sua conta para usar este recurso."
        }

        data class RateLimited(val retryAfterSeconds: Int?) : Failure {
            override val userMessage: String
                get() = if (retryAfterSeconds != null) {
                    "Muitas gerações seguidas. Tente de novo em ${retryAfterSeconds}s."
                } else {
                    "Muitas gerações seguidas. Aguarde um pouco e tente de novo."
                }
        }

        data class AiNotConfigured(val detail: String?) : Failure {
            override val userMessage: String get() = "A IA não está configurada no servidor."
        }

        data class AiUnreachable(val detail: String?) : Failure {
            override val userMessage: String get() = "O servidor não conseguiu falar com a IA. Tente de novo em instantes."
        }

        data class AiInvalidResponse(val detail: String?) : Failure {
            override val userMessage: String get() = "A IA respondeu em um formato inválido e nenhum card pôde ser aproveitado. Tente de novo."
        }

        data class TextTooLong(val detail: String) : Failure {
            override val userMessage: String get() = detail
        }

        data class Validation(val detail: String) : Failure {
            override val userMessage: String get() = detail
        }

        data class Server(val status: Int, val detail: String?) : Failure {
            override val userMessage: String get() = detail ?: "O servidor não conseguiu concluir a operação."
        }

        data class Malformed(val cause: Throwable) : Failure {
            override val userMessage: String get() = "O servidor respondeu num formato inesperado."
        }
    }
}

fun <T> ApiResult<T>.dataOrNull(): T? = (this as? ApiResult.Success)?.data
