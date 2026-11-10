# CPU image for the embedding API. The model is downloaded at BUILD time from the exact revision in models.lock,
# so a running container never needs the internet and always serves the pinned checkpoint.
FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    HF_HOME=/opt/hf \
    KHOJO_ENCODER=siglip

WORKDIR /srv

# AWS Lambda Web Adapter: lets this same image run on Lambda (ADR-004 option 1) or on a plain server. Outside Lambda
# it does nothing, so docker run still works.
COPY --from=public.ecr.aws/awsguru/aws-lambda-adapter:0.9.1 /lambda-adapter /opt/extensions/lambda-adapter

COPY requirements.txt requirements-model.txt ./
RUN pip install --no-cache-dir --extra-index-url https://download.pytorch.org/whl/cpu \
    -r requirements.txt -r requirements-model.txt

COPY models.lock ./
COPY app ./app

# Bake the pinned model into the image.
RUN python - <<'PY'
from app.config import load_lock
from transformers import AutoModel, AutoProcessor

lock = load_lock()
AutoProcessor.from_pretrained(lock.model_id, revision=lock.revision)
AutoModel.from_pretrained(lock.model_id, revision=lock.revision)
PY

# From here on nothing may reach the internet for model files.
ENV HF_HUB_OFFLINE=1

EXPOSE 8080
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
