package com.eink.hidato.data.db;

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
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class GameSaveDao_Impl implements GameSaveDao {
  private final RoomDatabase __db;

  private final EntityUpsertionAdapter<GameSaveEntity> __upsertionAdapterOfGameSaveEntity;

  public GameSaveDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__upsertionAdapterOfGameSaveEntity = new EntityUpsertionAdapter<GameSaveEntity>(new EntityInsertionAdapter<GameSaveEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `game_saves` (`id`,`rows`,`cols`,`difficulty`,`solutionPath`,`visibleClues`,`revealedHints`,`playerPath`,`currentNumber`,`errorCount`,`hintCount`,`completed`,`updatedAt`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final GameSaveEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getRows());
        statement.bindLong(3, entity.getCols());
        statement.bindString(4, entity.getDifficulty());
        statement.bindString(5, entity.getSolutionPath());
        statement.bindString(6, entity.getVisibleClues());
        statement.bindString(7, entity.getRevealedHints());
        statement.bindString(8, entity.getPlayerPath());
        statement.bindLong(9, entity.getCurrentNumber());
        statement.bindLong(10, entity.getErrorCount());
        statement.bindLong(11, entity.getHintCount());
        final int _tmp = entity.getCompleted() ? 1 : 0;
        statement.bindLong(12, _tmp);
        statement.bindLong(13, entity.getUpdatedAt());
      }
    }, new EntityDeletionOrUpdateAdapter<GameSaveEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `game_saves` SET `id` = ?,`rows` = ?,`cols` = ?,`difficulty` = ?,`solutionPath` = ?,`visibleClues` = ?,`revealedHints` = ?,`playerPath` = ?,`currentNumber` = ?,`errorCount` = ?,`hintCount` = ?,`completed` = ?,`updatedAt` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final GameSaveEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getRows());
        statement.bindLong(3, entity.getCols());
        statement.bindString(4, entity.getDifficulty());
        statement.bindString(5, entity.getSolutionPath());
        statement.bindString(6, entity.getVisibleClues());
        statement.bindString(7, entity.getRevealedHints());
        statement.bindString(8, entity.getPlayerPath());
        statement.bindLong(9, entity.getCurrentNumber());
        statement.bindLong(10, entity.getErrorCount());
        statement.bindLong(11, entity.getHintCount());
        final int _tmp = entity.getCompleted() ? 1 : 0;
        statement.bindLong(12, _tmp);
        statement.bindLong(13, entity.getUpdatedAt());
        statement.bindLong(14, entity.getId());
      }
    });
  }

  @Override
  public Object upsert(final GameSaveEntity entity, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfGameSaveEntity.upsert(entity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<GameSaveEntity> observeCurrent() {
    final String _sql = "SELECT * FROM game_saves WHERE id = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"game_saves"}, new Callable<GameSaveEntity>() {
      @Override
      @Nullable
      public GameSaveEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRows = CursorUtil.getColumnIndexOrThrow(_cursor, "rows");
          final int _cursorIndexOfCols = CursorUtil.getColumnIndexOrThrow(_cursor, "cols");
          final int _cursorIndexOfDifficulty = CursorUtil.getColumnIndexOrThrow(_cursor, "difficulty");
          final int _cursorIndexOfSolutionPath = CursorUtil.getColumnIndexOrThrow(_cursor, "solutionPath");
          final int _cursorIndexOfVisibleClues = CursorUtil.getColumnIndexOrThrow(_cursor, "visibleClues");
          final int _cursorIndexOfRevealedHints = CursorUtil.getColumnIndexOrThrow(_cursor, "revealedHints");
          final int _cursorIndexOfPlayerPath = CursorUtil.getColumnIndexOrThrow(_cursor, "playerPath");
          final int _cursorIndexOfCurrentNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "currentNumber");
          final int _cursorIndexOfErrorCount = CursorUtil.getColumnIndexOrThrow(_cursor, "errorCount");
          final int _cursorIndexOfHintCount = CursorUtil.getColumnIndexOrThrow(_cursor, "hintCount");
          final int _cursorIndexOfCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "completed");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final GameSaveEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final int _tmpRows;
            _tmpRows = _cursor.getInt(_cursorIndexOfRows);
            final int _tmpCols;
            _tmpCols = _cursor.getInt(_cursorIndexOfCols);
            final String _tmpDifficulty;
            _tmpDifficulty = _cursor.getString(_cursorIndexOfDifficulty);
            final String _tmpSolutionPath;
            _tmpSolutionPath = _cursor.getString(_cursorIndexOfSolutionPath);
            final String _tmpVisibleClues;
            _tmpVisibleClues = _cursor.getString(_cursorIndexOfVisibleClues);
            final String _tmpRevealedHints;
            _tmpRevealedHints = _cursor.getString(_cursorIndexOfRevealedHints);
            final String _tmpPlayerPath;
            _tmpPlayerPath = _cursor.getString(_cursorIndexOfPlayerPath);
            final int _tmpCurrentNumber;
            _tmpCurrentNumber = _cursor.getInt(_cursorIndexOfCurrentNumber);
            final int _tmpErrorCount;
            _tmpErrorCount = _cursor.getInt(_cursorIndexOfErrorCount);
            final int _tmpHintCount;
            _tmpHintCount = _cursor.getInt(_cursorIndexOfHintCount);
            final boolean _tmpCompleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfCompleted);
            _tmpCompleted = _tmp != 0;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _result = new GameSaveEntity(_tmpId,_tmpRows,_tmpCols,_tmpDifficulty,_tmpSolutionPath,_tmpVisibleClues,_tmpRevealedHints,_tmpPlayerPath,_tmpCurrentNumber,_tmpErrorCount,_tmpHintCount,_tmpCompleted,_tmpUpdatedAt);
          } else {
            _result = null;
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
  public Object getCurrent(final Continuation<? super GameSaveEntity> $completion) {
    final String _sql = "SELECT * FROM game_saves WHERE id = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<GameSaveEntity>() {
      @Override
      @Nullable
      public GameSaveEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRows = CursorUtil.getColumnIndexOrThrow(_cursor, "rows");
          final int _cursorIndexOfCols = CursorUtil.getColumnIndexOrThrow(_cursor, "cols");
          final int _cursorIndexOfDifficulty = CursorUtil.getColumnIndexOrThrow(_cursor, "difficulty");
          final int _cursorIndexOfSolutionPath = CursorUtil.getColumnIndexOrThrow(_cursor, "solutionPath");
          final int _cursorIndexOfVisibleClues = CursorUtil.getColumnIndexOrThrow(_cursor, "visibleClues");
          final int _cursorIndexOfRevealedHints = CursorUtil.getColumnIndexOrThrow(_cursor, "revealedHints");
          final int _cursorIndexOfPlayerPath = CursorUtil.getColumnIndexOrThrow(_cursor, "playerPath");
          final int _cursorIndexOfCurrentNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "currentNumber");
          final int _cursorIndexOfErrorCount = CursorUtil.getColumnIndexOrThrow(_cursor, "errorCount");
          final int _cursorIndexOfHintCount = CursorUtil.getColumnIndexOrThrow(_cursor, "hintCount");
          final int _cursorIndexOfCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "completed");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final GameSaveEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final int _tmpRows;
            _tmpRows = _cursor.getInt(_cursorIndexOfRows);
            final int _tmpCols;
            _tmpCols = _cursor.getInt(_cursorIndexOfCols);
            final String _tmpDifficulty;
            _tmpDifficulty = _cursor.getString(_cursorIndexOfDifficulty);
            final String _tmpSolutionPath;
            _tmpSolutionPath = _cursor.getString(_cursorIndexOfSolutionPath);
            final String _tmpVisibleClues;
            _tmpVisibleClues = _cursor.getString(_cursorIndexOfVisibleClues);
            final String _tmpRevealedHints;
            _tmpRevealedHints = _cursor.getString(_cursorIndexOfRevealedHints);
            final String _tmpPlayerPath;
            _tmpPlayerPath = _cursor.getString(_cursorIndexOfPlayerPath);
            final int _tmpCurrentNumber;
            _tmpCurrentNumber = _cursor.getInt(_cursorIndexOfCurrentNumber);
            final int _tmpErrorCount;
            _tmpErrorCount = _cursor.getInt(_cursorIndexOfErrorCount);
            final int _tmpHintCount;
            _tmpHintCount = _cursor.getInt(_cursorIndexOfHintCount);
            final boolean _tmpCompleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfCompleted);
            _tmpCompleted = _tmp != 0;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _result = new GameSaveEntity(_tmpId,_tmpRows,_tmpCols,_tmpDifficulty,_tmpSolutionPath,_tmpVisibleClues,_tmpRevealedHints,_tmpPlayerPath,_tmpCurrentNumber,_tmpErrorCount,_tmpHintCount,_tmpCompleted,_tmpUpdatedAt);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
