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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.AppBarLayout;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.SharedElementCallback;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;

import com.readystatesoftware.systembartint.SystemBarTintManager;
import com.umeng.analytics.MobclickAgent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import butterknife.Bind;
import butterknife.BindColor;
import butterknife.ButterKnife;
import io.realm.Realm;
import io.realm.RealmChangeListener;
import me.xingrz.gankmeizhi.db.Image;
import me.xingrz.gankmeizhi.util.ColorMixer;

public class MainActivity extends AppCompatActivity
        implements SwipeRefreshLayout.OnRefreshListener, RealmChangeListener {

    private static final String TAG = "MainActivity";

    @Bind(R.id.appbar)
    AppBarLayout appbar;

    @Bind(R.id.toolbar)
    Toolbar toolbar;

    @Bind(R.id.refresher)
    SwipeRefreshLayout refresher;

    @Bind(R.id.content)
    RecyclerView content;

    @BindColor(R.color.primary)
    int colorPrimary;

    private Realm realm;

    private MeizhiAdapter adapter;
    private StaggeredGridLayoutManager layoutManager;

    private LocalBroadcastManager localBroadcastManager;

    private UpdateResultReceiver updateResultReceiver = new UpdateResultReceiver();

    private boolean isFetching = false;

    private Bundle reenterState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        MobclickAgent.updateOnlineConfig(this);

        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        setSupportActionBar(toolbar);

        final SystemBarTintManager tintManager = new SystemBarTintManager(this);
        tintManager.setStatusBarTintEnabled(true);
        tintManager.setStatusBarTintColor(colorPrimary);
        tintManager.setStatusBarAlpha(1.0f);

        appbar.addOnOffsetChangedListener((appBarLayout, i) ->
                tintManager.setStatusBarTintColor(ColorMixer.mix(
                        colorPrimary, Color.BLACK, (float) -i / (float) appbar.getHeight()
                )));

        refresher.setColorSchemeResources(R.color.primary);
        refresher.setOnRefreshListener(this);

        realm = Realm.getDefaultInstance();
        realm.addChangeListener(this);

        adapter = new MeizhiAdapter(this) {
            @Override
            protected void onItemClick(View v, int position) {
                startViewerActivity(v, position);
            }
        };

        layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);

        content.setAdapter(adapter);
        content.setLayoutManager(layoutManager);
        content.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    MainActivity.this.onScrolled();
                }
            }
        });

        setExitSharedElementCallback(new SharedElementCallback() {
            @Override
            public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
                if (reenterState != null) {
                    int i = reenterState.getInt("index", 0);
                    Log.d(TAG, "reenter from " + i);

                    sharedElements.clear();
                    sharedElements.put(adapter.get(i).url, layoutManager.findViewByPosition(i));

                    reenterState = null;
                }
            }
        });

        localBroadcastManager = LocalBroadcastManager.getInstance(this);

        if (populate() == 0) {
            fetchForward();
        }
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

        localBroadcastManager.registerReceiver(updateResultReceiver,
                new IntentFilter(MeizhiFetchingService.ACTION_UPDATE_RESULT));
    }

    @Override
    protected void onPause() {
        super.onPause();
        MobclickAgent.onPause(this);
        localBroadcastManager.unregisterReceiver(updateResultReceiver);
    }

    @Override
    public void onRefresh() {
        fetchForward();
        MobclickAgent.onEvent(this, "refresh");
    }

    @Override
    public void onChange() {
        populate();
    }

    private int populate() {
        List<ImageWrapper> wrappers = new ArrayList<>();

        for (Image image : Image.all(realm)) {
            wrappers.add(ImageWrapper.from(image));
        }

        adapter.replaceWith(wrappers);

        return wrappers.size();
    }

    private void onScrolled() {
        int[] positions = new int[layoutManager.getSpanCount()];
        layoutManager.findLastVisibleItemPositions(positions);

        Log.d(TAG, "scrolled to " + Arrays.toString(positions));

        for (int position : positions) {
            if (position == layoutManager.getItemCount() - 1) {
                Log.d(TAG, "hit bottom and trying to fetch more");
                fetchBackward();
                break;
            }
        }
    }

    private void onFetched(int fetched, @NonNull String trigger) {
        Log.d(TAG, "fetched " + fetched + ", triggered by " + trigger);

        refresher.setRefreshing(false);
        isFetching = false;

        if (MeizhiFetchingService.ACTION_FETCH_FORWARD.equals(trigger)) {
            maybeFetchMoreToFill();
        } else if (MeizhiFetchingService.ACTION_FETCH_BACKWARD.equals(trigger)) {
            if (fetched > 0) {
                maybeFetchMoreToFill();
            }
        }
    }

    private void maybeFetchMoreToFill() {
        int itemCount = layoutManager.getItemCount();
        int childCount = layoutManager.getChildCount();

        Log.d(TAG, "item count: " + itemCount + ", child count: " + childCount);

        // 一开始数据少时尝试不停加载旧数据直到填满屏幕
        if (itemCount == childCount || childCount == 0) {
            Log.d(TAG, "trying to fetch backward for more");
            fetchBackward();
        }
    }

    private void fetchForward() {
        if (isFetching) {
            return;
        }

        Intent intent = new Intent(this, MeizhiFetchingService.class);
        intent.setAction(MeizhiFetchingService.ACTION_FETCH_FORWARD);
        startService(intent);

        refresher.post(() -> refresher.setRefreshing(true));

        isFetching = true;
    }

    private void fetchBackward() {
        if (isFetching) {
            return;
        }

        Intent intent = new Intent(this, MeizhiFetchingService.class);
        intent.setAction(MeizhiFetchingService.ACTION_FETCH_BACKWARD);
        startService(intent);

        refresher.post(() -> refresher.setRefreshing(true));

        isFetching = true;
    }

    private void startViewerActivity(View itemView, int position) {
        Intent intent = new Intent(MainActivity.this, ViewerActivity.class);
        intent.putExtra("index", position);

        ActivityOptionsCompat options = ActivityOptionsCompat
                .makeSceneTransitionAnimation(this, itemView, adapter.get(position).url);

        startActivity(intent, options.toBundle());
    }

    @Override
    public void onActivityReenter(int resultCode, Intent data) {
        super.onActivityReenter(resultCode, data);

        supportPostponeEnterTransition();

        reenterState = new Bundle(data.getExtras());

        final int index = reenterState.getInt("index", 0);

        content.smoothScrollToPosition(index);
        content.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                content.getViewTreeObserver().removeOnPreDrawListener(this);
                supportStartPostponedEnterTransition();
                return true;
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.about:
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private class UpdateResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            onFetched(
                    intent.getIntExtra(MeizhiFetchingService.EXTRA_FETCHED, 0),
                    intent.getStringExtra(MeizhiFetchingService.EXTRA_TRIGGER)
            );
        }
    }

}
