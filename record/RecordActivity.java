package com.ourstu.opensnsh5.record;

import android.content.Intent;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ourstu.opensnsh5.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.src.dcloud.adapter.DCloudBaseActivity;

public class RecordActivity extends DCloudBaseActivity implements SurfaceHolder.Callback, View.OnTouchListener,VideoBar.OnProgressEndListener {
    private static final String TAG = "RecordActivity";
    private static final int LISTENER_START = 200;
    protected SurfaceView mSurfaceView;
    private boolean mStartedFlg = false;//是否正在录像
    private boolean mIsPlay = false;//是否正在播放录像
    private MediaRecorder mRecorder;
    private SurfaceHolder mSurfaceHolder;
    private VideoBar mVideoBar;
    private TextView mTvTip;
    private Camera mCamera;
    private ImageView cameraSwitch;
    private MediaPlayer mediaPlayer;
    private String path;
    RelativeLayout recView;
    TextView recBtn;
    private TextView mCountdown;
    private CountDownTimer mCountDownTimer;
    SimpleDateFormat simpleDateFormat;
    //进度条轮询周期(ms)
    private int TASK_TIME = 20;
    //当前进度/时间
    private float mProgress = 0;
    //进度条增加率
    private float mRate = 0;
    //是否上滑取消
    private boolean isCancel;
    //摄像头代号
    private int cameraCode = 0;
    //手势处理, 主要用于变焦 (双击放大缩小)
    private GestureDetector mDetector;
    //是否放大
    private boolean isZoomIn = false;
    private RelativeLayout toolsGroup;
    private android.os.Handler handler = new Handler();
    private Runnable mProgressRunnable = new Runnable() {
        @Override
        public void run() {
            mProgress+=mRate;
            mVideoBar.setProgress((int)mProgress);
            handler.postDelayed(this, TASK_TIME);
        }
    };

