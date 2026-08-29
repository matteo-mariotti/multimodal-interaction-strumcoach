from typing import List, Optional, Dict, Any, Union
from pydantic import BaseModel, Field


class StrumEvent(BaseModel):
    index: int
    timestamp: float
    value: float
    isDown: bool


class Exercise(BaseModel):
    id: str = ""
    name: str
    difficulty: str
    color: Optional[Any] = None
    authorName: str = "StrumCoach"
    isSong: bool = False
    isPublic: bool = False
    referenceSignal: Optional[List[float]] = None
    referenceStrums: Optional[List[StrumEvent]] = None
    hasReference: bool = False
    strummingPattern: str = ""
    referenceAudioUrl: Optional[str] = None
    referenceDurationMs: Optional[int] = None
    communitySourceId: Optional[str] = None
    userId: Optional[str] = None


class SessionStats(BaseModel):
    id: int  # Timestamp in ms (Long in Kotlin)
    exerciseId: str = ""
    exerciseName: str = ""
    accuracy: int
    grade: str
    isReference: bool = False
    timingData: List[float] = Field(default_factory=list)
    dynamicsFeedback: str = ""
    rawSignal: List[float] = Field(default_factory=list)
    detectedStrums: List[StrumEvent] = Field(default_factory=list)
    referenceSignal: Optional[List[float]] = None
    referenceStrums: Optional[List[StrumEvent]] = None
    referenceAudioUrl: Optional[str] = None
    indexShift: int = 0
    audioUrl: Optional[str] = None
    durationMs: int = 0
    audioEnvelope: List[float] = Field(default_factory=list)
    gyroSignal: List[float] = Field(default_factory=list)
    debugInfo: Dict[str, str] = Field(default_factory=dict)
    userId: Optional[str] = None
