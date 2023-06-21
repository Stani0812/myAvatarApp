package com.google.codelab.mlkit;

import static com.google.codelab.mlkit.MainActivity.getVolume;

import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.content.Context;
import android.util.Log;

import android.content.res.AssetManager;

import hrilab.ss.network.*;

public class VideoTask extends TimerTask {
    private static final String TAG = "Video";
    private Handler handler;
    private Context context;
    private NetConnection connection;

    static Bitmap bitmap;

    public VideoTask(Context context) {
        handler = new Handler();
        this.context = context;
        connection = new NetConnection( "Video", "10.229.40.78", 15964 );
        connection.start( );
    }

    public void close()
    {
        try {
            connection.close();
            connection.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if( connection.isConnected( ) )
                {
                    Packet packet = connection.getNextData( );
                    if( packet != null )
                    {
                        //Log.i(TAG, "Received " + packet.getDataLength( ) + " bytes");
                        //((MainActivity)context).InvalidateScreen();
                        bitmap = BitmapFactory.decodeByteArray( packet.getData(), packet.getDataOffset(), packet.getDataLength() );
                        MainActivity.mVideoView.setImageBitmap(bitmap);
                        MainActivity.mVolumeView.out(getVolume());
                        packet.removeRef( );
                    }
                    Thread.yield( );
                }
            }
        });
    }

}
