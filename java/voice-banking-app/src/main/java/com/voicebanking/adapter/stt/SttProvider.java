package com.voicebanking.adapter.stt;

/**
 * Speech-to-Text Provider Interface
 * 
 * <p>Contract for STT providers. Implementations:</p>
 * <ul>
 *   <li>{@link SttStubAdapter} - Deterministic stub for local/test</li>
 *   <li>SttChirp2Adapter - Google Cloud STT (Phase 2)</li>
 * </ul>
 */
public interface SttProvider {
    
    /**
     * Transcribe audio to text
     * 
     * @param audio Audio data (byte[] for real audio, String for stub/test)
     * @return Transcript response with text and metadata
     */
    TranscriptResponse transcribe(Object audio);
}
