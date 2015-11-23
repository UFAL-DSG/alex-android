package cz.cuni.mff.ufal.androidalex;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.concurrent.PriorityBlockingQueue;

import cz.cuni.mff.ufal.alex.WsioMessages;


public class AlexAudioPlayer extends Thread {
    AudioTrack at;
    int sampleRate;
    WebSocketAudioRecorder recorder;
    PriorityBlockingQueue<WsioMessages.AlexToClient> msgQueue;
    int currPlaybackBufferPos;

    ArrayList<Integer> utteranceCalendarTimes = new ArrayList<Integer>();
    ArrayList<Integer> utteranceCalendarUtterances = new ArrayList<Integer>();

    IDebugTerminal terminal;

    boolean terminate = false;

    private int findOutCurrentlyPlayingUtterance() {
        int currentI = 0;
        for (int i = 1; i < utteranceCalendarTimes.size(); i++) {
            int time = utteranceCalendarTimes.get(i);

            if (time > at.getPlaybackHeadPosition()) {
                break;
            } else {
                currentI = i;
            }
        }

        if (currentI > 0) {
            for (int i = 0; i < currentI; i++) {
                terminal.dbg("removing " + utteranceCalendarTimes.get(0) + " from calendar " + at.getPlaybackHeadPosition() + " " + currPlaybackBufferPos);
                utteranceCalendarUtterances.remove(0);
                utteranceCalendarTimes.remove(0);
            }
        }

        return utteranceCalendarUtterances.get(0);
    }

    public void setDebugTerminal(IDebugTerminal t) {
        terminal = t;
    }

    public AlexAudioPlayer(int sampleRate, PriorityBlockingQueue<WsioMessages.AlexToClient> queue, final WebSocketAudioRecorder recorder) {
        int atBuffSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        at = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                atBuffSize,
                AudioTrack.MODE_STREAM);

        this.recorder = recorder;
        this.sampleRate = sampleRate;

        setupAudioTrackNotifications();

        msgQueue = queue;

        setDaemon(true);
    }

    private void setupAudioTrackNotifications() {
        at.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
            @Override
            public void onMarkerReached(AudioTrack track) {
                recorder.updatePlaybackUtterance(findOutCurrentlyPlayingUtterance());
                terminal.dbg("head marker: " + at.getPlaybackHeadPosition());
            }

            @Override
            public void onPeriodicNotification(AudioTrack track) {
                recorder.updatePlaybackUtterance(findOutCurrentlyPlayingUtterance());
                terminal.dbg("head: " + at.getPlaybackHeadPosition());
            }
        });

        at.setPositionNotificationPeriod((int) (sampleRate * 1.0));
    }

    public void terminate() {
        stopPlayback();
        terminate = true;
    }

    public void stopPlayback() {
        at.stop();
    }

    public int getCurrPlaybackBufferPos() {
        return currPlaybackBufferPos;
    }

    public void markSpeechBegin(int uttId) {
        utteranceCalendarTimes.add(currPlaybackBufferPos);
        utteranceCalendarUtterances.add(uttId);
    }

    public void markSpeechEnd() {
        utteranceCalendarTimes.add(currPlaybackBufferPos);
        utteranceCalendarUtterances.add(-1);

        at.setNotificationMarkerPosition(currPlaybackBufferPos);
    }

    @Override
    public void run() {
        ADPCMDecoder dec = new ADPCMDecoder();
        currPlaybackBufferPos = 0;

        try {
            while(!terminate) {
                WsioMessages.AlexToClient msg = msgQueue.take();

                if (msg.getType() == WsioMessages.AlexToClient.Type.SPEECH) {

                    byte[] encoded = msg.getSpeech().toByteArray();

                    byte[] buffer = encoded;
                    //byte[] buffer = dec.decode(encoded);

                    at.write(buffer, 0, buffer.length);

                    currPlaybackBufferPos += buffer.length / 2;

                    //terminal.dbg("new frame " + currPlaybackBufferPos);

                    at.play();
                } else if(msg.getType() == WsioMessages.AlexToClient.Type.SPEECH_BEGIN) {
                    markSpeechBegin(msg.getUtteranceId());
                    terminal.dbg("speech begin " + msg.getUtteranceId());
                } else if(msg.getType() == WsioMessages.AlexToClient.Type.SPEECH_END) {
                    markSpeechEnd();
                    terminal.dbg("speech end " + msgQueue.size());
                }
            }
        } catch(InterruptedException e) {
            Log.e("setupWebSocketPlayback", "Interrupted.");
        }
    }

    public void flush() {
        at.pause();
        at.flush();
        at.setPlaybackHeadPosition(0);

        currPlaybackBufferPos = 0;
        utteranceCalendarUtterances.clear();
        utteranceCalendarTimes.clear();

        setupAudioTrackNotifications();
    }
}
