package com.android.audiomodem;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.widget.ProgressBar;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import jmodem.Config;

/**
 * Created by roman on 2/10/15.
 */
public class Receiver extends AsyncTask<Void, Double, Result> {

    final static int sampleRate = Config.sampleRate;

    // Hoping this will disable AGC and other pre-processing on the incoming audio...
    final static int sourceId = MediaRecorder.AudioSource.VOICE_RECOGNITION;
    final static String TAG = "Receiver";

    private ArrayList<short[]> buffers = new ArrayList<>();
    private final boolean debug = false;
    private double peak = 0;
    
    private volatile boolean stopFlag = false;

    class InputStreamWrapper implements jmodem.InputSampleStream {

        AudioRecord input;
        short[] buf;
        int offset;  // next short to be read from buf
        int size;  // number of shorts in buf

        public InputStreamWrapper(AudioRecord src, int bufSize) {
            input = src;
            buf = new short[bufSize];
        }

        @Override
        public double read() throws IOException {
            if (stopFlag) {
                throw new EOFException();
            }
            while (offset >= size) {
                offset = 0;
                size = input.read(buf, 0, buf.length);
                updatePeak(buf, size);
                if (debug) {
                    buffers.add(buf.clone());
                }
            }
            double sample = buf[offset] / Config.scalingFactor;
            offset++;
            return sample;
        }
    }

    public void stop() {
        stopFlag = true;
    }

    private void updatePeak(short[] buf, int size) {
        if (size <= 0) {
            return;
        }
        int result = 0;
        for (int i = 0; i < size; i++) {
            result = Math.max(result, Math.abs(buf[i]));
        }
        publishProgress( result / Config.scalingFactor );
    }

    @Override
    protected Result doInBackground(Void... params) {

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
            Log.d(TAG, "file: " + filePath);

            try {
                os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(filePath)));
            } catch (FileNotFoundException e) {
                Log.e(TAG, "file open failed", e);
                os = null;
            }
        }

        src.startRecording();
        try {
            jmodem.Receiver.run(input, output);
        } catch (IOException e) {
            Log.e(TAG, "receiver failed", e);
            return new Result(null, e.getMessage());
        } finally {
            src.stop();
            src.release();
            publishProgress(0.0);
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
                Log.e(TAG, "audio save failed", e);
                return new Result(null, e.getMessage());
            }
        }

        try {
            String str = new String(output.toByteArray(), "UTF-8");
            return new Result(str, null);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "unicode decoding failed", e);
            return new Result(null, e.getMessage());
        }
    }
}

