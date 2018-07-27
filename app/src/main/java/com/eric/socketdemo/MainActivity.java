package com.eric.socketdemo;

import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.net.UnknownHostException;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    public MyHandler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHandler = new MyHandler(this);

        mCardInfoReceive = new CardInfoReceive();
        mCardInfoThd = new Thread(mCardInfoReceive);
        mCardInfoThd.start();

    }

    public String server_ip = "172.17.16.26";
    public String timeSyc = "F10000000011000000000C000000000000";
    //	public String server_ip = "192.168.150.202";
    public String mCardNum = null;
    public int server_port = 903;
    public int timeCount = 0;
    public int timeInterval = 5;
    public int cardID;
    public int reConnectTime = 30 * 1000;
    public boolean ret;
    public boolean isSocketConnect = false;
    public boolean isReconnect = true;
    public boolean isNeedSendHeart;
    public boolean isDestroy = false;
    public InputStream in;
    public OutputStream out;
    public Socket socket;
    Thread mCardInfoThd = null;
    SynTimeThread synTimeThread = new SynTimeThread();
    CardInfoReceive mCardInfoReceive = null;


    private static final int MSG_CARD_NO = 0x01;
    private static final int MSG_TIME_COUNT = 0x02;

    static class MyHandler extends Handler {
        WeakReference<MainActivity> mActivityReference;

        MyHandler(MainActivity activity) {
            mActivityReference = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            MainActivity activity = mActivityReference.get();
            switch (msg.what) {
                case MSG_TIME_COUNT:
                    activity.timeCount++;
                    activity.requestCountTime();
                    if (activity.timeCount > activity.timeInterval) {
                        activity.isNeedSendHeart = true;
                        activity.timeCount = 0;
                        activity.synTimeThread.start();
                    }
                    break;
            }
        }
    }

    class CardInfoReceive implements Runnable {
        private boolean running;
        private CardInfoReceive() { running = true; }
        private void setStop() {
            running = false;
        }
        @Override
        public void run() {
            while (running) {
                ret = connect();
                while (!ret) {
//					ret = connect();
                    release();
                    if (isDestroy){
                        break;
                    }
                }
            }
        }
    }

    private boolean connect () {
        try {
            Log.e(TAG, "connect ip:" + server_ip + ":"+ server_port);
            mHandler.removeMessages(MSG_TIME_COUNT);

            socket = new Socket(server_ip, server_port);
            isSocketConnect = socket.isConnected();
            in = socket.getInputStream();
            out = socket.getOutputStream();
            byte[] buffer = new byte[100];
            startHeartBeat();
            Log.e(TAG, "wait for card buffer");
            in.read(buffer);
            Log.e(TAG, "array:" + Integer.toHexString(buffer[33]) + " " +Integer.toHexString(buffer[34]) + " " + Integer.toHexString(buffer[35]) + " " + Integer.toHexString(buffer[36]));
            byte[] cardIdByte = new byte[4];
            cardIdByte[0] = buffer[33];
            cardIdByte[1] = buffer[34];
            cardIdByte[2] = buffer[35];
            cardIdByte[3] = buffer[36];

            mCardNum = null;

            cardID = byteArrayToInt(cardIdByte);
            mCardNum = cardID + "";
            Log.e(TAG, "mCardNum" + mCardNum);
            Message msg = new Message();
            msg.what = MSG_CARD_NO;
            msg.obj = mCardNum;
            mHandler.sendMessage(msg);

        } catch (UnknownHostException e) {
            Log.e(TAG, "UnknownHostException:" + e.toString());
            isSocketConnect = false;
        } catch (IOException e) {
            Log.e(TAG, "IOException:" + e.toString());
            isSocketConnect = false;
        } catch (Exception e) {
            Log.e(TAG, "Exception:" + e.toString());
            isSocketConnect = false;
        }

        return isSocketConnect;
    }

    public void release() {
        Log.e(TAG, "socket release");
        try {
            if (in != null) {
                in.close();
            }
            in = null;
            if (out != null) {
                out.close();
            }
            out = null;

            if (socket != null) {
                socket.close();
            }
            socket = null;
            if (isReconnect) {
                Thread.sleep(reConnectTime);
                Log.e(TAG, "try to reconnect socket");
                ret = connect();
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    class  SynTimeThread extends Thread {
        @Override
        public void run() {
            super.run();
            if (isNeedSendHeart && !isDestroy) {
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
        isReconnect = false;
        isNeedSendHeart = false;
        release();
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

    @Override
    protected void onDestroy() {
        stopCardReceive();
        super.onDestroy();
    }
}
