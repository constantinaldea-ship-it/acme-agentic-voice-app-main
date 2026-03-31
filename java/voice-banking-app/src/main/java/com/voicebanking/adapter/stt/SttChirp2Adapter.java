package com.voicebanking.adapter.stt;

import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Chirp2 STT Adapter
 * 
 * <p>Google Cloud Speech-to-Text using Chirp 2 model.
 * Active in 'cloud' profile only.</p>
 * 
 * <p>Requires GOOGLE_APPLICATION_CREDENTIALS environment variable.</p>
 */
@Component
@Profile("cloud")
public class SttChirp2Adapter implements SttProvider {
    
    private static final Logger log = LoggerFactory.getLogger(SttChirp2Adapter.class);
    
    @Value("${voice-banking.stt.language-code:en-US}")
    private String languageCode;
    
    @Value("${voice-banking.stt.model:chirp_2}")
    private String model;
    
    @Value("${voice-banking.stt.enable-automatic-punctuation:true}")
    private boolean enableAutomaticPunctuation;
    
    @Value("${voice-banking.stt.sample-rate-hertz:16000}")
    private int sampleRateHertz;
    
    @Value("${voice-banking.stt.encoding:LINEAR16}")
    private String encoding;
    
    private final SpeechClient speechClient;
    
    public SttChirp2Adapter() throws IOException {
        this.speechClient = SpeechClient.create();
        log.info("SttChirp2Adapter initialized with model: {}", model);
    }
    
    @Override
    public TranscriptResponse transcribe(Object audio) {
        if (!(audio instanceof byte[])) {
            throw new IllegalArgumentException("SttChirp2Adapter requires byte[] audio input");
        }
        byte[] audioBytes = (byte[]) audio;
        
        log.debug("Chirp2 STT: transcribing audio ({} bytes)", audioBytes.length);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Build recognition config
            RecognitionConfig config = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.valueOf(encoding))
                .setSampleRateHertz(sampleRateHertz)
                .setLanguageCode(languageCode)
                .setModel(model)
                .setEnableAutomaticPunctuation(enableAutomaticPunctuation)
                .build();
            
            // Build audio
            RecognitionAudio recognitionAudio = RecognitionAudio.newBuilder()
                .setContent(ByteString.copyFrom(audioBytes))
                .build();
            
            // Perform synchronous recognition
            RecognizeResponse response = speechClient.recognize(config, recognitionAudio);
            
            long durationMs = System.currentTimeMillis() - startTime;
            
            // Extract best transcript
            if (response.getResultsCount() > 0) {
                SpeechRecognitionResult result = response.getResults(0);
                if (result.getAlternativesCount() > 0) {
                    SpeechRecognitionAlternative alternative = result.getAlternatives(0);
                    String transcript = alternative.getTranscript();
                    double confidence = (double) alternative.getConfidence();
                    
                    log.info("Chirp2 STT: transcribed '{}' (confidence: {}, duration: {}ms)", 
                        transcript, confidence, durationMs);
                    
                    return new TranscriptResponse(
                        transcript,
                        confidence,
                        languageCode,
                        durationMs
                    );
                }
            }
            
            log.warn("Chirp2 STT: no transcription results");
            return new TranscriptResponse("", 0.0, languageCode, durationMs);
            
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("Chirp2 STT: transcription failed after {}ms", durationMs, e);
            throw new RuntimeException("STT transcription failed", e);
        }
    }
    
    /**
     * Streaming transcription (for real-time voice)
     * Not used in current PoC but included for future enhancement
     */
    public void transcribeStreaming(BlockingQueue<byte[]> audioQueue, 
                                   ResponseObserver<StreamingRecognizeResponse> responseObserver) {
        
        log.info("Chirp2 STT: starting streaming transcription");
        
        try {
            ClientStream<StreamingRecognizeRequest> clientStream =
                speechClient.streamingRecognizeCallable().splitCall(responseObserver);
            
            // Configure streaming recognition
            RecognitionConfig config = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.valueOf(encoding))
                .setSampleRateHertz(sampleRateHertz)
                .setLanguageCode(languageCode)
                .setModel(model)
                .setEnableAutomaticPunctuation(enableAutomaticPunctuation)
                .build();
            
            StreamingRecognitionConfig streamingConfig =
                StreamingRecognitionConfig.newBuilder()
                    .setConfig(config)
                    .setInterimResults(true)
                    .build();
            
            // Send config
            clientStream.send(
                StreamingRecognizeRequest.newBuilder()
                    .setStreamingConfig(streamingConfig)
                    .build()
            );
            
            // Stream audio chunks
            while (true) {
                byte[] audioChunk = audioQueue.poll(100, TimeUnit.MILLISECONDS);
                if (audioChunk != null) {
                    clientStream.send(
                        StreamingRecognizeRequest.newBuilder()
                            .setAudioContent(ByteString.copyFrom(audioChunk))
                            .build()
                    );
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Chirp2 STT: streaming interrupted");
        } catch (Exception e) {
            log.error("Chirp2 STT: streaming failed", e);
            responseObserver.onError(e);
        }
    }
    
    public void shutdown() {
        if (speechClient != null) {
            speechClient.close();
            log.info("SttChirp2Adapter shut down");
        }
    }
}
