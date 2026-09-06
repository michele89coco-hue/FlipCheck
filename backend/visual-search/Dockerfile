FROM python:3.12-slim
WORKDIR /service
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY server.py .
ENV BIND_ADDRESS=0.0.0.0 PORT=8080
USER 65534:65534
EXPOSE 8080
CMD ["python", "server.py"]
