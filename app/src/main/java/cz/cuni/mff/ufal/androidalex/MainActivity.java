package cz.cuni.mff.ufal.androidalex;

import android.app.AlertDialog;
import android.graphics.PorterDuff;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.SoundPool;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;
import com.purplefrog.speexjni.FrequencyBand;
import com.purplefrog.speexjni.SpeexDecoder;
import com.purplefrog.speexjni.SpeexEncoder;

import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

import cz.cuni.mff.ufal.alex.WsioMessages;


public class MainActivity extends ActionBarActivity {
    String routerAddr = "http://147.251.253.69:5000/"; //http://10.0.0.8:9001/";
    int sampleRate = 16000;

    WebSocket ws;
    String key;
    WebSocketAudioPiper audioPiper;
    boolean breakRecording;
    ListView chatView;
    Button btnCallAlex;
    Button btnHangUp;

    ArrayList<ChatMessage> chatHistory;
    ChatAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chatView = (ListView)findViewById(R.id.chatView);
        btnCallAlex = (Button)findViewById(R.id.btnCallAlex);
        btnHangUp = (Button)findViewById(R.id.btnHangUp);
        btnCallAlex.getBackground().setColorFilter(0xFF44ef44, PorterDuff.Mode.MULTIPLY);
        btnHangUp.getBackground().setColorFilter(0xFFef4444, PorterDuff.Mode.MULTIPLY);
        btnHangUp.setVisibility(View.INVISIBLE);

        adapter = new ChatAdapter(MainActivity.this, new ArrayList<ChatMessage>());
        chatView.setAdapter(adapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void displayMessage(ChatMessage message) {
        adapter.add(message);
        adapter.notifyDataSetChanged();
        scroll();
    }

    private void scroll() {
        chatView.setSelection(chatView.getCount() - 1);
    }

    void showHangupButton() {
        btnCallAlex.setVisibility(View.INVISIBLE);
        btnHangUp.setVisibility(View.VISIBLE);
    }

    void showCallButton() {
        btnCallAlex.setVisibility(View.VISIBLE);
        btnHangUp.setVisibility(View.INVISIBLE);
    }

    public void hangup(final View view) {
        showCallButton();

        addChat("Zavěšeno.", true);

        audioPiper.stopRecording();
        audioPiper = null;

        ws.close();
        ws = null;
    }

    public void addChat(String strMsg, boolean me) {
        final ChatMessage msg = new ChatMessage();
        msg.setId(1);
        msg.setMe(me);
        msg.setMessage(strMsg);
        msg.setDate(DateFormat.getDateTimeInstance().format(new Date()));

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                displayMessage(msg);
            }
        });
    }

    public void callAlex(final View view) {
        showHangupButton();

        addChat("Vytáčím Alex...", true);

        AsyncHttpClient.getDefaultInstance()
                .websocket(routerAddr, null, new AsyncHttpClient.WebSocketConnectCallback() {
                    @Override
                    public void onCompleted(Exception ex, final WebSocket webSocket) {
                        if (ex != null) {
                            ex.printStackTrace();
                            sayAlexIsUnavailable();
                            return;
                        } else {
                            webSocket.setDataCallback(new DataCallback() {
                                public void onDataAvailable(DataEmitter e, ByteBufferList byteBufferList) {
                                    try {
                                        WsioMessages.WSRouterRoutingResponseProto msg = WsioMessages.WSRouterRoutingResponseProto.parseFrom(byteBufferList.getAllByteArray());
                                        routingResponse(msg);
                                        webSocket.close();
                                    } catch (InvalidProtocolBufferException exc) {
                                        System.out.println(exc);
                                    }
                                }
                            });

                            WsioMessages.WSRouterRequestProto reqMsg = WsioMessages.WSRouterRequestProto.newBuilder()
                                    .setType(WsioMessages.WSRouterRequestProto.Type.ROUTE_REQUEST)
                                    .build();
                            webSocket.send(reqMsg.toByteArray());
                        }
                    }
                });
    }

    void sayAlexIsUnavailable() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.system_unavailable)
                .setTitle(R.string.system_unavailable_title);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog dialog = builder.create();
                dialog.show();
                showCallButton();
            }
        });
    }

    void routingResponse(WsioMessages.WSRouterRoutingResponseProto msg) {
        if (msg.getAddr().equals("")) {
            sayAlexIsUnavailable();
            return;
        } else {
            callAlexAt(msg.getAddr(), msg.getKey());
        }
    }

    void callAlexAt(String addr, final String key) {
        AsyncHttpClient.getDefaultInstance()
                .websocket(addr, null, new AsyncHttpClient.WebSocketConnectCallback() {
                    @Override
                    public void onCompleted(Exception ex, final WebSocket webSocket) {
                        if (ex != null) {
                            Log.e("AndroidAlex", "WebSocket connection exception", ex);
                        } else {
                            audioPiper = new WebSocketAudioPiper(webSocket, key, sampleRate);
                            audioPiper.startRecording();

                            setupWebSocketPlayback(webSocket);
                        }

                    }
                });
    }

    void setupWebSocketPlayback(WebSocket webSocket) {
        int atBuffSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        final AudioTrack at = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                16000,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                atBuffSize,
                AudioTrack.MODE_STREAM);
        at.play();

        final SpeexDecoder dec = new SpeexDecoder(FrequencyBand.WIDE_BAND);

        webSocket.setDataCallback(new DataCallback() {
            public void onDataAvailable(DataEmitter e, ByteBufferList byteBufferList) {
                try {
                    WsioMessages.AlexToClient msg = WsioMessages.AlexToClient.parseFrom(byteBufferList.getAllByteArray());
                    if (msg.getType() == WsioMessages.AlexToClient.Type.SPEECH) {
                        byte[] encoded = msg.getSpeech().toByteArray();
                        short[] decoded = dec.decode(encoded);
                        byte[] buffer = new byte[decoded.length * 2];
                        ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(decoded);

                        at.write(buffer, 0, buffer.length);
                        at.play();
                    } else if (msg.getType() == WsioMessages.AlexToClient.Type.ASR_RESULT) {
                        addChat(msg.getAsrResult(), true);
                    } else if (msg.getType() == WsioMessages.AlexToClient.Type.SYSTEM_PROMPT) {
                        addChat(msg.getSystemPrompt(), false);
                    } else {
                        Log.e("setupWebSocketPlayback", "Unknown msg type.");
                    }
                } catch (InvalidProtocolBufferException exc) {
                    Log.e("setupWebSocketPlayback", "Protobuf exception.", exc);
                }
            }
        });

        webSocket.setClosedCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                at.stop();
            }
        });

        ws = webSocket;
    }
}
