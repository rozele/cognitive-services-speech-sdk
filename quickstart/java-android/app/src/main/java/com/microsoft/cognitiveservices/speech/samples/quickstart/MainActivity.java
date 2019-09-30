//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
// <code>
package com.microsoft.cognitiveservices.speech.samples.quickstart;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.microsoft.cognitiveservices.speech.SessionEventArgs;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionEventArgs;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.audio.PullAudioOutputStream;
import com.microsoft.cognitiveservices.speech.dialog.ActivityReceivedEventArgs;
import com.microsoft.cognitiveservices.speech.dialog.DialogServiceConfig;
import com.microsoft.cognitiveservices.speech.dialog.DialogServiceConnector;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

import static android.Manifest.permission.*;

public class MainActivity extends AppCompatActivity {

    // Replace below with your own subscription key
    private static String speechSubscriptionKey = "";
    // Replace below with your own service region (e.g., "westus").
    private static String serviceRegion = "";
    // Replace below with your own bot secret
    private static String botSecret = "";

    private boolean connected;
    private DialogServiceConnector connector;
    private long startTime = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Note: we need to request the permissions
        int requestCode = 5; // unique code for the permission request
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{RECORD_AUDIO, INTERNET}, requestCode);
    }

    public void onConnectButtonClicked(View v) {
        Button button = this.findViewById(R.id.connectButton);
        if (connected) {
            try {
                connector.disconnectAsync().get();
                connector.sessionStarted.removeEventListener(this::onSessionStarted);
                connector.sessionStopped.removeEventListener(this::onSessionStopped);
                connector.recognizing.removeEventListener(this::onRecognizing);
                connector.recognized.removeEventListener(this::onRecognized);
                connector.activityReceived.removeEventListener(this::onActivityReceived);
                button.setText("Connect");
                this.connected = false;
                startTime = -1;
            } catch (Exception ex) {
                Log.e("SpeechSDKDemo", "unexpected " + ex.getMessage());
                assert (false);
            }
        }
        else {
            try {
                DialogServiceConfig config = DialogServiceConfig.fromBotSecret(botSecret, speechSubscriptionKey, serviceRegion);
                connector = new DialogServiceConnector(config, AudioConfig.fromDefaultMicrophoneInput());
                connector.connectAsync().get();
                connector.sessionStarted.addEventListener(this::onSessionStarted);
                connector.sessionStopped.addEventListener(this::onSessionStopped);
                connector.recognizing.addEventListener(this::onRecognizing);
                connector.recognized.addEventListener(this::onRecognized);
                connector.activityReceived.addEventListener(this::onActivityReceived);
                button.setText("Disconnect");
                this.connected = true;
            } catch (Exception ex) {
                Log.e("SpeechSDKDemo", "unexpected " + ex.getMessage());
                assert (false);
            }
        }
    }

    public void onSpeechButtonClicked(View v) {
        startTime = System.currentTimeMillis();
        try {
            connector.listenOnceAsync();
        } catch (Exception ex) {
            Log.e("SpeechSDKDemo", "unexpected " + ex.getMessage());
            assert(false);
        }
    }


    private void onSessionStarted(Object obj, SessionEventArgs e) {
        MainActivity.this.runOnUiThread(() -> {
            TextView txt = this.findViewById(R.id.sessionDetected);
            txt.setText("Listening...");
        });
    }

    private void onSessionStopped(Object obj, SessionEventArgs e) {
    }

    private void onRecognizing(Object obj, SpeechRecognitionEventArgs e) {
        MainActivity.this.runOnUiThread(() -> {
            TextView txt = this.findViewById(R.id.speechDetected);
            txt.setText(e.getResult().getText());
        });

    }

    private void onRecognized(Object obj, SpeechRecognitionEventArgs e) {
        MainActivity.this.runOnUiThread(() -> {
            TextView txt = this.findViewById(R.id.speechDetected);
            txt.setText(e.getResult().getText());
        });
    }

    private void onActivityReceived(Object obj, ActivityReceivedEventArgs e) {
        boolean hasAudio = e.hasAudio();
        if (hasAudio) {
            playAudio(e.getAudio(), () -> {
                MainActivity.this.runOnUiThread(() -> {
                    long finishTime = System.currentTimeMillis();
                    if (startTime != -1) {
                        TextView session = this.findViewById(R.id.sessionDetected);
                        session.setText("Duration: " + (finishTime - startTime) + "ms");
                    }
                });
            });
        }

        String activity = e.getActivity();
        MainActivity.this.runOnUiThread(() -> {
            TextView txt = this.findViewById(R.id.activityDetected);
            txt.setText("Has audio: " + hasAudio + ", Activity: " + activity);
        });
    }

    private void playAudio(PullAudioOutputStream stream, Runnable callback) {
        final int SAMPLE_RATE = 16000;

        final int BUFFER_SIZE = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        LinkedList<byte[]> buffer = new LinkedList<>();
        AtomicInteger doneFlag = new AtomicInteger(0);

        AsyncTask.execute(() -> {
            byte[] data = new byte[BUFFER_SIZE];
            while (stream.read(data) > 0) {
                synchronized (buffer) {
                    buffer.add(data);
                }
                data = new byte[BUFFER_SIZE];
            }
            doneFlag.getAndIncrement();
        });

        AsyncTask.execute(() -> {
            AudioTrack audioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    BUFFER_SIZE,
                    AudioTrack.MODE_STREAM);

            if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                audioTrack.play();
                byte[] sound = new byte[BUFFER_SIZE];
                while (buffer.size() > 0 || doneFlag.get() == 0) {
                    if (buffer.size() > 0) {
                        audioTrack.write(buffer.peek(), 0, sound.length);
                        synchronized (buffer) {
                            buffer.pop();
                        }
                    }
                }
                audioTrack.stop();
                audioTrack.release();
            }

            if (callback != null) {
                callback.run();
            }
        });
    }
}
// </code>
