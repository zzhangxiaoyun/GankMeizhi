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

package me.xingrz.gankmeizhi;

import android.content.Context;
import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.StaggeredGridLayoutManager.LayoutParams;
import android.view.View;

/**
 * 生成瀑布流图片间隔
 *
 * @author XiNGRZ
 */
public class MeizhiItemDecoration extends RecyclerView.ItemDecoration {

    private final int padding;

    public MeizhiItemDecoration(Context context) {
        this.padding = context.getResources().getDimensionPixelSize(R.dimen.grid_padding);
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        int spanCount = ((StaggeredGridLayoutManager) parent.getLayoutManager()).getSpanCount();
        int spanIndex = ((LayoutParams) view.getLayoutParams()).getSpanIndex();

        int left = 0, top = 0;

        // 所有一律添加右边距和底边距
        int right = padding, bottom = padding;

        // 给第一排的添加顶边距
        if (position < spanCount) {
            top = padding;
        }

        // 给第一列的添加左边距
        if (spanIndex == 0) {
            left = padding;
        }

        outRect.set(left, top, right, bottom);
    }

}
