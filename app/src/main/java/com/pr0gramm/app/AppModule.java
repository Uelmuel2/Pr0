package com.pr0gramm.app;

import android.app.Application;
import android.app.DownloadManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.PowerManager;

import com.pr0gramm.app.util.BackgroundScheduler;
import com.pr0gramm.app.util.Databases;
import com.pr0gramm.app.util.Holder;
import com.squareup.sqlbrite.BriteDatabase;
import com.squareup.sqlbrite.SqlBrite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import rx.Observable;
import rx.Single;

/**
 */
@Module
public class AppModule {
    private final Application application;

    public AppModule(Application application) {
        this.application = application;
    }

    @Provides
    @Singleton
    public SharedPreferences sharedPreferences() {
        return application.getSharedPreferences("pr0gramm", Context.MODE_PRIVATE);
    }

    @Provides
    @Singleton
    public Observable<BriteDatabase> sqlBrite(Application application) {
        Logger logger = LoggerFactory.getLogger("SqlBrite");

        return Observable.fromCallable(() -> {
            SQLiteOpenHelper openHelper = new Databases.SqlBriteOpenHelper(application);
            return SqlBrite
                    .create(logger::info)
                    .wrapDatabaseHelper(openHelper, BackgroundScheduler.instance());
        }).cache();
    }

    @Provides
    @Singleton
    public Holder<SQLiteDatabase> sqLiteDatabase(Application application) {
        return Holder.ofSingle(Single
                .fromCallable(() -> new Databases.PlainOpenHelper(application).getWritableDatabase())
                .subscribeOn(BackgroundScheduler.instance()));
    }

    @Provides
    @Singleton
    public Application application() {
        return application;
    }

    @Provides
    @Singleton
    public Context context() {
        return application;
    }

    @Provides
    @Singleton
    public NotificationManager notificationManager() {
        return (NotificationManager) application.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Provides
    @Singleton
    public DownloadManager downloadManager() {
        return (DownloadManager) application.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    @Provides
    @Singleton
    public PowerManager powerManager() {
        return (PowerManager) application.getSystemService(Context.POWER_SERVICE);
    }
}
