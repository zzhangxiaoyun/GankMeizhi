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

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.SharedElementCallback;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.umeng.analytics.MobclickAgent;

import java.util.List;
import java.util.Map;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnPageChange;
import io.realm.Realm;
import io.realm.RealmChangeListener;
import me.xingrz.gankmeizhi.db.Image;
import me.xingrz.gankmeizhi.widget.PullBackLayout;

public class ViewerActivity extends AppCompatActivity implements RealmChangeListener, PullBackLayout.Callback {

    private static final String TAG = "ViewerActivity";

    private static final int SYSTEM_UI_BASE_VISIBILITY = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;

    private static final int SYSTEM_UI_IMMERSIVE = View.SYSTEM_UI_FLAG_IMMERSIVE
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN;

    @Bind(R.id.puller)
    PullBackLayout puller;

    @Bind(R.id.pager)
    ViewPager pager;

    private int index;

    private Realm realm;

    private List<Image> images;

    private PagerAdapter adapter;

    private boolean isSystemUiShown = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        supportPostponeEnterTransition();

        setContentView(R.layout.activity_viewer);
        ButterKnife.bind(this);

        index = getIntent().getIntExtra("index", 0);

        realm = Realm.getDefaultInstance();
        realm.addChangeListener(this);

        images = Image.all(realm);

        puller.setCallback(this);

        adapter = new PagerAdapter();

        pager.setAdapter(adapter);
        pager.setCurrentItem(index);

        // 避免图片在进行 Shared Element Transition 时盖过 Toolbar
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setSharedElementsUseOverlay(false);
        }

        setEnterSharedElementCallback(new SharedElementCallback() {
            @Override
            public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
                Image image = images.get(pager.getCurrentItem());
                sharedElements.clear();
                sharedElements.put(image.getPicurl(), adapter.getCurrent().getSharedElement());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        realm.removeChangeListener(this);
        realm.close();
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobclickAgent.onResume(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MobclickAgent.onPause(this);
    }

    @Override
    public void onChange() {
        images = Image.all(realm);
        adapter.notifyDataSetChanged();
    }

    @OnPageChange(value = R.id.pager, callback = OnPageChange.Callback.PAGE_SCROLL_STATE_CHANGED)
    void onPageChange(int state) {
        if (state == ViewPager.SCROLL_STATE_DRAGGING) {
            hideSystemUi();
        }
    }

    public void toggleToolbar() {
        if (isSystemUiShown) {
            hideSystemUi();
        } else {
            showSystemUi();
        }
    }

    private void showSystemUi() {
        pager.setSystemUiVisibility(SYSTEM_UI_BASE_VISIBILITY);
        isSystemUiShown = true;
    }

    private void hideSystemUi() {
        pager.setSystemUiVisibility(SYSTEM_UI_BASE_VISIBILITY | SYSTEM_UI_IMMERSIVE);
        isSystemUiShown = false;
    }

    @Override
    public void supportFinishAfterTransition() {
        Intent data = new Intent();
        data.putExtra("index", pager.getCurrentItem());
        setResult(RESULT_OK, data);

        showSystemUi();

        super.supportFinishAfterTransition();
    }

    @Override
    public void onPull(float progress) {
        getWindow().getDecorView().getBackground().setAlpha(0xff - (int) Math.floor(0xff * progress));
    }

    @Override
    public void onPullDown() {
        supportFinishAfterTransition();
    }

    @Override
    public boolean canPullDown() {
        return !adapter.getCurrent().isZoomed();
    }

    private class PagerAdapter extends FragmentStatePagerAdapter {

        public PagerAdapter() {
            super(getSupportFragmentManager());
        }

        @Override
        public int getCount() {
            return images.size();
        }

        @Override
        public Fragment getItem(int position) {
            return ViewerFragment.newFragment(
                    images.get(position).getPicurl(),
                    position == index);
        }

        public ViewerFragment getCurrent() {
            return (ViewerFragment) adapter.instantiateItem(pager, pager.getCurrentItem());
        }

    }

}
