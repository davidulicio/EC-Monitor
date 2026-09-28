#!/bin/bash
# Runs the off-device test suite: the protocol layer on its own, then the real
# polling client and the shared report against the simulator.
set -e
cd "$(dirname "$0")/.."

OUT=out-test
CORE_SRC=$(find src/ca/uqam/ecmonitor/proto src/ca/uqam/ecmonitor/net -name '*.java')

# Station.java and Report.java reference org.json, which lives in android.jar.
if [ -z "$ANDROID_JAR" ]; then
    if [ -f /usr/lib/android-sdk/platforms/android-23/android.jar ]; then
        ANDROID_JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
    elif [ -n "$ANDROID_HOME" ]; then
        ANDROID_JAR=$(ls -1 "$ANDROID_HOME"/platforms/*/android.jar 2>/dev/null | sort -V | tail -1)
    fi
fi

rm -rf "$OUT"
mkdir -p "$OUT"

echo "== compile tests"
javac -nowarn ${ANDROID_JAR:+-classpath "$ANDROID_JAR"} -d "$OUT" \
    $CORE_SRC src/ca/uqam/ecmonitor/Station.java src/ca/uqam/ecmonitor/Report.java \
    test/CoreTest.java test/IntegrationTest.java test/ReportTest.java 2>&1 \
    | grep -v "^Picked up" || true

CP="$OUT${ANDROID_JAR:+:$ANDROID_JAR}"

echo
echo "== CoreTest (parser, diagnostics, read-only guard, rules)"
java -cp "$CP" CoreTest 2>&1 | grep -v "^Picked up"

PORT=${PORT:-7299}
echo
echo "== starting the simulator on port $PORT"
python3 tools/licor_sim.py --port "$PORT" > /tmp/licor_sim_test.log 2>&1 &
SIM=$!
trap 'kill $SIM 2>/dev/null || true' EXIT
sleep 1.5

echo "== IntegrationTest (polling client against the simulator)"
java -cp "$CP" IntegrationTest "$PORT" 2>&1 | grep -v "^Picked up"

echo
echo "== ReportTest (shared text report)"
java -cp "$CP" ReportTest "$PORT" 2>&1 | grep -v "^Picked up" | grep -vE "^    |^  [A-Z]|^EC station|^Station |^Address |^Taken |^Note |^Model |^Head |^Firmware|^Hostname|^Last data|^Assessment|^Produced|^----|^$"

echo
echo "All suites passed."
