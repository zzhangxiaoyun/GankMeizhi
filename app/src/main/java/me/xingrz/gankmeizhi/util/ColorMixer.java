/*
 * Copyright 2015 XiNGRZ <chenxingyu92@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.xingrz.gankmeizhi.util;

import android.graphics.Color;
import android.support.annotation.ColorInt;
import android.support.v4.graphics.ColorUtils;

public class ColorMixer {

    /**
     * 将两个颜色叠加起来
     *
     * @param background 背景色
     * @param foreground 前景色
     * @param ratio      调节前景色的透明度比例
     * @return 混合后的颜色
     */
    public static int mix(@ColorInt int background, @ColorInt int foreground, float ratio) {
        int alpha = Color.alpha(foreground);
        alpha = (int) Math.floor((float) alpha * ratio);
        foreground = ColorUtils.setAlphaComponent(foreground, alpha);
        return ColorUtils.compositeColors(foreground, background);
    }

}
