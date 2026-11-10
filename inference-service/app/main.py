"""Embedding API. Sprint 4A: text and image vectors only. Licence checks arrive in 4B, in front of this service."""
import os
from contextlib import asynccontextmanager

from fastapi import Body, FastAPI, HTTPException, Request
from pydantic import BaseModel, Field

from app.config import ModelLock, load_lock
from app.encoder import Encoder, build_encoder

MAX_IMAGE_BYTES = 10 * 1024 * 1024
MAX_TEXT_CHARS = 512


class TextRequest(BaseModel):
    text: str = Field(min_length=1, max_length=MAX_TEXT_CHARS)


class EmbeddingResponse(BaseModel):
    modelVersion: str
    dimensions: int
    vector: list[float]
    l2Norm: float


def create_app(encoder: Encoder | None = None, lock: ModelLock | None = None) -> FastAPI:
    model_lock = lock or load_lock()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        app.state.encoder = encoder or build_encoder(os.environ.get("KHOJO_ENCODER", "siglip"), model_lock)
        yield

    app = FastAPI(title="Khojo inference service", lifespan=lifespan)

    def respond(vector: list[float]) -> EmbeddingResponse:
        norm = sum(v * v for v in vector) ** 0.5
        return EmbeddingResponse(
            modelVersion=model_lock.model_version, dimensions=len(vector), vector=vector, l2Norm=round(norm, 6),
        )

    @app.get("/healthz")
    def healthz(request: Request):
        return {
            "status": "ok",
            "encoder": request.app.state.encoder.name,
            "modelVersion": model_lock.model_version,
            "dimensions": model_lock.dimensions,
        }

    @app.post("/v1/embeddings/text", response_model=EmbeddingResponse)
    def embed_text(body: TextRequest, request: Request):
        text = body.text.strip()
        if not text:
            raise HTTPException(status_code=422, detail="The text is empty.")
        return respond(request.app.state.encoder.encode_text(text))

    @app.post("/v1/embeddings/image", response_model=EmbeddingResponse)
    def embed_image(request: Request, data: bytes = Body(media_type="application/octet-stream")):
        if not data:
            raise HTTPException(status_code=422, detail="The image is empty.")
        if len(data) > MAX_IMAGE_BYTES:
            raise HTTPException(status_code=413, detail="The image is larger than 10 MB.")
        try:
            return respond(request.app.state.encoder.encode_image(data))
        except ValueError as error:
            raise HTTPException(status_code=415, detail=str(error)) from error

    return app


app = create_app()
