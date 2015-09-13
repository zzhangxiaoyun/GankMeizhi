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
import android.graphics.Bitmap;
import android.graphics.Point;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.Target;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.squareup.okhttp.OkHttpClient;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import me.xingrz.gankmeizhi.db.Image;
import me.xingrz.gankmeizhi.net.DateUtils;
import me.xingrz.gankmeizhi.net.GankApi;
import me.xingrz.gankmeizhi.net.ImageFetcher;
import retrofit.GsonConverterFactory;
import retrofit.Retrofit;

/**
 * 数据抓取服务
 * <p>
 * 之所以用 {@link IntentService}，是因为可以在它的 {@link #onHandleIntent(Intent)} 里跑同步代码，系统会把
 * 它跑在后台线程中，切保证同一时间只有一个任务在跑。
 *
 * @author XiNGRZ
 */
public class MeizhiFetchingService extends IntentService implements ImageFetcher {

    public static final String ACTION_UPDATE_RESULT = "me.xingrz.gankmeizhi.UPDATE_RESULT";

    public static final String EXTRA_FETCHED = "fetched";
    public static final String EXTRA_TRIGGER = "trigger";

    public static final String ACTION_FETCH_FORWARD = "me.xingrz.gankmeizhi.FETCH_FORWARD";

    public static final String ACTION_FETCH_BACKWARD = "me.xingrz.gankmeizhi.FETCH_BACKWARD";

    private static final String TAG = "MeizhiFetchingService";

    private static final int COUNT_PER_FETCH = 10;

    private final OkHttpClient client = new OkHttpClient();

    private final Gson gson = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .setExclusionStrategies(new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes f) {
                    return f.getDeclaringClass().equals(RealmObject.class);
                }

                @Override
                public boolean shouldSkipClass(Class<?> clazz) {
                    return false;
                }
            })
            .create();

    private final Retrofit retrofit = new Retrofit.Builder()
            .baseUrl("https://gank.avosapps.com/api/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build();

    private final GankApi gankApi = retrofit.create(GankApi.class);

    private LocalBroadcastManager localBroadcastManager;

    public MeizhiFetchingService() {
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Realm realm = Realm.getDefaultInstance();

        RealmResults<Image> latest = Image.all(realm);

        int fetched = 0;

        try {
            if (latest.isEmpty()) {
                Log.d(TAG, "no latest, fresh fetch");
                fetched = fetchLatest(realm);
            } else if (ACTION_FETCH_FORWARD.equals(intent.getAction())) {
                Log.d(TAG, "latest fetch: " + latest.first().getUrl());
                fetched = fetchSince(realm, latest.first().getPublishedAt());
            } else if (ACTION_FETCH_BACKWARD.equals(intent.getAction())) {
                Log.d(TAG, "earliest fetch: " + latest.last().getUrl());
                fetched = fetchBefore(realm, latest.last().getPublishedAt());
            }
        } catch (IOException e) {
            Log.e(TAG, "failed to issue network request", e);
        }

        realm.close();

        Log.d(TAG, "finished fetching, actual fetched " + fetched);

        Intent broadcast = new Intent(ACTION_UPDATE_RESULT);
        broadcast.putExtra(EXTRA_FETCHED, fetched);
        broadcast.putExtra(EXTRA_TRIGGER, intent.getAction());

        localBroadcastManager.sendBroadcast(broadcast);
    }

    private int fetchLatest(Realm realm) throws IOException {
        GankApi.Result<List<Image>> result = gankApi.latest(COUNT_PER_FETCH).execute().body();

        if (result.error) {
            return 0;
        }

        for (int i = 0; i < result.results.size(); i++) {
            if (!saveToDb(realm, result.results.get(i))) {
                return i;
            }
        }

        return result.results.size();
    }

    private int fetchSince(Realm realm, Date sinceDate) throws IOException {
        String[] since = DateUtils.format(sinceDate);

        GankApi.Result<List<String>> dates = gankApi.since(
                COUNT_PER_FETCH, since[0], since[1], since[2]).execute().body();

        if (dates.error) {
            return 0;
        }

        return fetch(realm, dates.results, since);
    }

    private int fetchBefore(Realm realm, Date beforeDate) throws IOException {
        String[] before = DateUtils.format(beforeDate);

        GankApi.Result<List<String>> dates = gankApi.before(
                COUNT_PER_FETCH, before[0], before[1], before[2]).execute().body();

        if (dates.error) {
            return 0;
        }

        return fetch(realm, dates.results, before);
    }

    private int fetch(Realm realm, List<String> dates, String[] boundary) throws IOException {
        Log.d(TAG, "fetching: " + dates.toString());

        int fetched = 0;

        for (String dateString : dates) {
            String[] date = DateUtils.format(dateString);

            if (date == null) {
                return fetched;
            }

            if (Arrays.equals(date, boundary)) {
                continue;
            }

            GankApi.Result<GankApi.Article> article = gankApi.get(
                    date[0], date[1], date[2]).execute().body();

            if (article.error) {
                return fetched;
            }

            if (article.results == null || article.results.images == null) {
                continue;
            }

            for (Image image : article.results.images) {
                if (!saveToDb(realm, image)) {
                    return fetched;
                }

                fetched++;
            }
        }

        return fetched;
    }

    /**
     * 预解码图片并将抓到的数据保存至数据库
     *
     * @param realm Realm 实例
     * @param image 图片
     * @return 是否保存成功
     */
    private boolean saveToDb(Realm realm, Image image) {
        realm.beginTransaction();

        try {
            realm.copyToRealm(Image.persist(image, this));
        } catch (IOException | InterruptedException | ExecutionException e) {
            Log.e(TAG, "Failed to fetch image", e);
            realm.cancelTransaction();
            return false;
        }

        realm.commitTransaction();
        return true;
    }

    @Override
    public void prefetchImage(String url, Point measured)
            throws IOException, InterruptedException, ExecutionException {
        Bitmap bitmap = Glide.with(this)
                .load(url).asBitmap()
                .into(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                .get();

        measured.x = bitmap.getWidth();
        measured.y = bitmap.getHeight();

        Log.d(TAG, "pre-measured image: " + measured.x + " x " + measured.y + " " + url);
    }

}
