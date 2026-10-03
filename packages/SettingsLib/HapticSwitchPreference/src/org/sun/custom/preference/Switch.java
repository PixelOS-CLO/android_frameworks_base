/*
 * SPDX-FileCopyrightText: The SunOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.sun.custom.preference;

import android.content.Context;
import android.util.AttributeSet;

public class Switch extends android.widget.Switch {


    public Switch(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public Switch(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public Switch(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.switchStyle);
    }

    public Switch(Context context) {
        this(context, null);
    }

}
