package br.com.jotdown.data.dao;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import br.com.jotdown.data.entity.HighlightEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class HighlightDao_Impl implements HighlightDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<HighlightEntity> __insertionAdapterOfHighlightEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteHighlight;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllForDocument;

  public HighlightDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfHighlightEntity = new EntityInsertionAdapter<HighlightEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `highlights` (`id`,`documentId`,`page`,`text`) VALUES (?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final HighlightEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getDocumentId());
        statement.bindLong(3, entity.getPage());
        statement.bindString(4, entity.getText());
      }
    };
    this.__preparedStmtOfDeleteHighlight = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM highlights WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAllForDocument = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM highlights WHERE documentId = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertHighlight(final HighlightEntity highlight,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfHighlightEntity.insert(highlight);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteHighlight(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteHighlight.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteHighlight.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAllForDocument(final String documentId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllForDocument.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, documentId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAllForDocument.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<HighlightEntity>> getHighlightsForDocument(final String documentId) {
    final String _sql = "SELECT * FROM highlights WHERE documentId = ? ORDER BY page ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, documentId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"highlights"}, new Callable<List<HighlightEntity>>() {
      @Override
      @NonNull
      public List<HighlightEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfDocumentId = CursorUtil.getColumnIndexOrThrow(_cursor, "documentId");
          final int _cursorIndexOfPage = CursorUtil.getColumnIndexOrThrow(_cursor, "page");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final List<HighlightEntity> _result = new ArrayList<HighlightEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final HighlightEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpDocumentId;
            _tmpDocumentId = _cursor.getString(_cursorIndexOfDocumentId);
            final int _tmpPage;
            _tmpPage = _cursor.getInt(_cursorIndexOfPage);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            _item = new HighlightEntity(_tmpId,_tmpDocumentId,_tmpPage,_tmpText);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
