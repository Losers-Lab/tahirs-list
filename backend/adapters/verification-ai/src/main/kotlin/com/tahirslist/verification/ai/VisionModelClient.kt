package com.tahirslist.verification.ai

import com.tahirslist.domain.verification.CertificationImage
import com.tahirslist.domain.verification.ModelJudgment

/**
 * The vision-transport seam inside the adapter: something that sends [prompt]
 * and [image] to a hosted multimodal model and parses a [ModelJudgment].
 *
 * This is the swap point for the model provider (Gemini 2.5 Flash paid tier is
 * the ratified default; Claude Haiku 4.5 is the alternative — ARCHITECTURE.md
 * U-11). Implemented by [RestVisionModelClient]; mocked in adapter tests so no
 * live provider is ever called.
 *
 * The call is `suspend` (the transport is a Ktor client), so the seam is
 * coroutine-native rather than blocking a thread per request.
 *
 * @throws com.tahirslist.application.verification.VerificationProviderException
 *   on transport failure (non-2xx, timeout, unparseable); never returns a verdict.
 */
fun interface VisionModelClient {
    suspend fun analyze(prompt: String, image: CertificationImage): ModelJudgment
}
