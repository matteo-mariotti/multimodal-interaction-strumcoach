import os
import json
import sqlite3
from typing import List, Optional
from models import Exercise, SessionStats, StrumEvent

APP_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(APP_DIR, "data")
DB_PATH = os.path.join(DATA_DIR, "strumcoach.db")

os.makedirs(DATA_DIR, exist_ok=True)


def get_connection():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    with get_connection() as conn:
        cursor = conn.cursor()
        
        # Exercises Table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS exercises (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                difficulty TEXT NOT NULL,
                color TEXT,
                author_name TEXT NOT NULL DEFAULT 'StrumCoach',
                is_song BOOLEAN NOT NULL DEFAULT 0,
                is_public BOOLEAN NOT NULL DEFAULT 0,
                reference_signal TEXT,
                reference_strums TEXT,
                has_reference BOOLEAN NOT NULL DEFAULT 0,
                strumming_pattern TEXT NOT NULL DEFAULT '',
                reference_audio_url TEXT,
                reference_duration_ms INTEGER,
                community_source_id TEXT,
                user_id TEXT
            )
        """)
        
        # Community Table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS community (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                difficulty TEXT NOT NULL,
                color TEXT,
                author_name TEXT NOT NULL DEFAULT 'Anonimo',
                is_song BOOLEAN NOT NULL DEFAULT 0,
                is_public BOOLEAN NOT NULL DEFAULT 1,
                reference_signal TEXT,
                reference_strums TEXT,
                has_reference BOOLEAN NOT NULL DEFAULT 0,
                strumming_pattern TEXT NOT NULL DEFAULT '',
                reference_audio_url TEXT,
                reference_duration_ms INTEGER,
                community_source_id TEXT,
                user_id TEXT
            )
        """)

        # Sessions Table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS sessions (
                id INTEGER PRIMARY KEY,
                exercise_id TEXT NOT NULL DEFAULT '',
                exercise_name TEXT NOT NULL DEFAULT '',
                accuracy INTEGER NOT NULL,
                grade TEXT NOT NULL,
                is_reference BOOLEAN NOT NULL DEFAULT 0,
                timing_data TEXT NOT NULL,
                dynamics_feedback TEXT NOT NULL DEFAULT '',
                raw_signal TEXT,
                detected_strums TEXT,
                reference_signal TEXT,
                reference_strums TEXT,
                reference_audio_url TEXT,
                index_shift INTEGER NOT NULL DEFAULT 0,
                audio_url TEXT,
                duration_ms INTEGER DEFAULT 0,
                audio_envelope TEXT,
                gyro_signal TEXT,
                debug_info TEXT,
                user_id TEXT
            )
        """)
        
        conn.commit()
        # Migrate existing databases by adding user_id columns if missing
        for table in ("exercises", "sessions", "community"):
            try:
                cursor.execute(f"ALTER TABLE {table} ADD COLUMN user_id TEXT")
                conn.commit()
            except Exception:
                pass  # Column already exists


def ottieni_esercizio(row: sqlite3.Row) -> Exercise:
    d = dict(row)
    return Exercise(
        id=d["id"],
        name=d["name"],
        difficulty=d["difficulty"],
        color=json.loads(d["color"]) if d.get("color") else None,
        authorName=d.get("author_name") or "StrumCoach",
        isSong=bool(d.get("is_song")),
        isPublic=bool(d.get("is_public")),
        referenceSignal=json.loads(d["reference_signal"]) if d.get("reference_signal") else None,
        referenceStrums=[StrumEvent(**s) for s in json.loads(d["reference_strums"])] if d.get("reference_strums") else None,
        hasReference=bool(d.get("has_reference")),
        strummingPattern=d.get("strumming_pattern") or "",
        referenceAudioUrl=d.get("reference_audio_url"),
        referenceDurationMs=d.get("reference_duration_ms"),
        communitySourceId=d.get("community_source_id"),
        userId=d.get("user_id")
    )


def ottieni_sessione(row: sqlite3.Row) -> SessionStats:
    d = dict(row)
    return SessionStats(
        id=d["id"],
        exerciseId=d.get("exercise_id") or "",
        exerciseName=d.get("exercise_name") or "",
        accuracy=d["accuracy"],
        grade=d["grade"],
        isReference=bool(d.get("is_reference")),
        timingData=json.loads(d["timing_data"]) if d.get("timing_data") else [],
        dynamicsFeedback=d.get("dynamics_feedback") or "",
        rawSignal=json.loads(d["raw_signal"]) if d.get("raw_signal") else [],
        detectedStrums=[StrumEvent(**s) for s in json.loads(d["detected_strums"])] if d.get("detected_strums") else [],
        referenceSignal=json.loads(d["reference_signal"]) if d.get("reference_signal") else None,
        referenceStrums=[StrumEvent(**s) for s in json.loads(d["reference_strums"])] if d.get("reference_strums") else None,
        referenceAudioUrl=d.get("reference_audio_url"),
        indexShift=d.get("index_shift") or 0,
        audioUrl=d.get("audio_url"),
        durationMs=d.get("duration_ms") or 0,
        audioEnvelope=json.loads(d["audio_envelope"]) if d.get("audio_envelope") else [],
        gyroSignal=json.loads(d["gyro_signal"]) if d.get("gyro_signal") else [],
        debugInfo=json.loads(d["debug_info"]) if d.get("debug_info") else {},
        userId=d.get("user_id")
    )



def get_all_sessions(user_id: Optional[str] = None) -> List[SessionStats]:
    with get_connection() as conn:
        cursor = conn.cursor()
        if user_id:
            cursor.execute("SELECT * FROM sessions WHERE user_id = ? ORDER BY id DESC", (user_id,))
        else:
            cursor.execute("SELECT * FROM sessions ORDER BY id DESC")
        rows = cursor.fetchall()
        return [ottieni_sessione(r) for r in rows]



def all_exercises(user_id: Optional[str] = None) -> List[Exercise]:
    with get_connection() as conn:
        cursor = conn.cursor()
        if user_id:
            cursor.execute("SELECT * FROM exercises WHERE user_id = ?", (user_id,))
        rows = cursor.fetchall()
        return [ottieni_esercizio(r) for r in rows]


def save_exercise(ex: Exercise) -> Exercise:
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("""
            INSERT INTO exercises (
                id, name, difficulty, color, author_name, is_song, is_public,
                reference_signal, reference_strums, has_reference, strumming_pattern,
                reference_audio_url, reference_duration_ms, community_source_id, user_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name=excluded.name,
                difficulty=excluded.difficulty,
                color=excluded.color,
                author_name=excluded.author_name,
                is_song=excluded.is_song,
                is_public=excluded.is_public,
                reference_signal=excluded.reference_signal,
                reference_strums=excluded.reference_strums,
                has_reference=excluded.has_reference,
                strumming_pattern=excluded.strumming_pattern,
                reference_audio_url=excluded.reference_audio_url,
                reference_duration_ms=excluded.reference_duration_ms,
                community_source_id=excluded.community_source_id,
                user_id=excluded.user_id
        """, (
            ex.id,
            ex.name,
            ex.difficulty,
            json.dumps(ex.color) if ex.color is not None else None,
            ex.authorName,
            ex.isSong,
            ex.isPublic,
            json.dumps(ex.referenceSignal) if ex.referenceSignal is not None else None,
            json.dumps([s.model_dump() for s in ex.referenceStrums]) if ex.referenceStrums is not None else None,
            ex.hasReference,
            ex.strummingPattern,
            ex.referenceAudioUrl,
            ex.referenceDurationMs,
            ex.communitySourceId,
            ex.userId
        ))
        conn.commit()
    return ex

def get_all_community() -> List[Exercise]:
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM community")
        rows = cursor.fetchall()
        return [ottieni_esercizio(r) for r in rows]


def delete_exercise(ex_id: str) -> bool:
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("DELETE FROM exercises WHERE id = ?", (ex_id,))
        conn.commit()
        return cursor.rowcount > 0


def save_community(ex: Exercise) -> Exercise:
    ex.isPublic = True
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("""
            INSERT INTO community (
                id, name, difficulty, color, author_name, is_song, is_public,
                reference_signal, reference_strums, has_reference, strumming_pattern,
                reference_audio_url, reference_duration_ms, community_source_id, user_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name=excluded.name,
                difficulty=excluded.difficulty,
                color=excluded.color,
                author_name=excluded.author_name,
                is_song=excluded.is_song,
                is_public=excluded.is_public,
                reference_signal=excluded.reference_signal,
                reference_strums=excluded.reference_strums,
                has_reference=excluded.has_reference,
                strumming_pattern=excluded.strumming_pattern,
                reference_audio_url=excluded.reference_audio_url,
                reference_duration_ms=excluded.reference_duration_ms,
                community_source_id=excluded.community_source_id,
                user_id=excluded.user_id
        """, (
            ex.id,
            ex.name,
            ex.difficulty,
            json.dumps(ex.color) if ex.color is not None else None,
            ex.authorName,
            ex.isSong,
            ex.isPublic,
            json.dumps(ex.referenceSignal) if ex.referenceSignal is not None else None,
            json.dumps([s.model_dump() for s in ex.referenceStrums]) if ex.referenceStrums is not None else None,
            ex.hasReference,
            ex.strummingPattern,
            ex.referenceAudioUrl,
            ex.referenceDurationMs,
            ex.communitySourceId,
            ex.userId
        ))
        conn.commit()
    return ex


def delete_community(ex_id: str) -> bool:
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("DELETE FROM community WHERE id = ?", (ex_id,))
        count = cursor.rowcount
        # Resetta lo stato degli esercizi nella lista personale degli utenti che avevano pubblicato l'esercizio nella community
        cursor.execute("UPDATE exercises SET is_public = 0 WHERE id = ? OR community_source_id = ?", (ex_id, ex_id))
        conn.commit()
        return count > 0



def save_session(stats: SessionStats) -> SessionStats:
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("""
            INSERT INTO sessions (
                id, exercise_id, exercise_name, accuracy, grade, is_reference, timing_data,
                dynamics_feedback, raw_signal, detected_strums, reference_signal,
                reference_strums, reference_audio_url, index_shift, audio_url,
                duration_ms, audio_envelope, gyro_signal, debug_info, user_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                exercise_id=excluded.exercise_id,
                exercise_name=excluded.exercise_name,
                accuracy=excluded.accuracy,
                grade=excluded.grade,
                is_reference=excluded.is_reference,
                timing_data=excluded.timing_data,
                dynamics_feedback=excluded.dynamics_feedback,
                raw_signal=excluded.raw_signal,
                detected_strums=excluded.detected_strums,
                reference_signal=excluded.reference_signal,
                reference_strums=excluded.reference_strums,
                reference_audio_url=excluded.reference_audio_url,
                index_shift=excluded.index_shift,
                audio_url=excluded.audio_url,
                duration_ms=excluded.duration_ms,
                audio_envelope=excluded.audio_envelope,
                gyro_signal=excluded.gyro_signal,
                debug_info=excluded.debug_info,
                user_id=excluded.user_id
        """, (
            stats.id,
            stats.exerciseId,
            stats.exerciseName,
            stats.accuracy,
            stats.grade,
            stats.isReference,
            json.dumps(stats.timingData),
            stats.dynamicsFeedback,
            json.dumps(stats.rawSignal),
            json.dumps([s.model_dump() for s in stats.detectedStrums]),
            json.dumps(stats.referenceSignal) if stats.referenceSignal is not None else None,
            json.dumps([s.model_dump() for s in stats.referenceStrums]) if stats.referenceStrums is not None else None,
            stats.referenceAudioUrl,
            stats.indexShift,
            stats.audioUrl,
            stats.durationMs,
            json.dumps(stats.audioEnvelope),
            json.dumps(stats.gyroSignal),
            json.dumps(stats.debugInfo),
            stats.userId
        ))
        conn.commit()
    return stats


# Initialize tables on import
init_db()
