FROM python:3.12-slim
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY sync_worker ./sync_worker
CMD ["celery", "-A", "sync_worker.celery_app:app", "worker", "-l", "info"]
