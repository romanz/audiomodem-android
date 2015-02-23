package com.android.audiomodem;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

public class Sender extends Thread {

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
    final static String TAG = "Main";

    final private byte[] data;

    public Sender(byte[] msg) {
        data = msg;
    }

    @Override
    public void run() {
        final int chanFormat = AudioFormat.CHANNEL_OUT_MONO;
        final int encoding = AudioFormat.ENCODING_PCM_16BIT;
        final int mode = AudioTrack.MODE_STATIC;
        final int bufSize = AudioTrack.getMinBufferSize(sampleRate, chanFormat, encoding);

        OutputBuffer buf = new OutputBuffer();

        jmodem.Sender send = new jmodem.Sender(buf);
        try {
            send.writePrefix();
            send.writeTraining();
            send.writeData(data, data.length);
            send.writeEOF();
        } catch (IOException e) {
            Log.e(TAG, "sending data failed", e);
            return;
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
        Log.d(TAG, "playing " + n + " samples");

        dst.play();
        try {
            sleep(n * sampleRate);
        } catch (InterruptedException e) {}
        Log.d(TAG, "Done");
        dst.stop();
        dst.release();
    }

}