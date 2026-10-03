#!/usr/bin/env python3
"""Execute production migration/query SQL with SQLite when an Android toolchain is unavailable.

This supplements, and does not replace, Room's generated-schema/device migration validation.
Only migrations containing literal SQL are supported; unexpected expressions fail closed.
"""
import json
import re
import sqlite3
import textwrap
import argparse
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DB = ROOT / "app/src/main/java/dev/behradhz/meowzix/data/db"
STRING = re.compile(r'"""([\s\S]*?)"""|"(?:\\.|[^"\\])*"')


def literal_sql(expression):
    tokens = list(STRING.finditer(expression))
    remainder = STRING.sub("", expression)
    assert not re.sub(r"\s|\+|,|\.trimIndent\(\)", "", remainder), expression
    return "".join(textwrap.dedent(t.group(1)).strip() if t.group(1) is not None
                   else json.loads(t.group(0)) for t in tokens)


def migration_sql(path):
    source = path.read_text()
    for match in re.finditer(r"db\.execSQL\(", source):
        start = match.end()
        position = start
        depth = 1
        while depth:
            token = STRING.match(source, position)
            if token:
                position = token.end()
                continue
            depth += (source[position] == "(") - (source[position] == ")")
            position += 1
        yield literal_sql(source[start:position - 1])


def dao_query(name, filename="HistoryDao.kt"):
    source = (DB / filename).read_text()
    match = re.search(r'@Query\(((?:(?!@Query)[\s\S])*?)\)\s*suspend fun ' + re.escape(name) + r'\(', source)
    assert match is not None, name
    return literal_sql(match.group(1))


