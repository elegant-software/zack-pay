package com.elegant.software.blitzpay.voice.service

interface SpeechTranscriptionClient {
    fun transcribe(submission: VoiceAudioSubmission): VoiceTranscription
}
