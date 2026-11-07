package org.oolah.s24backup;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public final class BackupIndex extends SQLiteOpenHelper {
    public BackupIndex(Context context) {
        super(context, "uploaded_files.db", null, 1);
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE uploads (path TEXT PRIMARY KEY, bytes INTEGER NOT NULL, mtime INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV) { }

    public boolean isCurrent(String target, long bytes, long mtime) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT bytes,mtime FROM uploads WHERE path=?", new String[]{target})) {
            return c.moveToFirst() && c.getLong(0) == bytes && c.getLong(1) == mtime;
        }
    }

    public void record(String target, long bytes, long mtime) {
        android.content.ContentValues v = new android.content.ContentValues();
        v.put("path", target); v.put("bytes", bytes); v.put("mtime", mtime);
        getWritableDatabase().insertWithOnConflict("uploads", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }
}
