package cz.cuni.mff.ufal.androidalex;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.google.protobuf.ByteString;
import com.koushikdutta.async.http.WebSocket;
import com.purplefrog.speexjni.FrequencyBand;
import com.purplefrog.speexjni.SpeexEncoder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

import cz.cuni.mff.ufal.alex.WsioMessages;

public class WebSocketAudioPiper extends Thread {
    private static final int SPEEX_FRAME_SIZE = 320;

    boolean breakRecording;
    WebSocket ws;
    AudioRecord record;
    String key;
    SpeexEncoder enc;
    int sampleRate;

    public WebSocketAudioPiper(WebSocket webSocket, String key, int sampleRate) {
        ws = webSocket;
        this.key = key;
        enc = new SpeexEncoder(FrequencyBand.WIDE_BAND, 9);
        this.sampleRate = sampleRate;
        setDaemon(true);
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

        byte[] buffer = new byte[SPEEX_FRAME_SIZE * 2];
        breakRecording = false;

        while(!breakRecording) {
            readSpeechFromMic(buffer);
            byte[] encBuffer = encodeSpeech(buffer);
            sendToClient(encBuffer);
        }
    }

    private void readSpeechFromMic(byte[] buffer) {
        try {
            record.read(buffer, 0, buffer.length);
        }catch (Exception e) {
            Log.e("WebSocketAudioPiper", "Error calling record.read.", e);
        }
    }

    private void sendToClient(byte[] encBuffer) {
        WsioMessages.ClientToAlex msg = WsioMessages.ClientToAlex.newBuilder()
                .setSpeech(ByteString.copyFrom(encBuffer))
                .setKey(key)
                .build();
        ws.send(msg.toByteArray());
    }

    private byte[] encodeSpeech(byte[] buffer) {
        ByteBuffer bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
        ShortBuffer sb = bb.asShortBuffer();
        short[] shortBuffer = new short[sb.remaining()];
        int i = 0;
        while(sb.hasRemaining()) {
            shortBuffer[i] = sb.get();
            i++;
        }

        return enc.encode(shortBuffer);
    }

    private void startCapturingMic() {
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 10;  // The 10 is arbitrary here.
        record = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize);
        record.startRecording();
    }
}
