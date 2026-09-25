# EC Monitor

Check eddy covariance stations from an Android phone. It speaks the same LI-COR
configuration grammar the LI-7x00 Windows software uses on TCP port 7200, so it
talks directly to the LI-7550 or LI-7500DS at each tower.

**It only reads.** Every command it sends is a query ending in `?`. Anything that
would set a value is refused before it reaches the socket, so the app cannot
start a calibration, change an output, alter the logging configuration or stop a
run. That guard lives in one place, `Queries.isReadOnly`, and is covered by
tests.

## What it shows

**Station list.** One line per analyzer with a coloured status, the headline
problem and when it was last checked. *Check all stations* probes every station
in parallel: connect, ask everything once, disconnect. The "is anything broken
this morning" view.

**Overview.** Live CO2, H2O, signal strength, cell temperature and pressure,
detector cooler, flow rate and pump drive on enclosed paths, CH4 and LI-7700
RSSI, wind speed and sonic temperature. Plus instrument identity, the logging
drive, and SmartFlux status including the age of the last flux file.

**Checks.** Ranked list of things worth looking at, each with what to check next,
then the decoded diagnostic flags, the raw diagnostic word in binary, and the
dates of the last zero and span.

**Values.** Every field the instrument returned, grouped and labelled with units.

**Console.** Raw traffic both ways, with an input box and one-tap buttons for the
standard queries. The thing to reach for when something does not look right: it
shows exactly what was asked and exactly what came back.

*Share this check* produces a plain text report suitable for an email to a
colleague or to LI-COR support.

## What it does not do

Configuration changes, calibration, starting and stopping logging, file transfer,
real-time graphs and flux results are all missing on purpose: they are writes, or
they need a second service. The SmartFlux system diagnostics the Windows software
shows under Diagnostics > SmartFlux use port 5050 with a different protocol and
are not covered.

## Installing

Grab the APK from the [Releases](../../releases) page, copy it to the phone and
open it. Android will ask permission to install from that source the first time.
Needs Android 5.0 or newer; the only permission requested is network access.

Then add a station: a name, the address the analyzer answers on, and the port,
normally 7200. *Import stations* takes a block of lines like

    UQAM_1, 10.0.0.20, 7200
    UQAM_2, 10.0.0.21, 7200

which beats typing eight of them.

## Testing without an instrument

`tools/licor_sim.py` pretends to be an analyzer. On a computer on the same WiFi:

    python3 tools/licor_sim.py

then point a station at that computer on port 7200. It reproduces specific
failures so you can see what the app does before it matters:

    --fault flow        flow module reporting zero
    --fault smartflux   no new flux file for hours
    --fault dirty       low signal strength
    --fault mirror      LI-7700 mirrors dirty
    --fault usb         logging drive error
    --fault chopper     chopper temperature off setpoint
    --model 7500        open path instead of enclosed
    --port 7201         a second station

## Building

No Android Studio, no Gradle, and nothing downloaded from Google. On Debian or
Ubuntu:

    sudo apt-get install aapt apksigner zipalign android-sdk-build-tools \
                         android-sdk-platform-23 dalvik-exchange default-jdk-headless
    ./build.sh

The result is `build/ec-monitor.apk`, signed with a key `build.sh` creates on
first run. **Keep `debug.keystore` and keep it out of git.** Android only accepts
an update to an installed app if the new build carries the same signature, so
losing that file means every phone has to uninstall before updating.

The app compiles against the API 23 platform and declares target 34, using only
framework classes: no AndroidX, no Compose, no Kotlin. That is what makes it
buildable offline in about five seconds.

## Tests

    ./tools/run-tests.sh

`CoreTest` checks the parser against the records printed in the LI-COR
"Configuration grammar" help topic, the documented diagnostic bit map examples
(125 for the open path, 8061 for the enclosed), stream framing across split and
concatenated reads, the label-less field order, the read-only guard, and the
assessment rules. `IntegrationTest` runs the real polling client against the
simulator, and `ReportTest` covers the shared report including empty and
malformed snapshots.

## Continuous integration

`.github/workflows/build.yml` installs the same toolchain, runs the tests and
builds the APK on every push. Pushing a tag attaches the APK to a GitHub release:

    git tag v1.0 && git push origin v1.0

By default CI generates a throwaway signing key, which is fine for a one-off
build but produces an APK that cannot update an installed copy. To sign CI builds
with your own key, add two repository secrets under Settings > Secrets and
variables > Actions:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 debug.keystore` |
| `ANDROID_KEYSTORE_PASSWORD` | the store password, `android` by default |

## Repository layout

    AndroidManifest.xml          minSdk 21, targetSdk 34, one permission
    build.sh                     the whole build, about 90 lines
    src/ca/uqam/ecmonitor/
      MainActivity.java          station list and the check-all sweep
      StationActivity.java       live view: overview, checks, values, console
      EditStationActivity.java   add and edit a station
      Station.java  Store.java   model and persistence
      Report.java                the shareable text report
      net/Client.java            read-only polling client and one-shot probe
      net/Traffic.java           ring buffer behind the console
      proto/Parser.java          grammar parser and TCP stream framing
      proto/Node.java            one node of a record
      proto/Queries.java         the query set and the read-only guard
      proto/Diagnostics.java     diagnostic bit maps
      proto/Metrics.java         tag labels, units and grouping
      proto/Assessment.java      the rules that turn readings into findings
      proto/Snapshot.java        merged state of one station
      proto/Fmt.java             formatting helpers
      ui/Ui.java                 colours, cards, tiles, chips
    test/                        CoreTest, IntegrationTest, ReportTest
    tools/licor_sim.py           analyzer simulator
    tools/run-tests.sh           runs all three suites
    docs/PROTOCOL.md             notes on the LI-COR grammar

Everything outside `proto/` and `net/` is Android. Those two packages are plain
Java with no Android imports, which is why they can be tested on a laptop and
why a port to another platform would start there.

## The checks and their thresholds

Starting points drawn from the LI-COR help and ordinary practice, not gospel.
They live in `Assessment.java` and are easy to change.

| Check | Warning | Fault |
| --- | --- | --- |
| Signal strength | below 70 % | below 40 % |
| Delta signal strength | above 5 | |
| Detector cooler | above 2.6 V | |
| Flow rate, enclosed | below 10 SLPM | below 1 SLPM |
| Pump drive | above 90 % | |
| Differential pressure | above 6 kPa | |
| Cell pressure | outside 50 to 110 kPa | |
| CO2 mole fraction | outside 250 to 1200 | |
| Logging drive | below 15 % free | error state, or under 200 MB |
| SmartFlux input voltage | | below 11.5 V |
| GPS satellites | fewer than 4 | |
| Last flux file | older than 75 min | older than 3 h |
| LI-7700 RSSI | below 20 % | below 10 % |
| LI-7700 drop rate | above 5 % | |
| Diagnostic flags | pressure, thermocouples | sync, PLL, chopper, detector, head, aux |

## Protocol

See [docs/PROTOCOL.md](docs/PROTOCOL.md) for the record shape, the query syntax,
the diagnostic bit maps and the label-less output format.

## Licence

MIT, see [LICENSE](LICENSE). Not affiliated with or endorsed by LI-COR
Biosciences.
