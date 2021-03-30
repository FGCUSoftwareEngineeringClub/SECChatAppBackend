FROM openjdk:8-jdk
EXPOSE 80:8080
ENV DB_URL=postgresql://localhost:49153/secchat
ENV DB_USERNAME=admin
ENV DB_PASSWORD=password

RUN mkdir /app
COPY ./build/install/ /app/
WORKDIR /app/ChatAppBackend/bin
CMD ["./ChatAppBackend"]