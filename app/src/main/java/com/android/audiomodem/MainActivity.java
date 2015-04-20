package com.android.audiomodem;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;


public class MainActivity extends ActionBarActivity {

    private EditText editText = null;
    private ImageButton recvBtn = null;
    private ImageButton sendBtn = null;
    private Color origColor = null;

    private Sender tx = null;
    private Receiver rx = null;
    private ProgressBar pBar;
    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editText = (EditText) findViewById(R.id.editText);
        recvBtn = (ImageButton) findViewById(R.id.recvBtn);
        sendBtn = (ImageButton) findViewById(R.id.sendBtn);
        pBar = (ProgressBar) findViewById(R.id.pBar);

        // Get intent, action and MIME type
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null && "text/plain".equals(type)) {
            String str = intent.getStringExtra(Intent.EXTRA_TEXT);
            editText.setText(str);
            editText.setSelection(str.length());
        }

        context = getApplicationContext();
    }

    void toast(String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    public void onReceive(View v) {
        rx = new Receiver() {
            @Override
            protected void onPostExecute(Result res) {
                if (res.err == null) {
                    editText.setText(res.out);
                    int len = res.out.length();
                    editText.setSelection(len);
                    toast("OK");
                } else {
                    toast("Error: " + res.err);
                }
                recvBtn.setEnabled(true);
            }

            @Override
            protected void onProgressUpdate(Double... values) {
                double p = values[0];
                pBar.setProgress((int)(p * pBar.getMax()));
            }
        };
        recvBtn.setEnabled(false);
        rx.execute();
        toast("Receiving...");
    }

    public void onSend(View v) {
        String msg = editText.getText().toString();
        tx = new Sender() {
            @Override
            protected void onPostExecute(Void result) {
                sendBtn.setEnabled(true);
            }

            @Override
            protected void onProgressUpdate(Double... values) {
                double p = values[0];
                pBar.setProgress((int)(p * pBar.getMax()));
            }
        };
        sendBtn.setEnabled(false);
        tx.execute(msg);
        toast("Sending...");
    }

    public void onClear(View v) {
        editText.setText("");
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

        if (id == R.id.menu_copy) {
            String str = editText.getText().toString();
            ClipData clip = ClipData.newPlainText("text", str);
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(clip);
            return true;
        }

        if (id == R.id.menu_clear) {
            editText.setText("");
            return true;
        }

        if (id == R.id.menu_stop) {
            if (rx != null) {
                rx.stop();
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
