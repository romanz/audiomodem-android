package com.android.audiomodem;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

public class Sender extends AsyncTask<String, Double, Void> {

    static class OutputBuffer implements jmodem.OutputSampleStream {

        final ByteArrayOutputStream out;
        final DataOutputStream stream;

        public OutputBuffer() {
            out = new ByteArrayOutputStream();
            stream = new DataOutputStream(out);
        }

        @Override
        public void write(double value) throws IOException {
            short sample = (short) (Config.scalingFactor * value);
            stream.writeShort(sample);
        }

        public short[] samples() {
            ShortBuffer b = ByteBuffer.wrap(out.toByteArray()).asShortBuffer();
            short[] result = new short[b.capacity()];
            b.get(result);
            return result;
        }
    }

    final static int sampleRate = Config.sampleRate;

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

        try {
            jmodem.Main.send(new ByteArrayInputStream(data), buf);
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
        double duration = ((double) n) / sampleRate;
        Log.d(TAG, String.format("playing %d samples (%f seconds)", n, duration));

        dst.play();
        try {
            final double dt = 0.1;
            for (double t = 0; t < duration; t += dt) {
                publishProgress(t / duration);
                Thread.sleep((long) (dt * 1000));
            }
        } catch (InterruptedException e) {
            // nothing to do
        } finally {
            publishProgress(1.0);
        }
        dst.stop();
        dst.release();
        return null;
    }

}