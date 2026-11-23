package jp.questpilot.app;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

/**
 * Display cover art rotated by a multiple of 90 degrees, without modifying
 * the original image or clipping landscape covers inside portrait bounds.
 * The quarter-turn value is persisted with the Guide, not with the PNG.
 */
final class RotatableCoverView extends ImageView {
    private int quarterTurns;

    RotatableCoverView(Context context) {
        super(context);
        setScaleType(ScaleType.MATRIX);
    }

    void setQuarterTurns(int turns) {
        quarterTurns = ((turns % 4) + 4) % 4;
        updateImageMatrix();
    }

    @Override public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        updateImageMatrix();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateImageMatrix();
    }

    private void updateImageMatrix() {
        Drawable image = getDrawable();
        if (image == null) return;
        int iw = image.getIntrinsicWidth();
        int ih = image.getIntrinsicHeight();
        int vw = getWidth() - getPaddingLeft() - getPaddingRight();
        int vh = getHeight() - getPaddingTop() - getPaddingBottom();
        if (iw <= 0 || ih <= 0 || vw <= 0 || vh <= 0) return;

        // At 90/270 degrees, fit against the swapped visible image bounds.
        int rotatedWidth = (quarterTurns % 2 == 0) ? iw : ih;
        int rotatedHeight = (quarterTurns % 2 == 0) ? ih : iw;
        float scale = Math.min((float) vw / rotatedWidth, (float) vh / rotatedHeight);
        Matrix transform = new Matrix();
        transform.postTranslate(-iw / 2f, -ih / 2f);
        transform.postRotate(quarterTurns * 90f);
        transform.postScale(scale, scale);
        transform.postTranslate(getPaddingLeft() + vw / 2f, getPaddingTop() + vh / 2f);
        setImageMatrix(transform);
    }
}
