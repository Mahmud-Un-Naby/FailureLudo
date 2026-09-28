# Build first: ./gradlew -PserverOnly=true :online-server:installDist
# Use online-server/ as the Docker context; no repository credentials are copied.
FROM eclipse-temurin:17-jre
WORKDIR /opt/ludo
COPY --chown=10001:10001 build/install/online-server/ ./
USER 10001:10001
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["bin/online-server"]