def validate_scale():
    """Host SQLite scale probe; Android/Room latency and cancellation still need device tests."""
    connection = sqlite3.connect(":memory:")
    connection.execute("PRAGMA foreign_keys=ON")
    schema = json.loads((ROOT / "app/schemas/dev.behradhz.meowzix.data.db.MeowzixDatabase/10.json").read_text())["database"]
    for entity in schema["entities"]:
        connection.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity.get("indices", []):
            connection.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    for name in ["Migration10To11.kt", "Migration11To12.kt", "Migration12To13.kt"]:
        for sql in migration_sql(DB / name):
            connection.execute(sql)
    connection.executemany(
        "INSERT INTO tracks (id,title,normalizedTitle,normalizedArtist,durationMs,favorite,hidden,createdAtEpochMs,updatedAtEpochMs) VALUES (?,?,?,?,200000,0,0,1,1)",
        [(f"track-{i}", f"Track {i}", f"track {i}", f"artist-{i % 1000}") for i in range(10_000)])
    connection.executemany(
        "INSERT INTO track_sources (id,trackId,type,availability,localPath,trainingEligible,createdAtEpochMs,lastVerifiedAtEpochMs) VALUES (?,?,'APP_OFFLINE_COPY','AVAILABLE_LOCAL','/fixture/audio.wav',1,1,1)",
        [(f"source-{i}", f"track-{i}") for i in range(10_000)])
    connection.execute("INSERT INTO listening_sessions VALUES ('scale',1,NULL,'SMART_SHUFFLE')")
    types = ("MANUAL_SELECTED", "PLAY_STARTED", "PLAY_COMPLETED")
    for start in range(0, 100_000, 5_000):
        rows = []
        for i in range(start, min(start + 5_000, 100_000)):
            instance = f"instance-{i // 3}"
            kind = types[i % 3]
            rows.append((f"event-{i}", instance, f"track-{(i // 3) % 10_000}", "scale", kind,
                         1000 + i, 23, 4, "NIGHT", 0, 190000, 200000, 0.95, "USER", "SMART_SHUFFLE",
                         i + 1, instance if kind == "PLAY_COMPLETED" else None))
        connection.executemany(
            "INSERT INTO listening_events (id,playbackInstanceId,trackId,sessionId,type,occurredAtEpochMs,localHour,dayOfWeek,timeBucket,isWeekend,positionMs,durationMs,completionRatio,initiatedBy,playbackMode,eventSequence,outcomeKey) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            rows)
    connection.execute("UPDATE recommendation_event_clock SET lastSequence=100000 WHERE id=1")
    connection.commit()
    began = time.perf_counter()
    eligible = connection.execute(dao_query("eligibleTrackIds", "RecommendationDao.kt"), {"offlineOnly": 1}).fetchall()
    selection = connection.execute(dao_query("trainingOutcomes"), {"after": 0, "floor": 0, "limit": 10, "latestFirst": 0, "through": 100000}).fetchall()
    select_ms = (time.perf_counter() - began) * 1000
    assert len(eligible) == 10_000
    assert [row[1] for row in selection] == list(range(3, 31, 3))
    began = time.perf_counter()
    connection.execute(dao_query("rebuildTrackStats"), {"floor": 0})
    connection.execute(dao_query("rebuildTimeStats"), {"floor": 0})
    rebuild_ms = (time.perf_counter() - began) * 1000
    assert connection.execute("SELECT SUM(totalStarts),SUM(totalCompletions) FROM track_preference_stats").fetchone() == (33333, 33333)
    assert connection.execute("SELECT COUNT(*) FROM track_time_preferences").fetchone() == (10000,)
    assert connection.execute("PRAGMA foreign_key_check").fetchall() == []
    print(f"PASS: host SQLite scale, 10,000 tracks / 1,000 artists / 100,000 events; selection {select_ms:.1f} ms; aggregate rebuild {rebuild_ms:.1f} ms (not Android timings).")


def main():
    connection = sqlite3.connect(":memory:")
    connection.execute("PRAGMA foreign_keys=ON")
    schema = json.loads((ROOT / "app/schemas/dev.behradhz.meowzix.data.db.MeowzixDatabase/10.json").read_text())["database"]
    for entity in schema["entities"]:
        connection.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity.get("indices", []):
            connection.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    connection.execute("INSERT INTO tracks (id,title,normalizedTitle,durationMs,favorite,hidden,createdAtEpochMs,updatedAtEpochMs) VALUES ('kept','Kept','kept',100000,1,0,1,1)")
    connection.execute("INSERT INTO listening_sessions VALUES ('session',1,NULL,'ORDERED')")
    connection.execute("INSERT INTO listening_events VALUES ('event','instance','kept','session','PLAY_COMPLETED',1000,23,4,'NIGHT',0,95000,100000,0.95,'USER','ORDERED')")
    migrations = ["Migration10To11.kt", "Migration11To12.kt", "Migration12To13.kt"]
    statements = 0
    for name in migrations:
        for sql in migration_sql(DB / name):
            connection.execute(sql)
            statements += 1
    assert connection.execute("SELECT favorite FROM tracks WHERE id='kept'").fetchone() == (1,)
    sequence = connection.execute("SELECT eventSequence FROM listening_events WHERE id='event'").fetchone()[0]
    assert sequence > 0
    assert connection.execute("SELECT lastSequence FROM recommendation_event_clock").fetchone() == (sequence,)
    assert len(connection.execute("PRAGMA table_info(training_samples)").fetchall()) == 12
    sample = ("instance", "kept", b"vector", 0.6, 1.0, sequence, 2, 2, 1000, "event", None, None)
    connection.execute("INSERT INTO training_samples VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", sample)
    try:
        connection.execute("INSERT INTO training_samples VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", sample)
        raise AssertionError("Duplicate playback reward accepted")
    except sqlite3.IntegrityError:
        pass
    event_columns = [row[1] for row in connection.execute("PRAGMA table_info(listening_events)")]
    original_event = dict(zip(event_columns, connection.execute("SELECT * FROM listening_events WHERE id='event'").fetchone()))
    def add_event(identifier, kind, event_sequence, outcome):
        values = original_event | {"id": identifier, "type": kind, "eventSequence": event_sequence, "outcomeKey": outcome}
        placeholders = ",".join("?" for _ in event_columns)
        connection.execute(f"INSERT INTO listening_events VALUES ({placeholders})", [values[column] for column in event_columns])
    try:
        add_event("duplicate-outcome", "SKIPPED_EARLY", sequence + 1, "instance")
        raise AssertionError("Second terminal type for the same instance accepted")
    except sqlite3.IntegrityError:
        pass
    add_event("seek-one", "SEEKED", sequence + 2, None)
    add_event("seek-two", "SEEKED", sequence + 3, None)
    assert connection.execute("SELECT COUNT(*) FROM listening_events WHERE type='SEEKED'").fetchone() == (2,)
    connection.execute("UPDATE recommendation_event_clock SET lastSequence=(SELECT MAX(eventSequence) FROM listening_events)")
    candidate_source = (DB / "RecommendationDao.kt").read_text()
    eligibility = literal_sql(re.search(r'@Query\((.*?)\)\s*suspend fun eligibleTrackIds', candidate_source, re.S).group(1))
    connection.execute("INSERT INTO track_sources (id,trackId,type,availability,localPath,trainingEligible,createdAtEpochMs,lastVerifiedAtEpochMs) VALUES ('source','kept','APP_OFFLINE_COPY','AVAILABLE_LOCAL','/local/music.wav',1,1,1)")
    assert connection.execute(eligibility, {"offlineOnly": 1}).fetchall() == [("kept",)]
    connection.execute("UPDATE track_sources SET type='TELEGRAM_REMOTE',availability='REMOTE_ONLY',localPath=NULL")
    assert connection.execute(eligibility, {"offlineOnly": 0}).fetchall() == []
    connection.execute("INSERT INTO telegram_track_sources (trackSourceId,accountId,chatId,messageId,tdFileId) VALUES ('source','account',1,1,123)")
    assert connection.execute(eligibility, {"offlineOnly": 0}).fetchall() == [("kept",)]
    assert connection.execute(eligibility, {"offlineOnly": 1}).fetchall() == []
    connection.execute("UPDATE tracks SET hidden=1 WHERE id='kept'")
    assert connection.execute(eligibility, {"offlineOnly": 0}).fetchall() == []
    connection.execute("UPDATE tracks SET hidden=0 WHERE id='kept'")
    query_count = 0
    for name in ["HistoryDao.kt", "AudioFeatureDao.kt", "RecommendationDao.kt", "TrainingSampleEntity.kt"]:
        source = (DB / name).read_text()
        for match in re.finditer(r"@Query\(", source):
            position = match.end()
            start = position
            depth = 1
            while depth:
                token = STRING.match(source, position)
                if token:
                    position = token.end()
                    continue
                depth += (source[position] == "(") - (source[position] == ")")
                position += 1
            sql = literal_sql(source[start:position - 1])
            bindings = {parameter: 0 for parameter in re.findall(r":(\w+)", sql)}
            connection.execute("EXPLAIN " + sql, bindings).fetchall()
            query_count += 1
    last_sequence = connection.execute("SELECT lastSequence FROM recommendation_event_clock").fetchone()[0]
    connection.execute("DELETE FROM listening_events")
    connection.execute("UPDATE recommendation_event_clock SET lastSequence=lastSequence+1 WHERE id=1")
    assert connection.execute("SELECT lastSequence FROM recommendation_event_clock").fetchone()[0] > last_sequence

    # Exercise the actual bounded selection/rebuild SQL, including a playback crossing a reset.
    def history_event(identifier, instance, kind, event_sequence, bucket="NIGHT", terminal=False):
        values = original_event | {"id": identifier, "playbackInstanceId": instance, "type": kind,
                                   "eventSequence": event_sequence, "timeBucket": bucket,
                                   "outcomeKey": instance if terminal else None}
        placeholders = ",".join("?" for _ in event_columns)
        connection.execute(f"INSERT INTO listening_events VALUES ({placeholders})", [values[column] for column in event_columns])
    history_event("old-choice", "old", "MANUAL_SELECTED", 100, "MORNING")
    history_event("old-start", "old", "PLAY_STARTED", 101, "MORNING")
    history_event("old-completion", "old", "PLAY_COMPLETED", 102, terminal=True)
    history_event("cross-start", "cross-reset", "PLAY_STARTED", 103)
    history_event("cross-completion", "cross-reset", "PLAY_COMPLETED", 106, terminal=True)
    history_event("new-choice", "new", "AUTO_SELECTED", 107)
    history_event("new-start", "new", "PLAY_STARTED", 108)
    history_event("new-completion", "new", "PLAY_COMPLETED", 109, terminal=True)
    history_event("legacy-duplicate", "old", "SKIPPED_EARLY", 110)
    selection = dao_query("trainingOutcomes")
    params = {"after": 0, "floor": 0, "limit": 1, "latestFirst": 0, "through": 109}
    assert connection.execute(selection, params).fetchall() == [("old", 102)]
    assert connection.execute(selection, params | {"latestFirst": 1}).fetchall() == [("new", 109)]
    assert connection.execute(selection, params | {"after": 102}).fetchall() == [("cross-reset", 106)]
    assert connection.execute(selection, params | {"through": 102, "limit": 10}).fetchall() == [("old", 102)]
    assert connection.execute(selection, params | {"after": 104, "floor": 104, "limit": 10}).fetchall() == [("new", 109)]
    bounded_page = connection.execute(dao_query("trainingEventPage"), {"after": 0, "limit": 100, "through": 102}).fetchall()
    assert len(bounded_page) == 3
    assert all(row[0] <= 102 for row in bounded_page)
    def rebuild_stats(floor):
        connection.execute("DELETE FROM track_preference_stats")
        connection.execute("DELETE FROM track_time_preferences")
        connection.execute(dao_query("rebuildTrackStats"), {"floor": floor})
        connection.execute(dao_query("rebuildTimeStats"), {"floor": floor})
    rebuild_stats(0)
    assert connection.execute("SELECT totalStarts,totalCompletions,earlySkips,manualSelections FROM track_preference_stats WHERE trackId='kept'").fetchone() == (3, 3, 0, 1)
    assert connection.execute("SELECT starts,completions FROM track_time_preferences WHERE trackId='kept' AND timeBucket='MORNING'").fetchone() == (1, 1)
    rebuild_stats(104)
    assert connection.execute("SELECT totalStarts,totalCompletions,earlySkips,manualSelections FROM track_preference_stats WHERE trackId='kept'").fetchone() == (1, 1, 0, 0)
    assert connection.execute("SELECT starts,completions FROM track_time_preferences WHERE trackId='kept' AND timeBucket='NIGHT'").fetchone() == (1, 1)
    assert connection.execute("SELECT COUNT(*) FROM track_time_preferences WHERE timeBucket='MORNING'").fetchone() == (0,)
    connection.execute("DELETE FROM tracks WHERE id='kept'")
    assert connection.execute("SELECT COUNT(*) FROM training_samples").fetchone() == (0,)
    assert connection.execute("PRAGMA foreign_key_check").fetchall() == []
    print(f"PASS: {statements} production migration statements; {query_count} production queries; preserved data, terminal UUID deduplication, repeated seeks, offline/source/hidden filters, durable clock, bounded chronological batches, frozen event pages, privacy-safe aggregate rebuild, FK cascade.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scale", action="store_true", help="Also probe production SQL with 100,000 synthetic events")
    arguments = parser.parse_args()
    main()
    if arguments.scale:
        validate_scale()
