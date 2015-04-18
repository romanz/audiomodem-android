package com.android.audiomodem;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

public class Sender extends AsyncTask<String, Void, Void> {

    static class OutputBuffer implements jmodem.OutputSampleStream {

        final ByteArrayOutputStream out;
        final DataOutputStream stream;

        public OutputBuffer() {
            out = new ByteArrayOutputStream();
            stream = new DataOutputStream(out);
        }

        @Override
        public void write(double value) throws IOException {
            short sample = (short) (jmodem.Config.scalingFactor * value);
            stream.writeShort(sample);
        }

        public short[] samples() {
            ShortBuffer b = ByteBuffer.wrap(out.toByteArray()).asShortBuffer();
            short[] result = new short[b.capacity()];
            b.get(result);
            return result;
        }
    }

    final static int sampleRate = jmodem.Config.sampleRate;
    final static int duration = 10;  // in seconds

    final static int streamType = AudioManager.STREAM_MUSIC;
    final static String TAG = "Sender";

    @Override
    protected Void doInBackground(String... params) {

        final int chanFormat = AudioFormat.CHANNEL_OUT_MONO;
        final int encoding = AudioFormat.ENCODING_PCM_16BIT;
        final int mode = AudioTrack.MODE_STATIC;
        final int bufSize = AudioTrack.getMinBufferSize(sampleRate, chanFormat, encoding);

        OutputBuffer buf = new OutputBuffer();
        final byte[] data = params[0].getBytes();

        jmodem.Sender send = new jmodem.Sender(buf);
        try {
            send.writePrefix();
            send.writeTraining();
            send.writeData(data, data.length);
            send.writeEOF();
        } catch (IOException e) {
            Log.e(TAG, "sending data failed", e);
            return null;
        }

        short[] samples = buf.samples();

        AudioTrack dst = new AudioTrack(
                streamType,
                sampleRate,
                chanFormat,
                encoding,
                Math.max(samples.length, bufSize) * 2,
                mode
        );
        int n = dst.write(samples, 0, samples.length);
        long duration = n * 1000L / sampleRate; // [ms]
        Log.d(TAG, String.format("playing {0} samples ({1} seconds)", n, duration / 1e3));

        dst.play();
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            // nothing to do
        }
        Log.d(TAG, "Done");
        dst.stop();
        dst.release();
        return null;
    }

}