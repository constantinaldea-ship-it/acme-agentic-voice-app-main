package com.voicebanking.adapter.stt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Stub STT Adapter
 * 
 * <p>Deterministic speech-to-text for local development and testing.
 * Echoes input or returns predefined responses for golden utterances.</p>
 * 
 * <p>Active in 'local' profile only.</p>
 */
@Component
@Profile("local")
public class SttStubAdapter implements SttProvider {
    
    private static final Logger log = LoggerFactory.getLogger(SttStubAdapter.class);
    
    @Value("${voice-banking.stub.stt-latency-ms:50}")
    private long latencyMs;
    
    @Value("${voice-banking.stub.stt-confidence:0.95}")
    private double confidence;
    
    @Override
    public TranscriptResponse transcribe(Object audio) {
        log.debug("STT Stub: transcribing audio (simulated latency: {}ms)", latencyMs);
        
        // Simulate latency
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // For stub, audio is expected to be a String (text input)
        String text = audio instanceof String str ? str : audio.toString();
        
        log.info("STT Stub: transcribed '{}' (confidence: {})", text, confidence);
        
        return new TranscriptResponse(
            text,
            confidence,
            "en-US",
            latencyMs
        );
    }
}
