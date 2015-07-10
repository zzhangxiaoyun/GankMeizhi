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
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmResults;
import me.xingrz.gankmeizhi.db.Article;
import me.xingrz.gankmeizhi.db.Image;

public class MainActivity extends AppCompatActivity
        implements SwipeRefreshLayout.OnRefreshListener, RealmChangeListener {

    private static final String TAG = "MainActivity";

    @Bind(R.id.toolbar)
    Toolbar toolbar;

    @Bind(R.id.refresher)
    SwipeRefreshLayout refresher;

    @Bind(R.id.content)
    RecyclerView content;

    private Realm realm;

    private MeizhiAdapter adapter;
    private StaggeredGridLayoutManager layoutManager;

    private UpdateResultReceiver updateResultReceiver = new UpdateResultReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        setSupportActionBar(toolbar);

        refresher.setColorSchemeResources(R.color.primary);
        refresher.setOnRefreshListener(this);

        realm = Realm.getInstance(this);
        realm.addChangeListener(this);

        adapter = new MeizhiAdapter(this);

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

        onChange();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        realm.removeChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(updateResultReceiver,
                new IntentFilter(MeizhiFetchingService.ACTION_UPDATE_RESULT),
                MeizhiFetchingService.PERMISSION_ACCESS_UPDATE_RESULT, null);

        refresher.setRefreshing(true);
        fetchForward();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(updateResultReceiver);
    }

    @Override
    public void onRefresh() {
        fetchForward();
    }

    @Override
    public void onChange() {
        List<Article> articles = realm.where(Article.class)
                .findAllSorted("date", RealmResults.SORT_ORDER_DESCENDING);

        List<ImageWrapper> images = new ArrayList<>();

        for (Article article : articles) {
            for (Image image : article.getImages()) {
                images.add(ImageWrapper.from(article, image));
            }
        }

        adapter.replaceWith(images);
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

    private void onFetched(int fetched, String trigger) {
        Log.d(TAG, "fetched " + fetched + ", triggered by " + trigger);

        refresher.setRefreshing(false);

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
        Intent intent = new Intent(this, MeizhiFetchingService.class);
        intent.setAction(MeizhiFetchingService.ACTION_FETCH_FORWARD);
        startService(intent);
    }

    private void fetchBackward() {
        Intent intent = new Intent(this, MeizhiFetchingService.class);
        intent.setAction(MeizhiFetchingService.ACTION_FETCH_BACKWARD);
        startService(intent);
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
