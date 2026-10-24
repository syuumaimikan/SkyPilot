package dev.skypilot.android;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/** Resolution-independent flight illustration; honours Android's animation setting. */
public final class OrbitView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
    private float phase;

    public OrbitView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        animator.setDuration(8000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(value -> { phase = (float) value.getAnimatedValue(); invalidate(); });
    }

    @Override public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        if (visible && !isInEditMode() && ValueAnimator.areAnimatorsEnabled()) animator.start();
        else animator.cancel();
    }

    @Override protected void onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        float scale = Math.min(getWidth() / 340f, getHeight() / 180f);
        canvas.translate((getWidth() - 340f * scale) / 2f, (getHeight() - 180f * scale) / 2f);
        canvas.scale(scale, scale);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(180, 88, 108, 0x405F55CB, 0x005F55CB, Shader.TileMode.CLAMP));
        canvas.drawCircle(180, 88, 108, paint);
        paint.setShader(null);
        paint.setColor(0xFF34415F);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);
        canvas.save();
        canvas.rotate(-18, 170, 90);
        canvas.drawOval(48, 37, 294, 143, paint);
        canvas.drawOval(18, 18, 322, 162, paint);
        canvas.restore();
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(117, 41, 216, 142, 0xFF605FB8, 0xFF202C58, Shader.TileMode.CLAMP));
        canvas.drawCircle(170, 88 + phase * 6, 54, paint);
        paint.setShader(null);
        paint.setColor(0xFF82E2ED);
        canvas.drawCircle(57, 111, 5, paint);
        paint.setColor(0xFFB5A7FF);
        canvas.drawCircle(278, 65, 4, paint);
        for (int i = 0; i < 14; i++) {
            paint.setColor(i % 2 == 0 ? 0xFF6C779B : 0xFFB9C2DB);
            canvas.drawCircle(20 + (i * 47) % 310, 16 + (i * 37) % 145, i % 3 == 0 ? 1.5f : 1, paint);
        }
        canvas.translate(0, -phase * 7);
        Path plane = new Path();
        plane.moveTo(143, 97); plane.lineTo(222, 54); plane.lineTo(198, 126);
        plane.lineTo(179, 101); plane.close();
        paint.setShader(new LinearGradient(145, 55, 210, 122, 0xFFF0F2FF, 0xFF9EAAFF, Shader.TileMode.CLAMP));
        canvas.drawPath(plane, paint);
        paint.setShader(null);
        paint.setColor(0xFF6C70BD);
        Path fold = new Path();
        fold.moveTo(179, 101); fold.lineTo(222, 54); fold.lineTo(165, 93); fold.close();
        canvas.drawPath(fold, paint);
        canvas.restore();
    }
}
