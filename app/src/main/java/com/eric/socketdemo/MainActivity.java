package com.eric.socketdemo;

import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCardInfoReceive = new CardInfoReceive();
        mCardInfoThd = new Thread(mCardInfoReceive);
        mCardInfoThd.start();

    }
    Thread mCardInfoThd = null;
    SynTimeThread synTimeThread = null;
    CardInfoReceive mCardInfoReceive = null;
    public String mCardNum;
    public String server_ip = "172.17.16.26";
    public int server_port = 903;
    public boolean isSocketConnect = false;
    public boolean isSocketClose;
    public int timeCount = 0;
    public int timeInterval = 60;
    public boolean isNeedSendHeart;
    public String timeSyc = "F10000000011000000000C000000000000";
    public InputStream in;
    public OutputStream out;

    private static final int MSG_CARD_NO = 0x01;
    private static final int MSG_TIME_COUNT = 0x02;
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_TIME_COUNT:
                    timeCount ++ ;
                    requestCountTime();
                    if (timeCount > timeInterval) {
                        isNeedSendHeart = true;
                        timeCount = 0;
                        synTimeThread = new SynTimeThread();
                        synTimeThread.start();
                    }
                    break;
                case MSG_CARD_NO:


                    break;
                    default:
                        break;
            }
        }
    };

    class CardInfoReceive implements Runnable {
        private boolean running;
        public CardInfoReceive() { running = true; }
        public void setStop() {
            running = false;
        }
        @Override
        public void run() {
            while (running) {
                boolean ret = connect();
                while (!ret) {
                    ret = connect();
                }
            }
        }
    }

    private boolean connect () {
        try {
            Log.e(TAG, "connect ip:" + server_ip + ":"+ server_port);
            mHandler.removeMessages(MSG_TIME_COUNT);

            Socket socket = new Socket(server_ip, server_port);
            isSocketConnect = socket.isConnected();
            isSocketClose = socket.isClosed();
            in = socket.getInputStream();
            out = socket.getOutputStream();
            byte[] buffer = new byte[100];
            startHeartBeat();
            while (!isSocketClose && !socket.isInputShutdown() && isSocketConnect) {

                if ((in.read()) != -1) {
                    in.read(buffer);

//					for (int i = 0; i < buffer.length; i++) {
//						String a = Integer.toHexString(buffer[i]);
//						Log.e(TAG, "array:" + a);
//					}
                    byte[] cardIdByte = new byte[4];
                    cardIdByte[0] = buffer[32];
                    cardIdByte[1] = buffer[33];
                    cardIdByte[2] = buffer[34];
                    cardIdByte[3] = buffer[35];

                    mCardNum = null;

                    for (int i = 0; i < cardIdByte.length; i++) {
                        String a = Integer.toHexString(cardIdByte[i]);
                        if (a .equals("0")) {
                            a = "00";
                        }
                        mCardNum = mCardNum + a;
                        Log.e(TAG, "cardIdByte:" + a);
                    }

//					cardID = byteArrayToInt(cardIdByte);
//					mCardNum = new String(cardIdByte);
                    Log.e(TAG, "mCardNum" + mCardNum);
                    Message msg = new Message();
                    msg.what = MSG_CARD_NO;
                    msg.obj = mCardNum;
                    mHandler.sendMessage(msg);

                }

            }
        } catch (UnknownHostException e) {
            Log.e(TAG, "UnknownHostException:" + e.toString());
            isSocketConnect = false;
            connect();
        } catch (IOException e) {
            Log.e(TAG, "IOException:" + e.toString());
            isSocketConnect = false;
            connect();
        } catch (Exception e) {
            Log.e(TAG, "Exception:" + e.toString());
            isSocketConnect = false;
            connect();
        }

        return isSocketConnect;
    }

    class  SynTimeThread extends Thread {
        @Override
        public void run() {
            super.run();
            if (isNeedSendHeart) {
                Log.e(TAG, "send Time Syc");
                try {
                    out.write(toBytes(timeSyc));
                    out.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                    isSocketConnect = false;
                }
                isNeedSendHeart = false;
            }
        }
    }

    public void stopCardReceive() {
        if (mCardInfoReceive != null) {
            mCardInfoReceive.setStop();
            mCardInfoReceive = null;
        }
    }

    public void startHeartBeat() {
        requestCountTime();
    }

    public void requestCountTime() {
        mHandler.sendEmptyMessageDelayed(MSG_TIME_COUNT, 1000);
    }

    public static int byteArrayToInt(byte[] bytes) {
        int value = 0;
        // 由高位到低位
        for (int i = 0; i < 4; i++) {
            int shift = (4 - 1 - i) * 8;
            value += (bytes[i] & 0x000000FF) << shift;// 往高位游
        }
        return value;
    }

    public static byte[] toBytes(String str) {
        if(str == null || str.trim().equals("")) {
            return new byte[0];
        }

        byte[] bytes = new byte[str.length() / 2];
        for(int i = 0; i < str.length() / 2; i++) {
            String subStr = str.substring(i * 2, i * 2 + 2);
            bytes[i] = (byte) Integer.parseInt(subStr, 16);
        }

        return bytes;
    }

}
