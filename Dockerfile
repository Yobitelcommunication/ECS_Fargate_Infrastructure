FROM ubuntu:18.04
ENV LC_ALL C.UTF-8
ENV LANG C.UTF-8
EXPOSE 80
COPY . /app
RUN mkdir /app-storage
WORKDIR /app
RUN apt-get update && apt-get install -y python3-flask 
ENV FLASK_APP hello.py
CMD ["flask","run","--host=0.0.0.0","--port=80"]
