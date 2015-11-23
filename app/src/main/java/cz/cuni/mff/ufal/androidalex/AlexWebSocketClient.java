package cz.cuni.mff.ufal.androidalex;

import com.koushikdutta.async.http.WebSocket;

import java.util.ArrayList;


public class AlexWebSocketClient {
    int currPlaybackBufferPos;
    int lastAudioFlushSeq = 0;
    int currUtteranceId;
    ArrayList<Integer> utteranceCalendarTimes = new ArrayList<Integer>();
    ArrayList<Integer> utteranceCalendarUtterances = new ArrayList<Integer>();

    public AlexWebSocketClient(WebSocket webSocket) {

    }


}
