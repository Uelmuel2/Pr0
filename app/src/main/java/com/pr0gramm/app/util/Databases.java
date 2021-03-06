package com.pr0gramm.app.util;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.pr0gramm.app.orm.BenisRecord;
import com.pr0gramm.app.orm.Bookmark;
import com.pr0gramm.app.orm.CachedVote;
import com.pr0gramm.app.services.preloading.DatabasePreloadManager;

/**
 */

public class Databases {
    private Databases() {
    }

    public static void withTransaction(SQLiteDatabase db, Runnable inTxRunnable) {
        db.beginTransaction();
        try {
            inTxRunnable.run();
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public static class SqlBriteOpenHelper extends SQLiteOpenHelper {
        public SqlBriteOpenHelper(Context context) {
            super(context, "pr0-sqlbrite", null, 4);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            DatabasePreloadManager.onCreate(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onCreate(db);
        }
    }

    public static class PlainOpenHelper extends SQLiteOpenHelper {
        public PlainOpenHelper(Context context) {
            super(context, "pr0gramm.db", null, 8);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            CachedVote.prepareDatabase(db);
            BenisRecord.prepareDatabase(db);
            Bookmark.prepareDatabase(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onCreate(db);
        }
    }
}
