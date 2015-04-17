package com.android.audiomodem;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;

import jmodem.BufferedStream;
import jmodem.Config;
import jmodem.InputSampleStream;

/**
 * Created by roman on 2/10/15.
 */
public class Receiver extends AsyncTask<Void, Void, String> {

    final static int sampleRate = Config.sampleRate;

    // Hoping this will disable AGC and other pre-processing on the incoming audio...
    final static int sourceId = MediaRecorder.AudioSource.VOICE_RECOGNITION;
    final static String TAG = "Receiver";

    private ArrayList<short[]> buffers = new ArrayList<>();
    private final boolean debug = false;

    class InputStreamWrapper implements jmodem.InputSampleStream {

        AudioRecord input;
        short[] buf;
        int offset;
        int size;

        public InputStreamWrapper(AudioRecord src, int bufSize) {
            input = src;
            buf = new short[bufSize];
        }

        @Override
        public double read() throws IOException {
            while (offset >= size) {
                offset = 0;
                size = input.read(buf, 0, buf.length);
                if (debug) {
                    buffers.add(buf.clone());
                }
            }
            double sample = buf[offset] / Config.scalingFactor;
            offset++;
            return sample;
        }
    }

    @Override
    protected String doInBackground(Void... params) {

        int chanFormat = AudioFormat.CHANNEL_IN_MONO;
        int encoding = AudioFormat.ENCODING_PCM_16BIT;
        int bufSize = AudioRecord.getMinBufferSize(sampleRate, chanFormat, encoding);
        bufSize = Math.max(bufSize, 1024);

        Log.d(TAG, "bufSize: " + bufSize);

        AudioRecord src = new AudioRecord(sourceId, sampleRate, chanFormat, encoding, bufSize * 8);

        InputStreamWrapper input = new InputStreamWrapper(src, bufSize);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        DataOutputStream os = null;
        if (debug) {
            String filePath = Environment.getExternalStorageDirectory().getPath() + "/audio.raw";
            Log.d(TAG, "file:" + filePath);

            try {
                os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(filePath)));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                os = null;
            }
        }

        src.startRecording();
        try {
            jmodem.Receiver.run(input, output);
        } catch (IOException e) {
            Log.e(TAG, "receiver failed", e);
        }
        if (os != null) {
            try {
                for (short[] b : buffers) {
                    for (short s : b) {
                        os.writeShort(s);
                    }
                }
                os.flush();
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        src.stop();
        src.release();

        String str = "N/A";
        try {
            str = new String(output.toByteArray(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "result: " + str);
        return str;
    }
}

