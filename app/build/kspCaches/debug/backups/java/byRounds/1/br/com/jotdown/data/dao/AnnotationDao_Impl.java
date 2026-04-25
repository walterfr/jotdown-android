package br.com.jotdown.data.dao;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.EntityUpsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import br.com.jotdown.data.entity.AnnotationEntity;
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
public final class AnnotationDao_Impl implements AnnotationDao {
  private final RoomDatabase __db;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAnnotation;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllForDocument;

  private final EntityUpsertionAdapter<AnnotationEntity> __upsertionAdapterOfAnnotationEntity;

  public AnnotationDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__preparedStmtOfDeleteAnnotation = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM annotations WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAllForDocument = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM annotations WHERE documentId = ?";
        return _query;
      }
    };
    this.__upsertionAdapterOfAnnotationEntity = new EntityUpsertionAdapter<AnnotationEntity>(new EntityInsertionAdapter<AnnotationEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `annotations` (`id`,`documentId`,`page`,`x`,`y`,`text`,`color`) VALUES (?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AnnotationEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getDocumentId());
        statement.bindLong(3, entity.getPage());
        statement.bindDouble(4, entity.getX());
        statement.bindDouble(5, entity.getY());
        statement.bindString(6, entity.getText());
        statement.bindString(7, entity.getColor());
      }
    }, new EntityDeletionOrUpdateAdapter<AnnotationEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `annotations` SET `id` = ?,`documentId` = ?,`page` = ?,`x` = ?,`y` = ?,`text` = ?,`color` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AnnotationEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getDocumentId());
        statement.bindLong(3, entity.getPage());
        statement.bindDouble(4, entity.getX());
        statement.bindDouble(5, entity.getY());
        statement.bindString(6, entity.getText());
        statement.bindString(7, entity.getColor());
        statement.bindLong(8, entity.getId());
      }
    });
  }

  @Override
  public Object deleteAnnotation(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAnnotation.acquire();
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
          __preparedStmtOfDeleteAnnotation.release(_stmt);
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
  public Object upsertAnnotation(final AnnotationEntity annotation,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfAnnotationEntity.upsert(annotation);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<AnnotationEntity>> getAnnotationsForDocument(final String documentId) {
    final String _sql = "SELECT * FROM annotations WHERE documentId = ? ORDER BY page ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, documentId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"annotations"}, new Callable<List<AnnotationEntity>>() {
      @Override
      @NonNull
      public List<AnnotationEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfDocumentId = CursorUtil.getColumnIndexOrThrow(_cursor, "documentId");
          final int _cursorIndexOfPage = CursorUtil.getColumnIndexOrThrow(_cursor, "page");
          final int _cursorIndexOfX = CursorUtil.getColumnIndexOrThrow(_cursor, "x");
          final int _cursorIndexOfY = CursorUtil.getColumnIndexOrThrow(_cursor, "y");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final List<AnnotationEntity> _result = new ArrayList<AnnotationEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AnnotationEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpDocumentId;
            _tmpDocumentId = _cursor.getString(_cursorIndexOfDocumentId);
            final int _tmpPage;
            _tmpPage = _cursor.getInt(_cursorIndexOfPage);
            final float _tmpX;
            _tmpX = _cursor.getFloat(_cursorIndexOfX);
            final float _tmpY;
            _tmpY = _cursor.getFloat(_cursorIndexOfY);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final String _tmpColor;
            _tmpColor = _cursor.getString(_cursorIndexOfColor);
            _item = new AnnotationEntity(_tmpId,_tmpDocumentId,_tmpPage,_tmpX,_tmpY,_tmpText,_tmpColor);
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
