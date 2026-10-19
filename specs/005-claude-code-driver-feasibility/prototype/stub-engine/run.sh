#!/bin/sh
# Build and start the stub engine. ENGINE_TOKEN (default dev-token), ENGINE_PORT (default 8787).
set -e
cd "$(dirname "$0")"
[ -f cp.txt ] || mvn -q -B dependency:build-classpath -Dmdep.outputFile=cp.txt
mkdir -p target/classes
javac -nowarn -d target/classes -cp "$(cat cp.txt)" src/main/java/stub/Main.java
exec java -cp "target/classes:$(cat cp.txt)" stub.Main
