package org.wikipedia.views;

import static org.wikipedia.util.L10nUtil.setConditionalLayoutDirection;

import android.content.Context;
import android.text.Spanned;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

public class ConfigurableTextView extends AppCompatTextView {
    public ConfigurableTextView(Context context) {
        super(context);
    }

    public ConfigurableTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ConfigurableTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setText(CharSequence text, String languageCode) {
        super.setText(text);
        setLocale(languageCode);
    }

    public void setText(Spanned text, String languageCode) {
        super.setText(text);
        setLocale(languageCode);
    }

    public void setLocale(String languageCode) {
        setConditionalLayoutDirection(this, languageCode);
    }
}
