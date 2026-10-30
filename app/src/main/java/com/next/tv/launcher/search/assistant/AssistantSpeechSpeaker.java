package com.next.tv.launcher.search.assistant;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

/**
 * Singleton Text-To-Speech speaker for NextTV Voice Assistant.
 * Provides concise (< 12 words) audio feedback in Vietnamese with English fallback.
 */
public final class AssistantSpeechSpeaker {
    private static final String TAG = "TvHome.AssistantTTS";
    private static volatile AssistantSpeechSpeaker instance;

    private final Context appContext;
    private TextToSpeech tts;
    private boolean isInitialized = false;

    public static AssistantSpeechSpeaker getInstance(Context context) {
        if (instance == null) {
            synchronized (AssistantSpeechSpeaker.class) {
                if (instance == null) {
                    instance = new AssistantSpeechSpeaker(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private AssistantSpeechSpeaker(Context context) {
        this.appContext = context;
        initTts();
    }

    private void initTts() {
        try {
            tts = new TextToSpeech(appContext, status -> {
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    Locale viLocale = new Locale("vi", "VN");
                    int result = tts.setLanguage(viLocale);
                    if (result != TextToSpeech.LANG_AVAILABLE && result != TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                        int fallbackRes = tts.setLanguage(Locale.US);
                        Log.d(TAG, "Vietnamese TTS not supported, falling back to US English (status=" + fallbackRes + ")");
                    }
                    tts.setPitch(1.0f);
                    tts.setSpeechRate(1.05f);
                    isInitialized = true;
                } else {
                    isInitialized = false;
                    Log.w(TAG, "TTS initialization failed with status: " + status);
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Failed creating TextToSpeech: " + e.getMessage());
            isInitialized = false;
        }
    }

    /**
     * Speaks text using QUEUE_FLUSH. Keeps utterances concise under 12 words.
     */
    public synchronized void speak(String text) {
        if (text == null || text.trim().isEmpty()) return;
        String clean = text.trim();

        // Enforce max 12 words limit for clean TV experience
        String[] words = clean.split("\\s+");
        if (words.length > 12) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                if (i > 0) sb.append(" ");
                sb.append(words[i]);
            }
            clean = sb.toString();
        }

        if (tts != null && isInitialized) {
            try {
                tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "nexttv_assistant_utt");
            } catch (Exception e) {
                Log.w(TAG, "TTS speak error: " + e.getMessage());
            }
        }
    }

    /**
     * Stops any ongoing TTS speech immediately.
     */
    public synchronized void stop() {
        if (tts != null && isInitialized) {
            try {
                tts.stop();
            } catch (Exception ignored) {}
        }
    }

    public synchronized void shutdown() {
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {}
            tts = null;
            isInitialized = false;
        }
    }
}
