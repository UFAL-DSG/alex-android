package cz.cuni.mff.ufal.androidalex;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.MediaSyncEvent;
import android.util.Log;

import com.google.protobuf.ByteString;
import com.koushikdutta.async.http.WebSocket;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Objects;

import cz.cuni.mff.ufal.alex.WsioMessages;

public class WebSocketAudioRecorder extends Thread {
    final int AUDIO_BUFFER_FRAMES = 512;

    boolean breakRecording;
    WebSocket ws;
    AudioRecord record;
    String key;
    ADPCMEncoder enc;
    int sampleRate;
    int currPlaybackPos;
    int currPlaybackUtterance;
    final Object lock = new Object();

    public WebSocketAudioRecorder(WebSocket webSocket, String key, int sampleRate) {
        ws = webSocket;
        this.key = key;
        enc = new ADPCMEncoder();
        this.sampleRate = sampleRate;
        setDaemon(true);
    }

    public void updatePlaybackPos(int pos) {
        currPlaybackPos = pos;
    }

    public void updatePlaybackUtterance(int pos) {
        currPlaybackUtterance = pos;
    }

    public void stopRecording() {
        breakRecording = true;
    }

    public void startRecording() {
        start();
    }

    @Override
    public void run() {
        startCapturingMic();

        byte[] buffer = new byte[AUDIO_BUFFER_FRAMES * 2];
        breakRecording = false;

        while(!breakRecording) {
            readSpeechFromMic(buffer);
            byte[] encBuffer = encodeSpeech(buffer);
            sendToClient(encBuffer);
        }
        record.stop();
    }

    private void readSpeechFromMic(byte[] buffer) {
        try {
            record.read(buffer, 0, buffer.length);
        }catch (Exception e) {
            Log.e("WebSocketAudioRecorder", "Error calling record.read.", e);
        }
    }

    private void sendToClient(byte[] encBuffer) {
        WsioMessages.ClientToAlex msg = WsioMessages.ClientToAlex.newBuilder()
                .setSpeech(ByteString.copyFrom(encBuffer))
                .setKey(key)
                .setCurrentlyPlayingUtterance(currPlaybackUtterance)
                .build();

        ws.send(msg.toByteArray());
    }

    private byte[] encodeSpeech(byte[] buffer) {
        return buffer;
        //enc = new ADPCMEncoder();
        //return enc.encode(buffer);
    }

    private void startCapturingMic() {
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 10;  // The 10 is arbitrary here.
        record = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize);
        record.startRecording();

    }
}
