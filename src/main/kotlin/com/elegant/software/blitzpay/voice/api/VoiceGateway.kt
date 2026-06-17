package com.elegant.software.blitzpay.voice.api

import com.elegant.software.blitzpay.voice.service.VoiceAudioSubmission
import com.elegant.software.blitzpay.voice.service.VoiceTranscriptionResponse

interface VoiceGateway {
    fun process(submission: VoiceAudioSubmission): VoiceTranscriptionResponse
}
