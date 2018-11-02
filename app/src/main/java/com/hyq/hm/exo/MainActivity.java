package com.hyq.hm.exo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;

import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoTimeListener;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private String[] denied;
    private String[] permissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE};

    private Handler mainHandler;

    private SeekBar seekBar;

    private Surface mSurface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainHandler = new Handler();


        eglUtils = new EGLUtils();
        frame = new GLFrame();
        surfaceView = findViewById(R.id.surface_view);

        seekBar = findViewById(R.id.seek_bar);
        playView = findViewById(R.id.play_view);

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                if(player != null){
                    screenWidth = width;
                    screenHeight = height;
                    player.setVideoSurface(holder.getSurface());
                    player.setPlayWhenReady(isPlayer);
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isTracking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                player.seekTo(seekBar.getProgress()*player.getDuration()/100);
                if(!player.getPlayWhenReady()){
                    isTracking = false;
                }
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> list = new ArrayList<>();
            for (int i = 0; i < permissions.length; i++) {
                if (PermissionChecker.checkSelfPermission(this, permissions[i]) == PackageManager.PERMISSION_DENIED) {
                    list.add(permissions[i]);
                }
            }
            if (list.size() != 0) {
                denied = new String[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    denied[i] = list.get(i);
                }
                ActivityCompat.requestPermissions(this, denied, 5);
            } else {
                init();
            }
        } else {
            init();
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 5) {
            boolean isDenied = false;
            for (int i = 0; i < denied.length; i++) {
                String permission = denied[i];
                for (int j = 0; j < permissions.length; j++) {
                    if (permissions[j].equals(permission)) {
                        if (grantResults[j] != PackageManager.PERMISSION_GRANTED) {
                            isDenied = true;
                            break;
                        }
                    }
                }
            }
            if (isDenied) {
                Toast.makeText(this, "请开启权限", Toast.LENGTH_SHORT).show();
            } else {
                init();

            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private SimpleExoPlayer player;
    private SurfaceView surfaceView;

    private EGLUtils eglUtils;
    private GLFrame frame;
    private int screenWidth = 0,screenHeight = 0;

    private ImageView playView;


    private void init(){
        Uri url = Uri.parse(Environment.getExternalStorageDirectory().getAbsolutePath() +"/HMSDK/testvr.mp4");
        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();


        TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
        TrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);

        player = ExoPlayerFactory.newSimpleInstance(this, trackSelector);


        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this,
                Util.getUserAgent(this, "ExoPlayerTime"), bandwidthMeter);


        MediaSource videoSource = new ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(url, mainHandler,null);
//        LoopingMediaSource loopingSource = new LoopingMediaSource(videoSource);
        player.addVideoTiemListener(new VideoTimeListener() {

            @Override
            public Surface onSurface(Surface surface) {
                if(surface == null){
                    return null;
                }
                frame.screenSize(screenWidth,screenHeight);
                eglUtils.initEGL(surface);
                frame.initFrame();
                mSurface = new Surface(frame.getSurfaceTexture());
                return mSurface;
            }

            @Override
            public void onSizeChanged(int width, int height) {
                frame.videoSize(width,height);
            }

            @Override
            public void onVideoTimeChanged(long time) {
                frame.drawFrame();
                eglUtils.swap();
            }

            @Override
            public void onRelease() {
                if(frame != null){
                    frame.release();
                }
                if(eglUtils != null){
                    eglUtils.release();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(mSurface != null){
                            mSurface.release();
                            mSurface = null;
                        }
                    }
                });
            }

            @Override
            public void onStart() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(isTracking){
                            isTracking = false;
                        }else{
                            playView.setImageResource(R.drawable.ic_stop);
                            videoTime();
                        }

                    }
                });
            }

            @Override
            public void onStop() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(!isTracking){
                            playView.setImageResource(R.drawable.ic_play);
                            player.setPlayWhenReady(false);
                        }
                    }
                });
            }
        });
        player.prepare(videoSource);
        if(mSurface == null && surfaceView.getWidth() != 0 && surfaceView.getHeight() != 0){
            screenWidth = surfaceView.getWidth();
            screenHeight = surfaceView.getHeight();
            player.setVideoSurface(surfaceView.getHolder().getSurface());
        }
    }
    private boolean isTracking = false;
    private void videoTime(){
        seekBar.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(!isTracking){
                    int progress = (int) (player.getContentPosition()*100/player.getDuration());
                    seekBar.setProgress(progress);
                }
                if(isResume && player.getPlayWhenReady()){
                    videoTime();
                }
            }
        },100);
    }

    private boolean isResume = false;
    private boolean isPlayer = false;
    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            View decorView = getWindow().getDecorView();
            int mHideFlags =
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(mHideFlags);
        }else{
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        isResume = true;

    }

    @Override
    protected void onPause() {
        super.onPause();
        isResume = false;
        if(player != null){
            isPlayer = player.getPlayWhenReady();
            if(isPlayer){
                player.setPlayWhenReady(false);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(player != null){
            player.setPlayWhenReady(false);
            player.stop();
            player.release();
        }

    }

    public void onPlayer(View view){
        if(player.getPlayWhenReady()){
            player.setPlayWhenReady(false);
        }else{
            if(player.getContentPosition() >= player.getDuration()){
                player.seekTo(0);
            }
            player.setPlayWhenReady(true);
        }
    }

}
