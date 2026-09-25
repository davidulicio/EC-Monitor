# Changelog

## v1.0

First working version, tested against LI-7200RS and LI-7500DS style instruments
using the simulator, and in the field on Android.

- Station list with a health check across every station at once
- Overview with live gas, signal strength, cell, flow, CH4 and sonic values
- Checks tab: ranked issues with the next thing to look at, decoded diagnostic
  flags, and the dates of the last zero and span
- Values tab: every field the instrument returns, grouped and with units
- Console tab: raw traffic both ways, with one-tap standard queries
- Shareable plain text report
- Strictly read only, enforced by `Queries.isReadOnly` on every outgoing command
- `licor_sim.py` simulator with sixteen reproducible fault scenarios
- Builds without Android Studio, Gradle or a download from Google
