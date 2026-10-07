FROM continuumio/miniconda3:25.3.1-1
WORKDIR /app
RUN conda create --yes --prefix /app/rl_env_gpu_clean --clone base
RUN printf 'source /opt/conda/etc/profile.d/conda.sh\nconda activate /app/rl_env_gpu_clean\n' > /app/rl_env_gpu_clean/bin/activate
COPY requirements.txt .
RUN bash -c 'source rl_env_gpu_clean/bin/activate && pip install --no-cache-dir -r requirements.txt'
COPY app app
COPY tests tests
RUN groupadd -g 10001 bemon && useradd -u 10001 -g bemon -M bemon
USER 10001:10001
EXPOSE 8000
CMD ["bash", "-c", "source rl_env_gpu_clean/bin/activate && exec uvicorn app.main:app --host 0.0.0.0 --port 8000 --no-access-log"]
