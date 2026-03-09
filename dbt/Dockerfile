FROM python:3.12-slim
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY app.py ./app.py
COPY dbt_project.yml ./dbt_project.yml
COPY packages.yml ./packages.yml
COPY macros ./macros
COPY models ./models
COPY profiles ./profiles
EXPOSE 8090
CMD ["python", "app.py"]
