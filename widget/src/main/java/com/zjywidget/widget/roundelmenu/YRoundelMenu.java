package com.zjywidget.widget.roundelmenu;

/**
 * Created by Yang on 2019/4/13.
 */

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import com.zjywidget.widget.R;

/**
 * <b>Project:</b> https://github.com/GitHubZJY/ZJYWidget <br>
 * <b>Create Date:</b> 2019/04/04 <br>
 * <b>@author:</b> Yang <br>
 * <b>Description:</b> 炫酷圆盘菜单View <br>
 */
public class YRoundelMenu extends ViewGroup {

    /**
     * 展开状态
     */
    public static final int STATE_COLLAPSE = 0;

    /**
     * 收缩状态
     */
    public static final int STATE_EXPAND = 1;

    /**
     * 收缩时的半径
     */
    private int collapsedRadius;

    /**
     * 展开时的半径
     */
    private int expandedRadius;

    /**
     * 收缩状态时的颜色 / 展开时外圈的颜色
     */
    private int mRoundColor;

    /**
     * 展开时中心圆圈的颜色
     */
    private int mCenterColor;

    /**
     * 中心图标
     */
    private Drawable mCenterDrawable;

    /**
     * 子项的宽高
     */
    private int mItemWidth;

    /**
     * 当前展开的进度（0-1）
     */
    private float expandProgress = 0;

    /**
     * 当前状态 （展开 / 收缩）
     */
    private int state;

    /**
     * 展开或收缩的动画时长
     */
    private int mDuration;

    /**
     * 子View之间的动画间隔
     */
    private int mItemAnimIntervalTime;

    private Point center;
    private Paint mRoundPaint;
    private Paint mCenterPaint;
    private OvalOutline outlineProvider;
    private ValueAnimator mExpandAnimator;
    private ValueAnimator mColorAnimator;

    public YRoundelMenu(Context context) {
        this(context, null);
    }

