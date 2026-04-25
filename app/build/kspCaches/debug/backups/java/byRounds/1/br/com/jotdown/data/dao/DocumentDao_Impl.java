package br.com.jotdown.data.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import br.com.jotdown.data.entity.DocumentEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
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
public final class DocumentDao_Impl implements DocumentDao {
  private final RoomDatabase __db;

  private final SharedSQLiteStatement __preparedStmtOfSetDocumentFolder;

  private final SharedSQLiteStatement __preparedStmtOfClearFolder;

  private final SharedSQLiteStatement __preparedStmtOfDeleteDocument;

  private final SharedSQLiteStatement __preparedStmtOfRenameDocument;

  private final EntityUpsertionAdapter<DocumentEntity> __upsertionAdapterOfDocumentEntity;

  public DocumentDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__preparedStmtOfSetDocumentFolder = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE documents SET folderId = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearFolder = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE documents SET folderId = NULL WHERE folderId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteDocument = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM documents WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfRenameDocument = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE documents SET title = ? WHERE id = ?";
        return _query;
      }
    };
    this.__upsertionAdapterOfDocumentEntity = new EntityUpsertionAdapter<DocumentEntity>(new EntityInsertionAdapter<DocumentEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `documents` (`id`,`fileName`,`title`,`dateAdded`,`folderId`,`pdfFilePath`,`docType`,`authorLastName`,`authorFirstName`,`subtitle`,`edition`,`city`,`publisher`,`year`,`journal`,`volume`,`pages`,`url`,`accessDate`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DocumentEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getFileName());
        statement.bindString(3, entity.getTitle());
        statement.bindLong(4, entity.getDateAdded());
        if (entity.getFolderId() == null) {
          statement.bindNull(5);
        } else {
          statement.bindLong(5, entity.getFolderId());
        }
        statement.bindString(6, entity.getPdfFilePath());
        statement.bindString(7, entity.getDocType());
        statement.bindString(8, entity.getAuthorLastName());
        statement.bindString(9, entity.getAuthorFirstName());
        statement.bindString(10, entity.getSubtitle());
        statement.bindString(11, entity.getEdition());
        statement.bindString(12, entity.getCity());
        statement.bindString(13, entity.getPublisher());
        statement.bindString(14, entity.getYear());
        statement.bindString(15, entity.getJournal());
        statement.bindString(16, entity.getVolume());
        statement.bindString(17, entity.getPages());
        statement.bindString(18, entity.getUrl());
        statement.bindString(19, entity.getAccessDate());
      }
    }, new EntityDeletionOrUpdateAdapter<DocumentEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `documents` SET `id` = ?,`fileName` = ?,`title` = ?,`dateAdded` = ?,`folderId` = ?,`pdfFilePath` = ?,`docType` = ?,`authorLastName` = ?,`authorFirstName` = ?,`subtitle` = ?,`edition` = ?,`city` = ?,`publisher` = ?,`year` = ?,`journal` = ?,`volume` = ?,`pages` = ?,`url` = ?,`accessDate` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DocumentEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getFileName());
        statement.bindString(3, entity.getTitle());
        statement.bindLong(4, entity.getDateAdded());
        if (entity.getFolderId() == null) {
          statement.bindNull(5);
        } else {
          statement.bindLong(5, entity.getFolderId());
        }
        statement.bindString(6, entity.getPdfFilePath());
        statement.bindString(7, entity.getDocType());
        statement.bindString(8, entity.getAuthorLastName());
        statement.bindString(9, entity.getAuthorFirstName());
        statement.bindString(10, entity.getSubtitle());
        statement.bindString(11, entity.getEdition());
        statement.bindString(12, entity.getCity());
        statement.bindString(13, entity.getPublisher());
        statement.bindString(14, entity.getYear());
        statement.bindString(15, entity.getJournal());
        statement.bindString(16, entity.getVolume());
        statement.bindString(17, entity.getPages());
        statement.bindString(18, entity.getUrl());
        statement.bindString(19, entity.getAccessDate());
        statement.bindString(20, entity.getId());
      }
    });
  }

  @Override
  public Object setDocumentFolder(final String docId, final Long folderId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetDocumentFolder.acquire();
        int _argIndex = 1;
        if (folderId == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindLong(_argIndex, folderId);
        }
        _argIndex = 2;
        _stmt.bindString(_argIndex, docId);
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
          __preparedStmtOfSetDocumentFolder.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clearFolder(final long folderId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearFolder.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, folderId);
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
          __preparedStmtOfClearFolder.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteDocument(final String id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteDocument.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, id);
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
          __preparedStmtOfDeleteDocument.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object renameDocument(final String id, final String newTitle,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfRenameDocument.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, newTitle);
        _argIndex = 2;
        _stmt.bindString(_argIndex, id);
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
          __preparedStmtOfRenameDocument.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertDocument(final DocumentEntity document,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfDocumentEntity.upsert(document);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<DocumentEntity>> getAllDocuments() {
    final String _sql = "SELECT * FROM documents ORDER BY dateAdded DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"documents"}, new Callable<List<DocumentEntity>>() {
      @Override
      @NonNull
      public List<DocumentEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDateAdded = CursorUtil.getColumnIndexOrThrow(_cursor, "dateAdded");
          final int _cursorIndexOfFolderId = CursorUtil.getColumnIndexOrThrow(_cursor, "folderId");
          final int _cursorIndexOfPdfFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "pdfFilePath");
          final int _cursorIndexOfDocType = CursorUtil.getColumnIndexOrThrow(_cursor, "docType");
          final int _cursorIndexOfAuthorLastName = CursorUtil.getColumnIndexOrThrow(_cursor, "authorLastName");
          final int _cursorIndexOfAuthorFirstName = CursorUtil.getColumnIndexOrThrow(_cursor, "authorFirstName");
          final int _cursorIndexOfSubtitle = CursorUtil.getColumnIndexOrThrow(_cursor, "subtitle");
          final int _cursorIndexOfEdition = CursorUtil.getColumnIndexOrThrow(_cursor, "edition");
          final int _cursorIndexOfCity = CursorUtil.getColumnIndexOrThrow(_cursor, "city");
          final int _cursorIndexOfPublisher = CursorUtil.getColumnIndexOrThrow(_cursor, "publisher");
          final int _cursorIndexOfYear = CursorUtil.getColumnIndexOrThrow(_cursor, "year");
          final int _cursorIndexOfJournal = CursorUtil.getColumnIndexOrThrow(_cursor, "journal");
          final int _cursorIndexOfVolume = CursorUtil.getColumnIndexOrThrow(_cursor, "volume");
          final int _cursorIndexOfPages = CursorUtil.getColumnIndexOrThrow(_cursor, "pages");
          final int _cursorIndexOfUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "url");
          final int _cursorIndexOfAccessDate = CursorUtil.getColumnIndexOrThrow(_cursor, "accessDate");
          final List<DocumentEntity> _result = new ArrayList<DocumentEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DocumentEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final long _tmpDateAdded;
            _tmpDateAdded = _cursor.getLong(_cursorIndexOfDateAdded);
            final Long _tmpFolderId;
            if (_cursor.isNull(_cursorIndexOfFolderId)) {
              _tmpFolderId = null;
            } else {
              _tmpFolderId = _cursor.getLong(_cursorIndexOfFolderId);
            }
            final String _tmpPdfFilePath;
            _tmpPdfFilePath = _cursor.getString(_cursorIndexOfPdfFilePath);
            final String _tmpDocType;
            _tmpDocType = _cursor.getString(_cursorIndexOfDocType);
            final String _tmpAuthorLastName;
            _tmpAuthorLastName = _cursor.getString(_cursorIndexOfAuthorLastName);
            final String _tmpAuthorFirstName;
            _tmpAuthorFirstName = _cursor.getString(_cursorIndexOfAuthorFirstName);
            final String _tmpSubtitle;
            _tmpSubtitle = _cursor.getString(_cursorIndexOfSubtitle);
            final String _tmpEdition;
            _tmpEdition = _cursor.getString(_cursorIndexOfEdition);
            final String _tmpCity;
            _tmpCity = _cursor.getString(_cursorIndexOfCity);
            final String _tmpPublisher;
            _tmpPublisher = _cursor.getString(_cursorIndexOfPublisher);
            final String _tmpYear;
            _tmpYear = _cursor.getString(_cursorIndexOfYear);
            final String _tmpJournal;
            _tmpJournal = _cursor.getString(_cursorIndexOfJournal);
            final String _tmpVolume;
            _tmpVolume = _cursor.getString(_cursorIndexOfVolume);
            final String _tmpPages;
            _tmpPages = _cursor.getString(_cursorIndexOfPages);
            final String _tmpUrl;
            _tmpUrl = _cursor.getString(_cursorIndexOfUrl);
            final String _tmpAccessDate;
            _tmpAccessDate = _cursor.getString(_cursorIndexOfAccessDate);
            _item = new DocumentEntity(_tmpId,_tmpFileName,_tmpTitle,_tmpDateAdded,_tmpFolderId,_tmpPdfFilePath,_tmpDocType,_tmpAuthorLastName,_tmpAuthorFirstName,_tmpSubtitle,_tmpEdition,_tmpCity,_tmpPublisher,_tmpYear,_tmpJournal,_tmpVolume,_tmpPages,_tmpUrl,_tmpAccessDate);
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

  @Override
  public Object getDocumentById(final String id,
      final Continuation<? super DocumentEntity> $completion) {
    final String _sql = "SELECT * FROM documents WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<DocumentEntity>() {
      @Override
      @Nullable
      public DocumentEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDateAdded = CursorUtil.getColumnIndexOrThrow(_cursor, "dateAdded");
          final int _cursorIndexOfFolderId = CursorUtil.getColumnIndexOrThrow(_cursor, "folderId");
          final int _cursorIndexOfPdfFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "pdfFilePath");
          final int _cursorIndexOfDocType = CursorUtil.getColumnIndexOrThrow(_cursor, "docType");
          final int _cursorIndexOfAuthorLastName = CursorUtil.getColumnIndexOrThrow(_cursor, "authorLastName");
          final int _cursorIndexOfAuthorFirstName = CursorUtil.getColumnIndexOrThrow(_cursor, "authorFirstName");
          final int _cursorIndexOfSubtitle = CursorUtil.getColumnIndexOrThrow(_cursor, "subtitle");
          final int _cursorIndexOfEdition = CursorUtil.getColumnIndexOrThrow(_cursor, "edition");
          final int _cursorIndexOfCity = CursorUtil.getColumnIndexOrThrow(_cursor, "city");
          final int _cursorIndexOfPublisher = CursorUtil.getColumnIndexOrThrow(_cursor, "publisher");
          final int _cursorIndexOfYear = CursorUtil.getColumnIndexOrThrow(_cursor, "year");
          final int _cursorIndexOfJournal = CursorUtil.getColumnIndexOrThrow(_cursor, "journal");
          final int _cursorIndexOfVolume = CursorUtil.getColumnIndexOrThrow(_cursor, "volume");
          final int _cursorIndexOfPages = CursorUtil.getColumnIndexOrThrow(_cursor, "pages");
          final int _cursorIndexOfUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "url");
          final int _cursorIndexOfAccessDate = CursorUtil.getColumnIndexOrThrow(_cursor, "accessDate");
          final DocumentEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final long _tmpDateAdded;
            _tmpDateAdded = _cursor.getLong(_cursorIndexOfDateAdded);
            final Long _tmpFolderId;
            if (_cursor.isNull(_cursorIndexOfFolderId)) {
              _tmpFolderId = null;
            } else {
              _tmpFolderId = _cursor.getLong(_cursorIndexOfFolderId);
            }
            final String _tmpPdfFilePath;
            _tmpPdfFilePath = _cursor.getString(_cursorIndexOfPdfFilePath);
            final String _tmpDocType;
            _tmpDocType = _cursor.getString(_cursorIndexOfDocType);
            final String _tmpAuthorLastName;
            _tmpAuthorLastName = _cursor.getString(_cursorIndexOfAuthorLastName);
            final String _tmpAuthorFirstName;
            _tmpAuthorFirstName = _cursor.getString(_cursorIndexOfAuthorFirstName);
            final String _tmpSubtitle;
            _tmpSubtitle = _cursor.getString(_cursorIndexOfSubtitle);
            final String _tmpEdition;
            _tmpEdition = _cursor.getString(_cursorIndexOfEdition);
            final String _tmpCity;
            _tmpCity = _cursor.getString(_cursorIndexOfCity);
            final String _tmpPublisher;
            _tmpPublisher = _cursor.getString(_cursorIndexOfPublisher);
            final String _tmpYear;
            _tmpYear = _cursor.getString(_cursorIndexOfYear);
            final String _tmpJournal;
            _tmpJournal = _cursor.getString(_cursorIndexOfJournal);
            final String _tmpVolume;
            _tmpVolume = _cursor.getString(_cursorIndexOfVolume);
            final String _tmpPages;
            _tmpPages = _cursor.getString(_cursorIndexOfPages);
            final String _tmpUrl;
            _tmpUrl = _cursor.getString(_cursorIndexOfUrl);
            final String _tmpAccessDate;
            _tmpAccessDate = _cursor.getString(_cursorIndexOfAccessDate);
            _result = new DocumentEntity(_tmpId,_tmpFileName,_tmpTitle,_tmpDateAdded,_tmpFolderId,_tmpPdfFilePath,_tmpDocType,_tmpAuthorLastName,_tmpAuthorFirstName,_tmpSubtitle,_tmpEdition,_tmpCity,_tmpPublisher,_tmpYear,_tmpJournal,_tmpVolume,_tmpPages,_tmpUrl,_tmpAccessDate);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<DocumentSummary>> getDocumentSummariesByFolder(final Long folderId) {
    final String _sql = "SELECT id, fileName, title, dateAdded, docType, authorLastName, authorFirstName, (SELECT COUNT(*) FROM highlights h WHERE h.documentId = d.id) AS highlightCount, (SELECT COUNT(*) FROM annotations a WHERE a.documentId = d.id AND a.text != '') AS annotationCount FROM documents d WHERE (? IS NULL AND d.folderId IS NULL) OR (d.folderId = ?) ORDER BY d.dateAdded DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    if (folderId == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, folderId);
    }
    _argIndex = 2;
    if (folderId == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, folderId);
    }
    return CoroutinesRoom.createFlow(__db, false, new String[] {"highlights", "annotations",
        "documents"}, new Callable<List<DocumentSummary>>() {
      @Override
      @NonNull
      public List<DocumentSummary> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = 0;
          final int _cursorIndexOfFileName = 1;
          final int _cursorIndexOfTitle = 2;
          final int _cursorIndexOfDateAdded = 3;
          final int _cursorIndexOfDocType = 4;
          final int _cursorIndexOfAuthorLastName = 5;
          final int _cursorIndexOfAuthorFirstName = 6;
          final int _cursorIndexOfHighlightCount = 7;
          final int _cursorIndexOfAnnotationCount = 8;
          final List<DocumentSummary> _result = new ArrayList<DocumentSummary>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DocumentSummary _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final long _tmpDateAdded;
            _tmpDateAdded = _cursor.getLong(_cursorIndexOfDateAdded);
            final String _tmpDocType;
            _tmpDocType = _cursor.getString(_cursorIndexOfDocType);
            final String _tmpAuthorLastName;
            _tmpAuthorLastName = _cursor.getString(_cursorIndexOfAuthorLastName);
            final String _tmpAuthorFirstName;
            _tmpAuthorFirstName = _cursor.getString(_cursorIndexOfAuthorFirstName);
            final int _tmpHighlightCount;
            _tmpHighlightCount = _cursor.getInt(_cursorIndexOfHighlightCount);
            final int _tmpAnnotationCount;
            _tmpAnnotationCount = _cursor.getInt(_cursorIndexOfAnnotationCount);
            _item = new DocumentSummary(_tmpId,_tmpFileName,_tmpTitle,_tmpDateAdded,_tmpDocType,_tmpAuthorLastName,_tmpAuthorFirstName,_tmpHighlightCount,_tmpAnnotationCount);
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
