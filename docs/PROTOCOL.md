# The LI-7x00 configuration grammar

Notes gathered while writing this app, from the help shipped with the LI-COR
LI7x00 PC software (version 8.9.2), the grammar trees `7200tree.dtd` and
`7500tree.dtd` in the same install directory, and the strings inside
`li7200.exe`. The authoritative source is the "Configuration grammar" topic in
that help; this file records what the app actually relies on.

## Transport

The LI-7550 and LI-7500DS answer on **TCP port 7200**. The same grammar is used
over RS-232. Commands are terminated with a line feed (decimal 10); carriage
return plus line feed works equally well, because anything outside the
parentheses is ignored.

The PC software's `connect-default.ini` also shows port 22, which it uses for
file transfer over SSH with the password `licor`. This app does not touch that.

## Shape of a record

Every message is a parenthesised tree. A node is either a leaf carrying a raw
value or a container carrying child nodes.

    (Data (Ndx 1545)(DiagVal 250)(CO2Raw 1.5386712e-1)(CO2D 3.2183277e1)
          (H2ORaw 3.5775542e-2)(H2OD 1.9687008e2)(Temp 2.4227569e1)
          (Pres 9.8640356e1)(Aux 0)(Cooler 1.5756724))

    (Diagnostics (SYNC TRUE)(PLL TRUE)(DetOK TRUE)(Chopper TRUE)(Path 63))

Things the parser has to cope with, all of which appear in real records:

- values containing spaces: `(Date 26 08 2009 10:37)`
- values containing slashes and pipes: `(Model LI-7x00RS CO2/H2O Analyzer)`, `(FPGA 4.0.0|)`
- quoted values: `(EOL "0D0A")`
- empty values: `(Target )`
- records concatenated with no separator, and records split across TCP reads

The grammar is **case sensitive**. `(Outputs(BW 10))` works, `(outputs(bw 10))`
does not. Two tags differ only by case and mean different things: `Temp` is the
analyzer block temperature, `TEMP` is the LI-7700 temperature.

## Reading without writing

A query is the same shape with `?` as the value. The documented set is

    (Outputs ?)  (Calibrate ?)  (Coef ?)  (Data ?)  (Diagnostics ?)
    (EmbeddedSW ?)  (Inputs ?)  (Info ?)  (Network ?)
    (MeteoDevices ?)  (MeteoSensors ?)

Individual parameters can be queried by path: `(Outputs(RS232(Freq ?)))` returns
`(Outputs (RS232 (Freq 5)))`. Path queries work for configuration parameters,
not for data or diagnostics, which are requested as whole records.

`(Data ?)` puts one data record in the output queue, so an app can poll without
changing the instrument's own output rate. An ENQ character (0x05) over RS-232
does the same thing.

The app also sends `(Fluxes(Status ?))`, `(CH4 ?)`, `(FlowBox ?)`, `(Clock ?)`,
`(FluxDevices ?)`. These come from the grammar tree rather than the documented
query list, so a given firmware may not answer them. An unrecognised command
comes back as `(Error (Received TRUE))`, which is harmless, and the Console tab
shows which ones a particular instrument honours.

**Anything with a value is a write.** `(Outputs(BW 10))`, `(Calibrate(ZeroCO2(Date
"now")))` and `(Program(Reset TRUE))` all change the instrument.
`Queries.isReadOnly` parses each outgoing command and refuses it unless every
leaf value is either `?` or empty, with at least one `?` present. That check is
the single place read-only is enforced, and `CoreTest` covers it.

## Diagnostic words

`(Data (DiagVal ...))` is a bit field. Bits 0 to 3 are signal strength: multiply
by 6.67 for a percentage.

| Bit | LI-7500 family (1 byte, 0 to 255) | LI-7200 family (2 bytes, 0 to 8191) |
| --- | --- | --- |
| 0 to 3 | signal strength | signal strength |
| 4 | sync | sync |
| 5 | PLL, chopper wheel at the right rate | PLL |
| 6 | detector temperature near setpoint | detector |
| 7 | chopper temperature near setpoint | chopper |
| 8 | | differential pressure sensor in 0.1 to 4.9 V |
| 9 | | internal reference voltages OK, else the LI-7550 needs service |
| 10 | | inlet thermocouple continuity |
| 11 | | outlet thermocouple continuity |
| 12 | | head detected |
| 13 to 15 | | unused, read 0 |

In every case 1 means good. The worked examples in the LI-COR help are 125 for
the open path and 8061 for the enclosed, both meaning chopper not OK with signal
strength 87 %. `CoreTest` asserts exactly those two.

The `(Diagnostics ...)` record carries the same information as named booleans
and is emitted once a second when `(DiagRec TRUE)`. The app prefers it when
present because it needs no bit map, and falls back to `DiagVal` otherwise. Note
the tree spells the first flag `SYNC` in one place and `SYNCH` in another; both
are accepted.

## Label-less output

With `(Labels FALSE)` the instrument drops the field names and emits tab
delimited values:

    252  250  0.15401  32.2167  0.03569  196.703  24.33  98.6  0  1.5730

The fields present are those set TRUE in `(Outputs(ENet ...))`, in the order the
`Data` node lists them in the grammar tree. The app reconstructs the mapping
from a `(Outputs ?)` query and says so in the Checks tab, because that mapping
is inferred rather than read. `CoreTest` checks the reconstruction against the
worked example in the help, which is the line above.

## What lives where

One connection to the LI-7550 carries the whole system, because the other
instruments report through it:

| Record | Contents |
| --- | --- |
| `Data` | gas, cell, signal strength, flow, and when connected the sonic and CH4 fields |
| `CH4Data` | LI-7700 methane, RSSI, drop rate, its own temperature and pressure |
| `SonicData` | U, V, W, sonic temperature, speed of sound, anemometer diagnostic |
| `Info` | USB drive size, free space and state (0 absent, 1 logging, 2 idle, 3 error) |
| `Fluxes` | SmartFlux model, serial, firmware, EddyPro version, input volts, GPS, last file |
| `FlowBox` | flow module setpoint, measured rate, pressure, drive |
| `Coef` | head serial number and factory calibration coefficients |
| `Calibrate` | dates and values of the last zero and span |
| `Network` | host name, address, netmask, gateway, MAC |

## Not covered here

The SmartFlux system diagnostics that the Windows software shows under
Diagnostics > SmartFlux use **port 5050** with a different protocol, which this
app does not speak. What it reports about SmartFlux comes from
`(Fluxes(Status ?))` on port 7200.
