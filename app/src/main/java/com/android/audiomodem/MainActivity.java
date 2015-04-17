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


public class MainActivity extends ActionBarActivity {

    private EditText editText = null;
    private Button buttonRecv = null;
    private Color origColor = null;

    private Thread tx = null;
    private Receiver rx = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editText = (EditText) findViewById(R.id.editText);
        buttonRecv = (Button) findViewById(R.id.buttonRecv);

        // Get intent, action and MIME type
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null && "text/plain".equals(type)) {
            String str = intent.getStringExtra(Intent.EXTRA_TEXT);
            editText.setText(str);
        }
    }

    public void onReceive(View v) {
        rx = new Receiver() {
            @Override
            protected void onPostExecute(String result) {
                editText.setText(result);
                buttonRecv.setEnabled(true);
            }
        };
        buttonRecv.setEnabled(false);
        rx.execute();
    }

    public void onSend(View v) {
        byte[] msg = editText.getText().toString().getBytes();
        tx = new Sender(msg);
        tx.start();
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

        //noinspection SimplifiableIfStatement
        if (id == R.id.copy_to_clip) {
            String str = editText.getText().toString();
            ClipData clip = ClipData.newPlainText("text", str);
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(clip);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
