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

import android.app.IntentService;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

import io.realm.Realm;
import io.realm.RealmResults;
import me.xingrz.gankmeizhi.db.Article;
import me.xingrz.gankmeizhi.net.ArticleRequestFactory;
import me.xingrz.gankmeizhi.net.Content;
import me.xingrz.gankmeizhi.net.ContentParser;
import me.xingrz.gankmeizhi.net.ImageFetcher;

/**
 * 数据抓取服务
 * <p/>
 * 之所以用 {@link IntentService}，是因为可以在它的 {@link #onHandleIntent(Intent)} 里跑同步代码，系统会把
 * 它跑在后台线程中，切保证同一时间只有一个任务在跑。
 *
 * @author XiNGRZ
 */
public class MeizhiFetchingService extends IntentService implements ImageFetcher {

    public static final String ACTION_UPDATE_RESULT = "me.xingrz.gankmeizhi.UPDATE_RESULT";

    public static final String EXTRA_FETCHED = "fetched";
    public static final String EXTRA_TRIGGER = "trigger";

    public static final String PERMISSION_ACCESS_UPDATE_RESULT = "me.xingrz.gankmeizhi.ACCESS_UPDATE_RESULT";

    public static final String ACTION_FETCH_FORWARD = "me.xingrz.gankmeizhi.FETCH_FORWARD";

    public static final String ACTION_FETCH_BACKWARD = "me.xingrz.gankmeizhi.FETCH_BACKWARD";

    private static final String TAG = "MeizhiFetchingService";

    private final OkHttpClient client = new OkHttpClient();

    public MeizhiFetchingService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Realm realm = Realm.getInstance(this);

        RealmResults<Article> latest = realm.where(Article.class)
                .findAllSorted("date", RealmResults.SORT_ORDER_DESCENDING);

        int fetched = 0;

        if (latest.isEmpty()) {
            Log.d(TAG, "no latest, fresh fetch");
            fetched = freshFetch(realm, "/");
        } else {
            if (ACTION_FETCH_FORWARD.equals(intent.getAction())) {
                Log.d(TAG, "latest fetch: " + latest.first().getTitle());
                fetched = fetchForwardRecursively(realm, "/", latest.first());
            } else if (ACTION_FETCH_BACKWARD.equals(intent.getAction())) {
                fetched = fetchBackward(realm, latest.last().getEarlier(), 10);
            }
        }

        Intent broadcast = new Intent(ACTION_UPDATE_RESULT);
        broadcast.putExtra(EXTRA_FETCHED, fetched);
        broadcast.putExtra(EXTRA_TRIGGER, intent.getAction());

        sendBroadcast(broadcast, PERMISSION_ACCESS_UPDATE_RESULT);
    }

    private int freshFetch(Realm realm, String path) {
        Content content = fetchContent(path);
        if (content == null) {
            return 0;
        }

        realm.beginTransaction();

        try {
            realm.copyToRealm(content.persist(this));
        } catch (IOException e) {
            Log.e(TAG, "Failed to fetch image", e);
            realm.cancelTransaction();
            return 0;
        }

        realm.commitTransaction();
        return 1;
    }

    private int fetchForwardRecursively(Realm realm, String path, Article until) {
        Log.d(TAG, "recursively fetching " + path + " until " + until.getUrl());

        Content content = fetchContent(path);
        if (content == null) {
            return 0;
        }

        if (until.getKey().equals(content.key)) {
            if (!"/".equals(content.url)) {
                realm.beginTransaction();
                content.update(until);
                realm.commitTransaction();
            } else {
                Log.d(TAG, "nothing to update");
            }
            return 0;
        }

        realm.beginTransaction();

        try {
            realm.copyToRealm(content.persist(this));
        } catch (IOException e) {
            Log.e(TAG, "Failed to fetch image", e);
            realm.cancelTransaction();
            return 0;
        }

        realm.commitTransaction();

        if (content.earlier == null) {
            return 1;
        } else {
            return 1 + fetchForwardRecursively(realm, content.earlier, until);
        }
    }

    private int fetchBackward(Realm realm, String since, int maxCount) {
        Log.d(TAG, "fetching since " + since);

        if (TextUtils.isEmpty(since)) {
            Log.d(TAG, "nothing to fetch");
            return 0;
        }

        if (maxCount < 1) {
            return 0;
        }

        Content content = fetchContent(since);
        if (content == null) {
            return 0;
        }

        realm.beginTransaction();

        try {
            realm.copyToRealm(content.persist(this));
        } catch (IOException e) {
            Log.e(TAG, "Failed to fetch image", e);
            realm.cancelTransaction();
            return 0;
        }

        realm.commitTransaction();

        maxCount--;

        if (content.earlier == null) {
            return 1;
        } else {
            return 1 + fetchBackward(realm, content.earlier, maxCount);
        }
    }

    @Nullable
    private Content fetchContent(String path) {
        String html;

        try {
            html = client.newCall(ArticleRequestFactory.make(path)).execute().body().string();
        } catch (IOException e) {
            Log.e(TAG, "Failed to fetch " + path, e);
            return null;
        }

        Content content = ContentParser.parse(html);
        if (content == null) {
            Log.e(TAG, "cannot parse content " + path);
            return null;
        }

        return content;
    }

    @Override
    public void prefetchImage(String url, Point measured) throws IOException {
        Response response = client.newCall(new Request.Builder().url(url).build()).execute();

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        BitmapFactory.decodeStream(response.body().byteStream(), null, options);

        measured.x = options.outWidth;
        measured.y = options.outHeight;
    }

}
