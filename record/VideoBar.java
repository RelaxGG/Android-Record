package com.ourstu.opensnsh5.record;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
 * Created by Lulu on 2016/10/20.
 *
 */
public class VideoBar extends View {
    //取消状态为红色bar, 反之为绿色bar
    private boolean isCancel = false;
    private Context mContext;
    //正在录制的画笔
    private Paint mRecordPaint;
    //上滑取消时的画笔
    private Paint mCancelPaint;
    //是否显示
    private int mVisibility;
    // 当前进度
    private int progress;
    //进度条结束的监听
    private OnProgressEndListener mOnProgressEndListener;

    public VideoBar(Context context) {
        super(context, null);
    }
    public VideoBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init();
    }
    private void init() {
        mVisibility = INVISIBLE;
        mRecordPaint = new Paint();
        mRecordPaint.setColor(Color.GREEN);
        mCancelPaint = new Paint();
        mCancelPaint.setColor(Color.RED);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mVisibility == View.VISIBLE) {
            int height = getHeight();
            int width = getWidth();
            int mid = width / 2;

            //画出进度条
            if (progress < mid){
                canvas.drawRect(progress, 0, width-progress, height, isCancel ? mCancelPaint : mRecordPaint);
            } else {

                if (mOnProgressEndListener != null) {
                    mOnProgressEndListener.onProgressEndListener();
                }
            }
        } else {
            progress = 0;
            canvas.drawColor(Color.argb(0, 0, 0, 0));
        }
    }

    /**
     * 设置进度
     * @param progress int
     */
    public void setProgress(int progress) {
        this.progress = progress;
        invalidate();
    }

    /**
     * 设置录制状态 是否为取消状态
     * @param isCancel boolean
     */
    public void setCancel(boolean isCancel) {
        this.isCancel = isCancel;
        invalidate();
    }
    /**
     * 重写是否可见方法
     * @param visibility int
     */
    @Override
    public void setVisibility(int visibility) {
        mVisibility = visibility;
        //重新绘制
        invalidate();
    }

    /**
     * 当进度条结束后的 监听
     * @param onProgressEndListener OnProgressEndListener
     */
    public void setOnProgressEndListener(OnProgressEndListener onProgressEndListener) {
        mOnProgressEndListener = onProgressEndListener;
    }


    interface OnProgressEndListener{
        void onProgressEndListener();
    }

}
