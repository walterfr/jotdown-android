package br.com.jotdown.data.db;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import br.com.jotdown.data.dao.AnnotationDao;
import br.com.jotdown.data.dao.AnnotationDao_Impl;
import br.com.jotdown.data.dao.DocumentDao;
import br.com.jotdown.data.dao.DocumentDao_Impl;
import br.com.jotdown.data.dao.DrawingDao;
import br.com.jotdown.data.dao.DrawingDao_Impl;
import br.com.jotdown.data.dao.FolderDao;
import br.com.jotdown.data.dao.FolderDao_Impl;
import br.com.jotdown.data.dao.HighlightDao;
import br.com.jotdown.data.dao.HighlightDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class JotdownDatabase_Impl extends JotdownDatabase {
  private volatile DocumentDao _documentDao;

  private volatile AnnotationDao _annotationDao;

  private volatile HighlightDao _highlightDao;

  private volatile DrawingDao _drawingDao;

  private volatile FolderDao _folderDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(2) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `documents` (`id` TEXT NOT NULL, `fileName` TEXT NOT NULL, `title` TEXT NOT NULL, `dateAdded` INTEGER NOT NULL, `folderId` INTEGER, `pdfFilePath` TEXT NOT NULL, `docType` TEXT NOT NULL, `authorLastName` TEXT NOT NULL, `authorFirstName` TEXT NOT NULL, `subtitle` TEXT NOT NULL, `edition` TEXT NOT NULL, `city` TEXT NOT NULL, `publisher` TEXT NOT NULL, `year` TEXT NOT NULL, `journal` TEXT NOT NULL, `volume` TEXT NOT NULL, `pages` TEXT NOT NULL, `url` TEXT NOT NULL, `accessDate` TEXT NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `annotations` (`id` INTEGER NOT NULL, `documentId` TEXT NOT NULL, `page` INTEGER NOT NULL, `x` REAL NOT NULL, `y` REAL NOT NULL, `text` TEXT NOT NULL, `color` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_annotations_documentId` ON `annotations` (`documentId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `highlights` (`id` INTEGER NOT NULL, `documentId` TEXT NOT NULL, `page` INTEGER NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_highlights_documentId` ON `highlights` (`documentId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `drawings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `documentId` TEXT NOT NULL, `page` INTEGER NOT NULL, `pathsJson` TEXT NOT NULL, FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_drawings_documentId` ON `drawings` (`documentId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `folders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '73c9956e284611905f2e16db2db0b168')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `documents`");
        db.execSQL("DROP TABLE IF EXISTS `annotations`");
        db.execSQL("DROP TABLE IF EXISTS `highlights`");
        db.execSQL("DROP TABLE IF EXISTS `drawings`");
        db.execSQL("DROP TABLE IF EXISTS `folders`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        db.execSQL("PRAGMA foreign_keys = ON");
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsDocuments = new HashMap<String, TableInfo.Column>(19);
        _columnsDocuments.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDocuments.put("fileName", new TableInfo.Column("fileName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDocuments.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDocuments.put("dateAdded", new TableInfo.Column("dateAdded", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDocuments.put("folderId", new TableInfo.Column("folderId", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDocuments.put("pdfFilePath", new TableInfo.Column("pdfFilePath", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDocuments.put("docType", new TableInfo.Column("docType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDocuments.put("authorLastName", new TableInfo.Column("authorLastName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDocuments.put("authorFirstName", new TableInfo.Column("authorFirstName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDocuments.put("subtitle", new TableInfo.Column("subtitle", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDocuments.put("edition", new TableInfo.Column("edition", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDocuments.put("city", new TableInfo.Column("city", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDocuments.put("publisher", new TableInfo.Column("publisher", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDocuments.put("year", new TableInfo.Column("year", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDocuments.put("journal", new TableInfo.Column("journal", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDocuments.put("volume", new TableInfo.Column("volume", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDocuments.put("pages", new TableInfo.Column("pages", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDocuments.put("url", new TableInfo.Column("url", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDocuments.put("accessDate", new TableInfo.Column("accessDate", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysDocuments = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesDocuments = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoDocuments = new TableInfo("documents", _columnsDocuments, _foreignKeysDocuments, _indicesDocuments);
        final TableInfo _existingDocuments = TableInfo.read(db, "documents");
        if (!_infoDocuments.equals(_existingDocuments)) {
          return new RoomOpenHelper.ValidationResult(false, "documents(br.com.jotdown.data.entity.DocumentEntity).\n"
                  + " Expected:\n" + _infoDocuments + "\n"
                  + " Found:\n" + _existingDocuments);
        }
        final HashMap<String, TableInfo.Column> _columnsAnnotations = new HashMap<String, TableInfo.Column>(7);
        _columnsAnnotations.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAnnotations.put("documentId", new TableInfo.Column("documentId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAnnotations.put("page", new TableInfo.Column("page", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAnnotations.put("x", new TableInfo.Column("x", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAnnotations.put("y", new TableInfo.Column("y", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAnnotations.put("text", new TableInfo.Column("text", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAnnotations.put("color", new TableInfo.Column("color", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysAnnotations = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysAnnotations.add(new TableInfo.ForeignKey("documents", "CASCADE", "NO ACTION", Arrays.asList("documentId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesAnnotations = new HashSet<TableInfo.Index>(1);
        _indicesAnnotations.add(new TableInfo.Index("index_annotations_documentId", false, Arrays.asList("documentId"), Arrays.asList("ASC")));
        final TableInfo _infoAnnotations = new TableInfo("annotations", _columnsAnnotations, _foreignKeysAnnotations, _indicesAnnotations);
        final TableInfo _existingAnnotations = TableInfo.read(db, "annotations");
        if (!_infoAnnotations.equals(_existingAnnotations)) {
          return new RoomOpenHelper.ValidationResult(false, "annotations(br.com.jotdown.data.entity.AnnotationEntity).\n"
                  + " Expected:\n" + _infoAnnotations + "\n"
                  + " Found:\n" + _existingAnnotations);
        }
        final HashMap<String, TableInfo.Column> _columnsHighlights = new HashMap<String, TableInfo.Column>(4);
        _columnsHighlights.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHighlights.put("documentId", new TableInfo.Column("documentId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHighlights.put("page", new TableInfo.Column("page", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHighlights.put("text", new TableInfo.Column("text", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysHighlights = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysHighlights.add(new TableInfo.ForeignKey("documents", "CASCADE", "NO ACTION", Arrays.asList("documentId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesHighlights = new HashSet<TableInfo.Index>(1);
        _indicesHighlights.add(new TableInfo.Index("index_highlights_documentId", false, Arrays.asList("documentId"), Arrays.asList("ASC")));
        final TableInfo _infoHighlights = new TableInfo("highlights", _columnsHighlights, _foreignKeysHighlights, _indicesHighlights);
        final TableInfo _existingHighlights = TableInfo.read(db, "highlights");
        if (!_infoHighlights.equals(_existingHighlights)) {
          return new RoomOpenHelper.ValidationResult(false, "highlights(br.com.jotdown.data.entity.HighlightEntity).\n"
                  + " Expected:\n" + _infoHighlights + "\n"
                  + " Found:\n" + _existingHighlights);
        }
        final HashMap<String, TableInfo.Column> _columnsDrawings = new HashMap<String, TableInfo.Column>(4);
        _columnsDrawings.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDrawings.put("documentId", new TableInfo.Column("documentId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDrawings.put("page", new TableInfo.Column("page", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDrawings.put("pathsJson", new TableInfo.Column("pathsJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysDrawings = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysDrawings.add(new TableInfo.ForeignKey("documents", "CASCADE", "NO ACTION", Arrays.asList("documentId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesDrawings = new HashSet<TableInfo.Index>(1);
        _indicesDrawings.add(new TableInfo.Index("index_drawings_documentId", false, Arrays.asList("documentId"), Arrays.asList("ASC")));
        final TableInfo _infoDrawings = new TableInfo("drawings", _columnsDrawings, _foreignKeysDrawings, _indicesDrawings);
        final TableInfo _existingDrawings = TableInfo.read(db, "drawings");
        if (!_infoDrawings.equals(_existingDrawings)) {
          return new RoomOpenHelper.ValidationResult(false, "drawings(br.com.jotdown.data.entity.DrawingEntity).\n"
                  + " Expected:\n" + _infoDrawings + "\n"
                  + " Found:\n" + _existingDrawings);
        }
        final HashMap<String, TableInfo.Column> _columnsFolders = new HashMap<String, TableInfo.Column>(2);
        _columnsFolders.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFolders.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysFolders = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesFolders = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoFolders = new TableInfo("folders", _columnsFolders, _foreignKeysFolders, _indicesFolders);
        final TableInfo _existingFolders = TableInfo.read(db, "folders");
        if (!_infoFolders.equals(_existingFolders)) {
          return new RoomOpenHelper.ValidationResult(false, "folders(br.com.jotdown.data.entity.FolderEntity).\n"
                  + " Expected:\n" + _infoFolders + "\n"
                  + " Found:\n" + _existingFolders);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "73c9956e284611905f2e16db2db0b168", "16897240716588de048cfc8c7e5a1fff");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "documents","annotations","highlights","drawings","folders");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    final boolean _supportsDeferForeignKeys = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
    try {
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = FALSE");
      }
      super.beginTransaction();
      if (_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA defer_foreign_keys = TRUE");
      }
      _db.execSQL("DELETE FROM `documents`");
      _db.execSQL("DELETE FROM `annotations`");
      _db.execSQL("DELETE FROM `highlights`");
      _db.execSQL("DELETE FROM `drawings`");
      _db.execSQL("DELETE FROM `folders`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = TRUE");
      }
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(DocumentDao.class, DocumentDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(AnnotationDao.class, AnnotationDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(HighlightDao.class, HighlightDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(DrawingDao.class, DrawingDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(FolderDao.class, FolderDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public DocumentDao documentDao() {
    if (_documentDao != null) {
      return _documentDao;
    } else {
      synchronized(this) {
        if(_documentDao == null) {
          _documentDao = new DocumentDao_Impl(this);
        }
        return _documentDao;
      }
    }
  }

  @Override
  public AnnotationDao annotationDao() {
    if (_annotationDao != null) {
      return _annotationDao;
    } else {
      synchronized(this) {
        if(_annotationDao == null) {
          _annotationDao = new AnnotationDao_Impl(this);
        }
        return _annotationDao;
      }
    }
  }

  @Override
  public HighlightDao highlightDao() {
    if (_highlightDao != null) {
      return _highlightDao;
    } else {
      synchronized(this) {
        if(_highlightDao == null) {
          _highlightDao = new HighlightDao_Impl(this);
        }
        return _highlightDao;
      }
    }
  }

  @Override
  public DrawingDao drawingDao() {
    if (_drawingDao != null) {
      return _drawingDao;
    } else {
      synchronized(this) {
        if(_drawingDao == null) {
          _drawingDao = new DrawingDao_Impl(this);
        }
        return _drawingDao;
      }
    }
  }

  @Override
  public FolderDao folderDao() {
    if (_folderDao != null) {
      return _folderDao;
    } else {
      synchronized(this) {
        if(_folderDao == null) {
          _folderDao = new FolderDao_Impl(this);
        }
        return _folderDao;
      }
    }
  }
}
