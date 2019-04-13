package it.m2app.screamreceiver;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class MainActivity extends AppCompatActivity {
    private static final int HEADER_SIZE = 5;
    private static final int MAX_SO_PACKETSIZE = 1152 + HEADER_SIZE;

    private boolean running = true;
    private AudioTrack track = null;

    private int curSampleRate = 0;
    private int curSampleSize = 0;
    private int curChannels = 0;

    private String infoMsg;

    private Runnable updateUI;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final TextView info = findViewById(R.id.info);

        final Handler h = new Handler(Looper.getMainLooper());
        updateUI = new Runnable() {
            @Override
            public void run() {
                info.setText(infoMsg);

                h.postDelayed(updateUI, 1000);
            }
        };
        h.postDelayed(updateUI, 1000);

        Thread producer = new Thread() {
            @Override
            public void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

                int curChannelMapLSB = 0;
                int curChannelMapMSB = 0;
                int curChannelMap;

                AudioFormat format;

                WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiManager.MulticastLock multicastLock = wifi.createMulticastLock("screamReceiver:MulticastLock");
                multicastLock.setReferenceCounted(true);
                multicastLock.acquire();

                MulticastSocket socket = null;
                InetAddress group = null;
                try {
                    infoMsg = "Awaiting for multicast packets.";

                    socket = new MulticastSocket(4010);
                    group = InetAddress.getByName("239.255.77.77");
                    socket.joinGroup(group);

                    DatagramPacket packet;

                    byte[] buf = new byte[MAX_SO_PACKETSIZE];
                    packet = new DatagramPacket(buf, buf.length);

                    while (running) {
                        socket.receive(packet);
                        byte[] data = packet.getData();

                        if (data.length > HEADER_SIZE) {
                            int d0 = data[0] & 0xFF;
                            int d1 = data[1] & 0xFF;
                            int d2 = data[2] & 0xFF;
                            int d3 = data[3] & 0xFF;
                            int d4 = data[4] & 0xFF;
                            if (curSampleRate != d0 || curSampleSize != d1 || curChannels != d2 || curChannelMapLSB != d3 || curChannelMapMSB != d4) {
                                curSampleRate = d0;
                                curSampleSize = d1;
                                curChannels = d2;
                                curChannelMapLSB = d3;
                                curChannelMapMSB = d4;
                                curChannelMap = (curChannelMapMSB << 8) | curChannelMapLSB;

                                int sampleRate = ((curSampleRate >= 128) ? 44100 : 48000) * (curSampleRate % 128);

                                if (curSampleSize != 16) {// Android only support PCM 8, 16 or float. 24 and 32 bit integer are unsupported.
                                    infoMsg = "Android doesn't support more than 16bit per sample. Please set Scream to 16bit, not "+curSampleSize;
                                    System.err.println(infoMsg);
                                    if (track != null) {
                                        track.stop();
                                        track.release();
                                    }
                                    track = null;
                                    continue;
                                }

                                AudioFormat.Builder formatBuilder = new AudioFormat.Builder();

                                if (curChannels == 1) {
                                    format = formatBuilder
                                            .setSampleRate(sampleRate)
                                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                            .build();
                                } else if (curChannels == 2) {
                                    format = formatBuilder
                                            .setSampleRate(sampleRate)
                                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                            .build();
                                } else {
                                    int channelMask = curChannelMap << 2;//windows and android constants for channels are in the same order, but android values are shifted by 2 positions
                                    /*
                                    int channelMask = 0;
                                    // k is the key to map a windows SPEAKER_* position to a PA_CHANNEL_POSITION_*
                                    // it goes from 0 (SPEAKER_FRONT_LEFT) up to 10 (SPEAKER_SIDE_RIGHT) following the order in ksmedia.h
                                    // the SPEAKER_TOP_* values are not used
                                    int k = -1;
                                    for (int i = 0; i < curChannels; i++) {
                                        for (int j = k + 1; j <= 10; j++) {// check the channel map bit by bit from lsb to msb, starting from were we left on the previous step
                                            if(((curChannelMap >> j) & 0x01) !=0){// if the bit in j position is set then we have the key for this channel
                                                k = j;
                                                break;
                                            }
                                        }
                                        // map the key value to a pulseaudio channel position
                                        switch (k) {
                                            case 0:
                                                channelMask |= AudioFormat.CHANNEL_OUT_FRONT_LEFT;
                                                break;
                                            case 1:
                                                channelMask |= AudioFormat.CHANNEL_OUT_FRONT_RIGHT;
                                                break;
                                            case 2:
                                                channelMask |= AudioFormat.CHANNEL_OUT_FRONT_CENTER;
                                                break;
                                            case 3:
                                                channelMask |= AudioFormat.CHANNEL_OUT_LOW_FREQUENCY;
                                                break;
                                            case 4:
                                                channelMask |= AudioFormat.CHANNEL_OUT_BACK_LEFT;
                                                break;
                                            case 5:
                                                channelMask |= AudioFormat.CHANNEL_OUT_BACK_RIGHT;
                                                break;
                                            case 6:
                                                channelMask |= AudioFormat.CHANNEL_OUT_FRONT_LEFT_OF_CENTER;
                                                break;
                                            case 7:
                                                channelMask |= AudioFormat.CHANNEL_OUT_FRONT_RIGHT_OF_CENTER;
                                                break;
                                            case 8:
                                                channelMask |= AudioFormat.CHANNEL_OUT_BACK_CENTER;
                                                break;
                                            case 9:
                                                channelMask |= AudioFormat.CHANNEL_OUT_SIDE_LEFT;
                                                break;
                                            case 10:
                                                channelMask |= AudioFormat.CHANNEL_OUT_SIDE_RIGHT;
                                                break;
                                            default:
                                                // center is a safe default, at least it's balanced. This shouldn't happen, but it's better to have a fallback
                                                channelMask |= AudioFormat.CHANNEL_OUT_FRONT_CENTER;
                                        }
                                    }
                                    */
                                    format = formatBuilder
                                            .setSampleRate(sampleRate)
                                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                            .setChannelMask(channelMask)
                                            .build();
                                }
                                int bufferSize = AudioTrack.getMinBufferSize(format.getSampleRate(), format.getChannelMask(), format.getEncoding());
                                track = new AudioTrack(AudioManager.STREAM_MUSIC, format.getSampleRate(), format.getChannelMask(), format.getEncoding(), bufferSize, AudioTrack.MODE_STREAM);
                                track.play();
                                infoMsg = "Switched format to sample rate " + sampleRate + ", sample size " + curSampleSize + " and " + curChannels + " channels.";
                                System.err.println(infoMsg);
                            }
                        }

                        if (track != null) {
                            track.write(data, 5, data.length - 5, AudioTrack.WRITE_NON_BLOCKING);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (track != null) {
                    track.stop();
                    track.release();
                }
                if (socket != null) {
                    if (group != null) {
                        try {
                            socket.leaveGroup(group);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    socket.close();
                }

                multicastLock.release();
            }
        };
        producer.setPriority(Thread.MAX_PRIORITY);
        producer.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
    }
}
