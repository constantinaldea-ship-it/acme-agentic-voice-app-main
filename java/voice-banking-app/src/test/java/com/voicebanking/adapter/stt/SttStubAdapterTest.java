package com.voicebanking.adapter.stt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

/**
 * SttStubAdapter Unit Tests
 */
@SpringBootTest
@ActiveProfiles("local")
class SttStubAdapterTest {
    
    @Autowired
    private SttProvider sttProvider;
    
    @Test
    void shouldTranscribeText() {
        var response = sttProvider.transcribe("What is my balance?");
        
        assertThat(response.text()).isEqualTo("What is my balance?");
        assertThat(response.confidence()).isGreaterThan(0.9);
        assertThat(response.language()).isEqualTo("en-US");
        assertThat(response.durationMs()).isGreaterThan(0);
    }
    
    @Test
    void shouldHandleEmptyText() {
        var response = sttProvider.transcribe("");
        
        assertThat(response.text()).isEmpty();
    }
}
