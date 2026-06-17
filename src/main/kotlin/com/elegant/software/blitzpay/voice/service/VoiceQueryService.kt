package com.elegant.software.blitzpay.voice.service

import com.elegant.software.blitzpay.voice.api.VoiceGateway
import com.elegant.software.blitzpay.voice.config.VoiceProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class VoiceQueryService(
    private val transcriptionClient: SpeechTranscriptionClient,
    private val properties: VoiceProperties,
) : VoiceGateway {
    private val log = LoggerFactory.getLogger(VoiceQueryService::class.java)

    override fun process(submission: VoiceAudioSubmission): VoiceTranscriptionResponse {
        val transcription = transcriptionClient.transcribe(submission)

        transcription.durationSeconds?.let { duration ->
            if (duration.toDouble() < 1.0) {
                throw AudioTooShortException()
            }
            if (duration.toLong() > properties.maxDurationSeconds) {
                throw AudioTooLongException(properties.maxDurationSeconds)
            }
        }

        log.info(
            "voice transcription completed subject={} transcriptLength={}",
            submission.callerSubject,
            transcription.text.length,
        )

        return VoiceTranscriptionResponse(
            transcript = transcription.text,
            language = transcription.language,
        )
    }
}