    //传递过来的参数
    JSONObject cfgObj;
    //录制最大时间(默认20s)
    private int maximum = 20;
    //视频质量(正相关,值介于1-3之间)
    private int quality = 1;
    //存放目录
    private String saveDir = "";
    //文件名
    private String vdName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_video_rec);

        //获取传递参数
        String config = this.getIntent().getStringExtra("config");
        saveDir = this.getIntent().getStringExtra("dir");
        try {
            cfgObj = new JSONObject(config);
            maximum = cfgObj.optInt("maximum",20);
            maximum = maximum<3?20:maximum;         //录制时间不得低于3秒
            quality = cfgObj.optInt("quality",1);
            quality = quality>3||quality<1?2:quality;//视频质量介于1-3之间
            vdName = cfgObj.optString("name");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        mSurfaceView = (SurfaceView) findViewById(R.id.surface_view);
        mVideoBar = (VideoBar) findViewById(R.id.video_bar);
        int sWidth = this.getWindowManager().getDefaultDisplay().getWidth();
        mRate = ((float)sWidth/2/maximum)/(1000/TASK_TIME);
        mTvTip = (TextView) findViewById(R.id.video_tip);
        cameraSwitch = (ImageView) findViewById(R.id.camera_cut);
        recView = (RelativeLayout) findViewById(R.id.press_rl_view);
        recBtn = (TextView) findViewById(R.id.rec_trigger);
        mCountdown = (TextView) findViewById(R.id.vd_countdown);

        toolsGroup = (RelativeLayout) findViewById(R.id.tools_group);
        TextView delBtn = (TextView) findViewById(R.id.del_btn);
        TextView playBtn = (TextView) findViewById(R.id.play_btn);
        TextView okayBtn = (TextView) findViewById(R.id.okay_btn);

        simpleDateFormat = new SimpleDateFormat("mm:ss", Locale.CHINA);
        mDetector = new GestureDetector(this, new ZoomGestureListener());
        //单独处理mSurfaceView的双击事件
        mSurfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mDetector.onTouchEvent(event);
                return true;
            }
        });

        SurfaceHolder holder = mSurfaceView.getHolder();
        holder.addCallback(this);
        // setType必须设置，要不出错.
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        recView.setOnTouchListener(this);
        mVideoBar.setOnProgressEndListener(this);
        ToolsClickListener toolsClickListener = new ToolsClickListener();
        cameraSwitch.setOnClickListener(toolsClickListener);
        playBtn.setOnClickListener(toolsClickListener);
        okayBtn.setOnClickListener(toolsClickListener);
        delBtn.setOnClickListener(toolsClickListener);
        int numberOfCameras = Camera.getNumberOfCameras();
        if (numberOfCameras<2){
            cameraSwitch.setVisibility(View.GONE);
        }
    }
    private class ToolsClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            switch (v.getId()){
                case R.id.camera_cut:
                    cameraCode = cameraCode==1?0:1;
                    releaseCamera();
                    startPreView();
                    break;
                case R.id.play_btn:
                    videoPlay();
                    break;
                case R.id.okay_btn:
                    RecordActivity.this.setResult(1001, new Intent().putExtra("path",path));
                    finish();
                    break;
                case R.id.del_btn://放弃并删除
                    recView.setVisibility(View.VISIBLE);
                    toolsGroup.setVisibility(View.INVISIBLE);
                    delVideo();
                    break;
            }
        }
    }

    /**
     * 开启预览
     */
    private void startPreView() {
        mCountdown.setVisibility(View.INVISIBLE);
        cameraSwitch.setVisibility(View.VISIBLE);
        mTvTip.setVisibility(View.VISIBLE);
        mTvTip.setText("双击放大");
        if (mCamera == null) {
            mCamera = Camera.open(cameraCode);
        }
        if (mCamera != null) {
            mCamera.setDisplayOrientation(90);
            try {
                mCamera.setPreviewDisplay(mSurfaceHolder);
                Camera.Parameters parameters = mCamera.getParameters();
                //实现Camera自动对焦
                List<String> focusModes = parameters.getSupportedFocusModes();
                if (focusModes != null) {
                    for (String mode : focusModes) {
                        mode.contains("continuous-video");
                        parameters.setFocusMode("continuous-video");
                    }
                }
                mCamera.setParameters(parameters);
                mCamera.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 开始录制
     */
    private void startRecord() {
        resetPlayer();
        if (!mStartedFlg) {
            try {
                if (mRecorder == null) {
                    mRecorder = new MediaRecorder();
                }
                mCamera.unlock();
                CamcorderProfile mProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
                int width = mProfile.videoFrameWidth;
                mRecorder.setCamera(mCamera);
                // 这两项需要放在setOutputFormat之前
                mRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
                mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

                // Set output file format
                mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

                // 这两项需要放在setOutputFormat之后
                mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.MPEG_4_SP);

//                mRecorder.setVideoSize(mProfile.videoFrameWidth, mProfile.videoFrameHeight);
                mRecorder.setVideoSize(640, 480);
                mRecorder.setVideoFrameRate(30);
                mRecorder.setVideoEncodingBitRate(quality * 1024 * 1024);
                mRecorder.setOrientationHint(cameraCode==1?270:90);
                //设置记录会话的最大持续时间（毫秒）
                mRecorder.setMaxDuration(maximum * 1000);
                mRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());

                File dir = new File(saveDir);
                if (!dir.exists()) {
                    dir.mkdir();
                }
                path = dir +"/"+ vdName +"_"+ getDate() + ".mp4";
                mRecorder.setOutputFile(path);
                mRecorder.prepare();
                mRecorder.start();
                mStartedFlg = true;
                mProgress = 0f;
                mVideoBar.setVisibility(View.VISIBLE);
                handler.postDelayed(mProgressRunnable, TASK_TIME);
            } catch (Exception e) {
                releaseCamera();
                e.printStackTrace();
            }
        } else {
            stopRecord(false);
        }
    }

    /**
     * 停止录制并释放相机资源
     * @param flag boolean 是否保存视屏
     */
    private void stopRecord(boolean flag) {
        cameraSwitch.setVisibility(View.INVISIBLE);
        mTvTip.setVisibility(View.INVISIBLE);
        mVideoBar.setVisibility(View.INVISIBLE);
        if(mRecorder != null){
            try{
                mRecorder.stop();
            }catch(Exception e){
                e.printStackTrace();
            }
            mRecorder.reset();
            mRecorder.release();
            mRecorder = null;
        }
        releaseCamera();
        handler.removeCallbacks(mProgressRunnable);
        mStartedFlg = false;
        if(!flag){
            delVideo();
        }else{
            toolsGroup.setVisibility(View.VISIBLE);
            recView.setVisibility(View.GONE);
        }
    }

    /*删除文件*/
    private void delVideo(){
        if(path != null){
            File recFile = new File(path);
            if (recFile.exists()) {
                recFile.delete();
            }
            resetPlayer();
            path = null;
            startPreView();
        }
    }

    /*初始化播放器*/
    private void resetPlayer() {
        if (mIsPlay && mediaPlayer != null) {
            mIsPlay = false;
            mediaPlayer.stop();
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
    /*释放相机*/
    private void releaseCamera(){
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }
    /**
     * 播放视频
     */
    private void videoPlay(){
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        }
        mediaPlayer.reset();
        Uri uri = Uri.parse(path);
        mediaPlayer = MediaPlayer.create(RecordActivity.this, uri);
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setDisplay(mSurfaceHolder);
        try {
            mediaPlayer.prepare();
        } catch (Exception e) {
            e.printStackTrace();
        }
        mIsPlay = true;
        mediaPlayer.start();
        //倒计时器
        if(mCountDownTimer != null){
            mCountDownTimer.cancel();
        }
        mCountDownTimer = new CountDownTimer((long)mediaPlayer.getDuration(),1000){
            @Override
            public void onTick(long millisUntilFinished) {
                mCountdown.setText(simpleDateFormat.format(new Date(millisUntilFinished)));
            }

            @Override
            public void onFinish() {
                mCountdown.setText("00:00");
            }
        }.start();
        mCountdown.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mSurfaceHolder = holder;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int i, int i1, int i2) {
        // 将holder，这个holder为开始在onCreate里面取得的holder，将它赋给mSurfaceHolder
        mSurfaceHolder = holder;
        if(path == null){
            startPreView();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        releaseCamera();
        mStartedFlg = false;
        handler.removeCallbacks(mProgressRunnable);
        if (mRecorder != null) {
            mRecorder.release();
            mRecorder = null;
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        boolean ret = false;
        int action = event.getAction();
        float ey = event.getY();
        float ex = event.getX();
        //只监听中间的按钮处
        int vW = v.getWidth();
        int right = vW - LISTENER_START;
        float downY = 0;
        switch (v.getId()) {
            case R.id.press_rl_view: {
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        if (ex > LISTENER_START && ex < right) {
                            mVideoBar.setCancel(false);
                            //显示上滑取消
                            mTvTip.setVisibility(View.VISIBLE);
                            mTvTip.setText("↑ 上滑取消");
                            //记录按下的Y坐标
                            downY = ey;
                            //开始录制
                            startRecord();
                            ret = true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        //判断是否为录制结束, 或者为成功录制(时间过短)
                        if(mStartedFlg){
                            if (!isCancel) {
                                if (mProgress < 1000/TASK_TIME*mRate) {
                                    stopRecord(false);
                                    Toast.makeText(RecordActivity.this,"时间太短,录制无效!",Toast.LENGTH_SHORT).show();
                                    break;
                                }
                                stopRecord(true);
                            } else {
                                stopRecord(false);
                                isCancel = false;
                                mVideoBar.setCancel(false);
                            }
                        }
                        ret = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float currentY = event.getY();
                        if (downY - currentY > 10) {
                            if(!isCancel){
                                isCancel = true;
                                mVideoBar.setCancel(true);
                                mTvTip.setText("松开取消录制");
                            }
                        }else{
                            if(isCancel){
                                isCancel = false;
                                mVideoBar.setCancel(false);
                                mTvTip.setText("↑ 上滑取消");
                            }
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL://处理第一次调用时权限申请框打乱操作的BUG
                        mVideoBar.setVisibility(View.INVISIBLE);
                        stopRecord(false);
                        break;
                }
                break;
            }
        }
        return ret;
    }

    @Override
    public void onProgressEndListener() {
        //视频停止录制
        stopRecord(true);
    }

    /**
     * 相机变焦
     */
    public void setZoom(int zoomValue) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            if (parameters.isZoomSupported()) {//判断是否支持
                int maxZoom = parameters.getMaxZoom();
                if (maxZoom == 0) {
                    return;
                }
                if (zoomValue > maxZoom) {
                    zoomValue = maxZoom;
                }
                parameters.setZoom(zoomValue);
                mCamera.setParameters(parameters);
            }
        }
    }

    /**
     *  变焦手势处理类
     */
    private class ZoomGestureListener extends GestureDetector.SimpleOnGestureListener {
        //双击手势事件
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            super.onDoubleTap(e);
            if (!isZoomIn) {
                setZoom(20);
                isZoomIn = true;
            } else {
                setZoom(0);
                isZoomIn = false;
            }
            return true;
        }
    }

    /**
     * 获取系统时间
     * @return String
     */
    public static String getDate() {
        Calendar ca = Calendar.getInstance();
        int year = ca.get(Calendar.YEAR);           // 获取年份
        int month = ca.get(Calendar.MONTH);         // 获取月份
        int day = ca.get(Calendar.DATE);            // 获取日
        int minute = ca.get(Calendar.MINUTE);       // 分
        int hour = ca.get(Calendar.HOUR);           // 小时
        int second = ca.get(Calendar.SECOND);       // 秒

        String date = "" + year + (month + 1) + day + hour + minute + second;
        Log.d(TAG, "date:" + date);

        return date;
    }

    /**
     * 获取SD path
     * @return String
     */
    public String getSDPath() {
        File sdDir;
        boolean sdCardExist = Environment.getExternalStorageState()
                .equals(android.os.Environment.MEDIA_MOUNTED); // 判断sd卡是否存在
        if (sdCardExist) {
            sdDir = Environment.getExternalStorageDirectory();// 获取跟目录
            return sdDir.toString();
        }
        return null;
    }
}