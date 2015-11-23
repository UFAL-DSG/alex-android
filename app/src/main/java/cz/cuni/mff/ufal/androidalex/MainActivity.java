package cz.cuni.mff.ufal.androidalex;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.os.StrictMode;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.protobuf.InvalidProtocolBufferException;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.PriorityBlockingQueue;

import cz.cuni.mff.ufal.alex.WsioMessages;


public class MainActivity extends ActionBarActivity implements IDebugTerminal {
    //String routerAddr = "http://10.10.90.143:9001"; //http://195.113.16.35:9001"; //"http://147.251.253.69:5000/"; //
    //String routerAddr = "http://147.251.253.69:5000/";
    //String routerAddr = "http://10.0.0.8:9001/";
    String routerAddr = "http://195.113.16.55:9001/";
    int sampleRate = 16000;

    WebSocket ws;
    String key;
    WebSocketAudioRecorder audioRecorder;
    AlexAudioPlayer audioPlayer;
    boolean breakRecording;
    ListView chatView;
    Button btnCallAlex;
    Button btnHangUp;
    TextView dbgText;

    ArrayList<ChatMessage> chatHistory;
    ChatAdapter adapter;

    int currPlaybackBufferPos;
    int lastAudioFlushSeq = 0;
    int currUtteranceId;

    private class RouterAddrResolver extends AsyncTask<String, Integer, String> {
        protected String doInBackground(String... urls) {
            try {
                return httpGET(urls[0]);
            } catch(Exception e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(String s) {
            routerAddr = s;
            addChat("Using: " + routerAddr, true);
        }

        public String httpGET(String urlToRead) throws MalformedURLException, IOException {
            StringBuilder result = new StringBuilder();
            URL url = null;
            url = new URL(urlToRead);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            rd.close();

            return result.toString();
        }
    }



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

        dbgText = (TextView)findViewById(R.id.dbgText);
        dbgText.setVisibility(View.GONE);

        try {
            RouterAddrResolver get = new RouterAddrResolver();
            get.execute("https://vystadial.ms.mff.cuni.cz/android-alex-wsrouter.txt");
        } catch (Exception e) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(e.toString())
                    .setTitle("System unavailable!");

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AlertDialog dialog = builder.create();
                    dialog.show();
                }
            });
        }
    }

    public void dbg(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                dbgText.append(text + "\n");
                /*int scrollAmount = dbgText.getLayout().getLineTop(dbgText.getLineCount()) - dbgText.getHeight();
                if(scrollAmount > 0) {
                    dbgText.scrollTo(0, scrollAmount);
                } else {
                    dbgText.scrollTo(0, 0);
                }*/
            }
        });
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

        if (id == R.id.action_debug) {
            if(dbgText.getVisibility() == View.GONE) {
                dbgText.setVisibility(View.VISIBLE);
            }
            else {
                dbgText.setVisibility(View.GONE);
            }
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

        audioRecorder.stopRecording();
        audioRecorder = null;

        audioPlayer.terminate();

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

        dbg("Adding chat message.");
    }

    public void callAlex(final View view) {
        dbg("Calling Alex.");

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
                            audioRecorder = new WebSocketAudioRecorder(webSocket, key, sampleRate);
                            audioRecorder.startRecording();

                            setupWebSocketPlayback(webSocket);
                        }

                    }
                });
    }

    void setupWebSocketPlayback(final WebSocket webSocket) {
        final PriorityBlockingQueue<WsioMessages.AlexToClient> msgQueue = new PriorityBlockingQueue<WsioMessages.AlexToClient>(1000, new Comparator<WsioMessages.AlexToClient>() {
            @Override
            public int compare(WsioMessages.AlexToClient lhs, WsioMessages.AlexToClient rhs) {
                if(lhs.getPriority() < rhs.getPriority()) {
                    return -1;
                } else if(lhs.getPriority() > rhs.getPriority()) {
                    return 1;
                } else {
                    if(lhs.getSeq() < rhs.getSeq()) {
                        return -1;
                    } else if(lhs.getSeq() > rhs.getSeq()) {
                        return 1;
                    } else {
                        return 0;
                    }
                }
            }
        });

        webSocket.setDataCallback(new DataCallback() {
            public void onDataAvailable(DataEmitter e, ByteBufferList byteBufferList) {
                try {
                    WsioMessages.AlexToClient msg = WsioMessages.AlexToClient.parseFrom(byteBufferList.getAllByteArray());

                    if (msg.getType() == WsioMessages.AlexToClient.Type.SPEECH ||
                            msg.getType() == WsioMessages.AlexToClient.Type.SPEECH_BEGIN ||
                            msg.getType() == WsioMessages.AlexToClient.Type.SPEECH_END) {
                        if(lastAudioFlushSeq <= msg.getSeq())
                        {
                            try {
                                msgQueue.add(msg);
                            }
                            catch (IllegalStateException qe) {
                                // Just discard the message when the queue is full.
                            }
                        }
                    } else if (msg.getType() == WsioMessages.AlexToClient.Type.ASR_RESULT) {
                        addChat(msg.getAsrResult(), true);
                    } else if (msg.getType() == WsioMessages.AlexToClient.Type.SYSTEM_PROMPT) {
                        addChat(msg.getSystemPrompt(), false);
                    } else if(msg.getType() == WsioMessages.AlexToClient.Type.FLUSH_OUT_AUDIO) {
                        dbg("Flush Audio.");
                        msgQueue.clear();
                        audioPlayer.flush();
                        msgQueue.clear();
                        lastAudioFlushSeq = msg.getSeq();
                        Log.e("msg", "flushed");
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
                audioPlayer.stopPlayback();
            }
        });

        audioPlayer = new AlexAudioPlayer(sampleRate, msgQueue, audioRecorder);
        audioPlayer.setDebugTerminal(this);
        audioPlayer.start();



        ws = webSocket;
    }


}
