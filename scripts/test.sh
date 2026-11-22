#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p out/main out/test
find src/main/java -name '*.java' -print0 | xargs -0 javac --release 11 -d out/main
javac --release 11 -cp out/main -d out/test src/test/java/os/RegressionSuite.java
java -cp out/main:out/test os.RegressionSuite