    public YRoundelMenu(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public YRoundelMenu(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public YRoundelMenu(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    private void handleStyleable(Context context, AttributeSet attrs){
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.YRoundelMenu);
        collapsedRadius = ta.getDimensionPixelSize(R.styleable.YRoundelMenu_round_menu_collapsedRadius, dp2px(22));
        expandedRadius = ta.getDimensionPixelSize(R.styleable.YRoundelMenu_round_menu_expandedRadius, dp2px(84));
        mRoundColor = ta.getColor(R.styleable.YRoundelMenu_round_menu_roundColor, Color.parseColor("#ffffbb33"));
        mCenterColor = ta.getColor(R.styleable.YRoundelMenu_round_menu_centerColor, Color.parseColor("#ffff8800"));
        mDuration = ta.getInteger(R.styleable.YRoundelMenu_round_menu_duration, 400);
        mItemAnimIntervalTime = ta.getInteger(R.styleable.YRoundelMenu_round_menu_item_anim_delay, 50);
        mItemWidth = ta.getDimensionPixelSize(R.styleable.YRoundelMenu_round_menu_item_width, dp2px(22));
        ta.recycle();

        if (collapsedRadius > expandedRadius) {
            throw new IllegalArgumentException("expandedRadius must bigger than collapsedRadius");
        }
    }

    /**
     * 构造器方法 初始化准备 还没开始画
     * 1 设置Paint的各种属性（还未开始画）
     * 2 如果是ViewGroup 设置setWillNotDraw()，让ViewGroup走onDraw()方法
     * 3 圆形，圆角，阴影，初始化OutlineProvider并进行绘制，并设置Z轴
     * 4 初始化动画，设置好插值器 listener（调用invalidate()进行重绘）
     * @param context
     * @param attrs
     */
    private void init(Context context, AttributeSet attrs) {

        handleStyleable(context, attrs);

        mRoundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mRoundPaint.setColor(mRoundColor);
        /*
         * STROKE 描边
         * FILL 填充
         * FILL_AND_STROKE 描边加填充
         *
         */
        mRoundPaint.setStyle(Paint.Style.FILL);
        //抗锯齿
        //初始化Paint画笔，此时还没开始画
        mCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCenterPaint.setColor(mRoundColor);
        mCenterPaint.setStyle(Paint.Style.FILL);
        //ViewGroup的onDraw不会被执行，默认透明，使用此方法清楚标志位使onDraw能够执行
        setWillNotDraw(false);

        //初始化OutlineProvider（此时并未真正应用）
        if (Build.VERSION.SDK_INT >= 21) {
            outlineProvider = new OvalOutline();
            //设置Z轴高度（阴影 和 view堆叠关系）
            setElevation(dp2px(5));
        }
        center = new Point();
        mCenterDrawable = getResources().getDrawable(R.drawable.ic_menu);
        state = STATE_COLLAPSE;

        initAnim();
    }

    private void initAnim(){
        //0-0是什么操作--先new出来 使用的时候再设置
        mExpandAnimator = ValueAnimator.ofFloat(0, 0);
        //设置插值器，数值随时间变化的曲线  OvershootInterpolator超过1后回到1，即回弹效果
        mExpandAnimator.setInterpolator(new OvershootInterpolator());
        //属性里面读出来的持续时间
        mExpandAnimator.setDuration(mDuration);
        mExpandAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                //通过Animator是实现expendProgress数值的变化，同时expendProgress变化的同时调用invalidate()进行重绘
                //这个也是调用invalidateOutline()的时机，进行OutlineProvider的刷新
                expandProgress = (float)animation.getAnimatedValue();
                //注意setColor和setAlpha的顺序，setColor会顺便把透明度也设置了
                mRoundPaint.setAlpha(Math.min(255, (int) (expandProgress * 255)));

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    //触发OvalOutline.getOutline()方法
                    invalidateOutline();
                }
                //view也进行重绘
                invalidate();
            }
        });

        /*
          ofObject方法传入一个自定义的TypeEvaluator
          重写   fraction为进度，start end为起始和结束值
          public T evaluate(float fraction, T startValue, T endValue)
          T代表返回的值，色彩的值，字符串值，int值等等，因为fraction为float类型，因此返回值和float的关系也需要自己进行关系定义

          fraction我猜是 setDuration和start end共同作用出来的
          通过Listener的getAnimatedValue()可以获得线性变化的fraction和传进去的T共同作用返回的T（int 字符串 颜色等）

          ArgbEvaluator为系统提供的一个颜色渐变的类
         */
        mColorAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), mRoundColor, mCenterColor);
        mColorAnimator.setDuration(mDuration);
        mColorAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mCenterPaint.setColor((Integer) animation.getAnimatedValue());
            }

        });
    }


    public float getExpandProgress() {
        return expandProgress;
    }

    /**
     * onTouchEvent调用，点击即刻invalidate()
     * 同时开始动画
     * @param animate
     */
    void collapse(boolean animate) {
        state = STATE_COLLAPSE;
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).setVisibility(View.GONE);
        }
        invalidate();
        if (animate) {
            startCollapseAnimation();
        }
    }


    void expand(boolean animate) {
        state = STATE_EXPAND;
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).setVisibility(View.VISIBLE);
        }
        invalidate();
        if (animate) {
            startExpandAnimation();
        } else {
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).setAlpha(1);
            }
        }
    }

    /**
     * onMeasure()设置View的宽高
     * @param widthMeasureSpec
     * @param heightMeasureSpec
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        //View的标准流程，不仅需要setMeasuredDimension()设置自己的宽高，还需要设置子View的宽高
        //也可以调用measureChildWithMargins()方法适配margin属性
        setMeasuredDimension(width, height);
        measureChildren(widthMeasureSpec, heightMeasureSpec);
    }


    /**
     * onLayout()用于确定子元素的位置
     * 调用子View的layout()方法，里面有setFrame()方法设置子View的四个角坐标
     * 因此如果没有子元素，直接return()
     * @param changed
     * @param l
     * @param t
     * @param r
     * @param b
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (getChildCount() == 0) {
            return;
        }
        //此方法内已经为子view set X Y，最后再调用子View的layout方法
        calculateMenuItemPosition();
        for (int i = 0; i < getChildCount(); i++) {
            View item = getChildAt(i);
            item.layout(l + (int)item.getX(),
                    t + (int)item.getY(),
                    l + (int)item.getX() + item.getMeasuredWidth(),
                    t + (int)item.getY() + item.getMeasuredHeight());
        }
    }

    /**
     * 在布局文件加载View实例的时候在inflate()过程回调 但是在代码里面new是不会回调此方法的
     * 当View中所有的子控件均被映射成xml后触发  用于初始化子View
     *
     * 子View是在Activity里面setContentView()的时候添加进去的，因此构造方法内不可初始化
     * 子View只有在三大流程和这个onFinishInflate内才能获取到，但是三大流程会重复调用，因此放此处初始化子View最合适
     */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        for (int i = 0; i < getChildCount(); i++) {
            View item = getChildAt(i);
            item.setVisibility(View.GONE);
            item.setAlpha(0);
            item.setScaleX(1);
            item.setScaleY(1);
        }
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Point touchPoint = new Point();
        touchPoint.set((int) event.getX(), (int) event.getY());
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                //计算触摸点与中心点的距离
                double distance = getPointsDistance(touchPoint, center);
                if(state == STATE_EXPAND){
                    //展开状态下，如果点击区域与中心点的距离不处于子菜单区域，就收起菜单
                    if (distance > (collapsedRadius + (expandedRadius - collapsedRadius) * expandProgress)
                            || distance < collapsedRadius) {
                        collapse(true);
                        return true;
                    }
                    //展开状态下，如果点击区域处于子菜单区域，则不消费事件
                    return false;
                }else{
                    //收缩状态下，如果点击区域处于中心圆圈范围内，则展开菜单
                    if(distance < collapsedRadius){
                        expand(true);
                        return true;
                    }
                    //收缩状态下，如果点击区域不在中心圆圈范围内，则不消费事件
                    return false;
                }
            }
        }
        return super.onTouchEvent(event);
    }


    /**
     * 这个方法再onMeasure()的setFrame()方法中回调，在onMeasure() -> onLayout()之间
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setOutlineProvider(outlineProvider);
        }
        int x, y;
        x = w / 2;
        y = h / 2 ;
        //center在此处进行赋值更新，作为圆心使用
        center.set(x, y);
        //中心图标padding设为10dp
        //setBounds()方法表示drawable将被绘制在canvas的哪个矩形区域内，设置drawable的padding可以在这个方法内设置
        mCenterDrawable.setBounds(center.x - (collapsedRadius - dp2px(10)),
                center.y - (collapsedRadius - dp2px(10)),
                center.x + (collapsedRadius - dp2px(10)),
                center.y + (collapsedRadius - dp2px(10))
        );
    }

    /**
     * onDraw一般由invalidate()控制，此处控制的代码直接反馈到ui上
     * @param canvas
     * canvas 有save() 和 restore()方法
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //绘制放大的圆（绘制环形）
        if (expandProgress > 0f) {
            //x,y为圆心位置，radius为半径，paint为画笔，已经在init初始化
            //此处控制radius值即可通过再动画里面通过invalidate()方法进行动画绘制
            canvas.drawCircle(center.x, center.y, collapsedRadius + (expandedRadius - collapsedRadius) * expandProgress, mRoundPaint);
        }
        //绘制中间圆，44*.2f = 4.4 中间的圆会扩大一丝丝距离
        canvas.drawCircle(center.x, center.y, collapsedRadius + (collapsedRadius * .2f * expandProgress), mCenterPaint);
        // canvas的 save() 和 restore()方法成对出现，为了解决canvas进行 rotate translate等等操作后坐标轴会发生变化
        // 先save再restore进行恢复不影响后续坐标轴相关操作 save会返回一个id值，多次save后可以使用restoreToCount进行弹出到指定的保存
        // saveLayer()方法与save()差不多，但是saveLayer()会新建图层（性能有损耗！）
        // 先saveLayer()新建图层再restore()，会把入栈的图层出栈，同时把图层的内容绘制到上层或canvas上
        //saveLayer()一般配合Paint.setXfermode()使用，避免出现多图层混一起而导致重叠效果出错
        int count = canvas.saveLayer(0, 0, getWidth(), getHeight(), null, Canvas.ALL_SAVE_FLAG);
        //绘制中间的图标
        canvas.rotate(45*expandProgress, center.x, center.y);
        mCenterDrawable.draw(canvas);
        canvas.restoreToCount(count);
    }

    /**
     * 展开动画
     */
    void startExpandAnimation() {
        mExpandAnimator.setFloatValues(getExpandProgress(), 1f);
        mExpandAnimator.start();

        mColorAnimator.setObjectValues(mColorAnimator.getAnimatedValue() == null ? mRoundColor : mColorAnimator.getAnimatedValue(), mCenterColor);
        mColorAnimator.start();

        //默认延迟时间，一个一个出来，默认值50感觉看不出来效果
        int delay = mItemAnimIntervalTime;
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).animate()
                    .setStartDelay(delay)
                    .setDuration(mDuration)
                    .alphaBy(0f)
                    .scaleXBy(0f)
                    .scaleYBy(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .start();
            delay += mItemAnimIntervalTime;
        }
    }

    /**
     * 收缩动画，动画触发invalidate()，导致重绘，颜色位置大小均在动画中改变
     */
    void startCollapseAnimation() {
        //这句兼容了在动画播放过程中再次点击的情况
        mExpandAnimator.setFloatValues(getExpandProgress(), 0f);
        mExpandAnimator.start();

        mColorAnimator.setObjectValues(mColorAnimator.getAnimatedValue() == null ? mCenterColor : mColorAnimator.getAnimatedValue(), mRoundColor);
        mColorAnimator.start();

        int delay = mItemAnimIntervalTime;
        for (int i = getChildCount() - 1; i >= 0; i--) {
            getChildAt(i).animate()
                    .setStartDelay(delay)
                    .setDuration(mDuration)
                    .alpha(0)
                    .scaleX(0)
                    .scaleY(0)
                    .start();
            delay += mItemAnimIntervalTime;
        }
    }


    /**
     * 计算每个子菜单的坐标
     */
    private void calculateMenuItemPosition() {
        //外圆内圆平均半径
        float itemRadius = (expandedRadius + collapsedRadius) / 2f;
        //绘制正方形，用于绘制正方形内以边长为直径的圆   RectF和Rect区别就是使用Float参数，精确度更高
        RectF area = new RectF(
                center.x - itemRadius,
                center.y - itemRadius,
                center.x + itemRadius,
                center.y + itemRadius);
        Path path = new Path();
        path.addArc(area, 0, 360);
        //PathMeasure关联path，forceClosed参数用于确定path是否闭合，如画两根相连的线，闭合则形成三角形，不闭合就是一个角
        //path发生改变则需要重新setPath()
        PathMeasure measure = new PathMeasure(path, false);
        float len = measure.getLength();
        int divisor = getChildCount();
        float divider = len / divisor;

        //平均分这个圆，并定位每个item的位置
        for (int i = 0; i < getChildCount(); i++) {
            float[] itemPoints = new float[2];
            //用于得到路径上某一长度的位置以及该位置的正切值
            //distance：获取点距离起点的长度
            //pos：获取点的坐标，直接写数组
            //tan：获取点的正切值，tan是tangent的缩写，即中学中常见的正切，其中tan[0]是邻边边长，tan[1]是对边边长，而Math中atan2方法是根据正切是数值计算出该角度的大小，得到的单位是弧度，所以上面又将弧度转为了角度。
            //exm:float degrees = (float) (Math.atan2(tan[1], tan[0]) * 180.0 / Math.PI); 计算出角度，可以让图案旋转成当前path斜率的角度
            // ----------此处是获取了n段圆弧，每段圆弧正中间的坐标，反映在itemPoints里面 ---------
            measure.getPosTan(i * divider + divider * 0.5f, itemPoints, null);
            View item = getChildAt(i);
            item.setX((int) itemPoints[0] - mItemWidth / 2);
            item.setY((int) itemPoints[1] - mItemWidth / 2);
        }
    }

    public int getState() {
        return state;
    }

    public void setExpandedRadius(int expandedRadius) {
        this.expandedRadius = expandedRadius;
        requestLayout();
    }


    public void setCollapsedRadius(int collapsedRadius) {
        this.collapsedRadius = collapsedRadius;
        requestLayout();
    }

    public void setRoundColor(int color) {
        this.mRoundColor = color;
        mRoundPaint.setColor(mRoundColor);
        invalidate();
    }

    public void setCenterColor(int color) {
        this.mCenterColor = color;
        mCenterPaint.setColor(color);
        invalidate();
    }


    /**
     * Android5.0后添加的api，用于实现View的阴影和轮廓（圆角）
     * 搭配  View.setOutlineProvider()使用
     *
     * getOutline中
     * 设置圆角/圆形（50f参数不要）
     * outline.setRoundRect(0, 0, view.width, view.height, 50f)
     * 设置投影，需要一个path
     * setConvexPath()
     * 设置椭圆投影
     * outline.setOval(0, 0, view.width, view.height)
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public class OvalOutline extends ViewOutlineProvider {

        public OvalOutline() {
            super();
        }

        //TODO getOutline调用时机
        @Override
        public void getOutline(View view, Outline outline) {
            //expandProgress用于动画，动态扩大半径
            //TODO 查明getOutline的调用时机，很明显对象创建的时候此方法还未被调用
            int radius = (int) (collapsedRadius + (expandedRadius - collapsedRadius) * expandProgress);
            /*
                Rect 长方形
                构造方法中 left top为左上角的点坐标，right bottom为左下角的点坐标
                此处创建一个边长为radius的正方形
            */
            Rect area = new Rect(
                    center.x - radius,
                    center.y - radius,
                    center.x + radius,
                    center.y + radius);
            //把正方形变成圆形
            outline.setRoundRect(area, radius);
        }
    }


    public static double getPointsDistance(Point a, Point b) {
        int dx = b.x - a.x;
        int dy = b.y - a.y;
        return Math.sqrt(dx * dx + dy * dy);
    }


    /**
     * dp转px
     * @param dpVal   dp value
     * @return px value
     */
    public int dp2px(float dpVal) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpVal,
                getContext().getResources().getDisplayMetrics());
    }


}

