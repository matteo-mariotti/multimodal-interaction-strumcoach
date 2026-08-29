import os
import uuid
import shutil
from typing import Optional
import uvicorn
from fastapi import FastAPI, File, UploadFile, Form, HTTPException, status, Query
from fastapi.staticfiles import StaticFiles
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

import database as db
from models import Exercise, SessionStats

APP_DIR = os.path.dirname(os.path.abspath(__file__))
UPLOADS_DIR = os.path.join(APP_DIR, "uploads")
os.makedirs(UPLOADS_DIR, exist_ok=True)

app = FastAPI()

app.mount("/uploads", StaticFiles(directory=UPLOADS_DIR), name="uploads")


@app.get("/exercises")
def get_exercises(userId: Optional[str] = Query(default=None)):
    return db.all_exercises(user_id=userId)


@app.post("/exercises")
def save_exercise(exercise: Exercise):
    if not exercise.id:
        exercise.id = f"custom_{uuid.uuid4().hex[:8]}"
    return db.save_exercise(exercise)


@app.delete("/exercises/{id}")
def delete_exercise(id: str):
    success = db.delete_exercise(id)
    if not success:
        raise HTTPException(status_code=404, detail="Exercise not found")
    return {"status": "success", "message": f"Exercise {id} deleted"}


@app.get("/community")
def get_community_exercises():
    return db.get_all_community()


@app.post("/community")
def publish_to_community(exercise: Exercise):
    if not exercise.id:
        exercise.id = f"comm_{uuid.uuid4().hex[:8]}"
    return db.save_community(exercise)


@app.delete("/community/{id}")
def delete_from_community(id: str):
    success = db.delete_community(id)
    if not success:
        raise HTTPException(status_code=404, detail="Community exercise not found")
    return {"status": "success", "message": f"Community exercise {id} deleted"}


@app.get("/sessions")
def get_sessions(userId: Optional[str] = Query(default=None)):
    return db.get_all_sessions(user_id=userId)


@app.post("/sessions")
def save_session(stats: SessionStats):
    return db.save_session(stats)

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request, exc):
    print("Validation error occurred:")
    print(exc.errors())
    return JSONResponse(
        status_code=status.HTTP_400_BAD_REQUEST,
        content={"detail": exc.errors()},
    )

@app.post("/audio/upload")
async def upload_audio(
    audio: UploadFile = File(...),
    exerciseId: str = Form(...)
):
    try:
        ex_id = exerciseId
        ext = os.path.splitext(audio.filename or "recording.m4a")[1] or ".m4a"
        unique_filename = f"audio_{ex_id}_{uuid.uuid4().hex[:8]}{ext}"
        file_path = os.path.join(UPLOADS_DIR, unique_filename)
        
        with open(file_path, "wb") as buffer:
            shutil.copyfileobj(audio.file, buffer)
            
        file_url = f"/uploads/{unique_filename}"
        return {"url": file_url}
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to upload audio file: {str(e)}"
        )


if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
