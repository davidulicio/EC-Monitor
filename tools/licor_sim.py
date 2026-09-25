#!/usr/bin/env python3
"""
LI-7x00 analyzer simulator.

Speaks enough of the LI-COR parenthetical grammar on TCP port 7200 to exercise
the EC Monitor Android app without a real instrument. Useful for checking the
app on your phone over WiFi before relying on it in the field.

Run it on the laptop:

    python licor_sim.py                    healthy LI-7200RS on port 7200
    python licor_sim.py --fault dirty      low signal strength
    python licor_sim.py --fault chopper    chopper temperature fault
    python licor_sim.py --fault flow       flow module reporting zero
    python licor_sim.py --fault smartflux  no new flux file for hours
    python licor_sim.py --fault usb        USB drive error
    python licor_sim.py --model 7500       open path analyzer instead
    python licor_sim.py --push 1           also push a data record every second
    python licor_sim.py --port 7201        second station on another port

Then point the app at the laptop's LAN address. Several instances on different
ports stand in for several stations.
"""

import argparse
import datetime
import math
import random
import socket
import socketserver
import threading
import time

ARGS = None
START = time.time()


def diag_value(enclosed, fault):
    ss_bits = 13              # 13 * 6.67 = 87 %
    if fault == "dirty":
        ss_bits = 7           # 47 %
    value = ss_bits
    sync, pll, det, chop = 1, 1, 1, 1
    if fault == "chopper":
        chop = 0
    if fault == "pll":
        pll = 0
    value |= sync << 4
    value |= pll << 5
    value |= det << 6
    value |= chop << 7
    if enclosed:
        pdif = 0 if fault == "pdif" else 1
        value |= pdif << 8
        value |= 1 << 9       # aux input ok
        value |= (0 if fault == "tin" else 1) << 10
        value |= 1 << 11
        value |= 1 << 12      # head detected
    return value


def wobble(base, amp, period):
    return base + amp * math.sin((time.time() - START) * 2 * math.pi / period)


def data_record():
    enclosed = ARGS.model == "7200"
    fault = ARGS.fault
    ss = 47.0 if fault == "dirty" else wobble(92.0, 1.5, 300)
    flow = 0.0 if fault == "flow" else wobble(14.9, 0.2, 60)
    drive = 100.0 if fault == "flow" else wobble(46.0, 3.0, 120)

    fields = [
        ("Ndx", "%d" % int((time.time() - START) * 150)),
        ("DiagVal", "%d" % diag_value(enclosed, fault)),
        ("CO2Raw", "%.7e" % (0.1283 + random.uniform(-0.0004, 0.0004))),
        ("CO2D", "%.7e" % wobble(17.2, 0.4, 90)),
        ("CO2MF", "%.7e" % (wobble(418.0, 6.0, 90) + random.uniform(-1.5, 1.5))),
        ("CO2MFd", "%.7e" % wobble(422.0, 6.0, 90)),
        ("H2ORaw", "%.7e" % (0.0553 + random.uniform(-0.0003, 0.0003))),
        ("H2OD", "%.7e" % wobble(480.0, 20.0, 200)),
        ("H2OMF", "%.7e" % wobble(11.8, 0.6, 200)),
        ("H2OMFd", "%.7e" % wobble(12.0, 0.6, 200)),
        ("DewPt", "%.7e" % wobble(9.4, 1.0, 400)),
        ("Temp", "%.7e" % wobble(18.6, 2.0, 600)),
        ("Pres", "%.7e" % wobble(98.4, 0.3, 900)),
        ("Cooler", "%.7e" % wobble(1.42, 0.05, 300)),
        ("AvgSS", "%.4f" % ss),
        ("CO2SS", "%.4f" % (ss + 0.8)),
        ("H2OSS", "%.4f" % (ss - 0.8)),
        ("DeltaSS", "%.4f" % (9.5 if fault == "delta" else 1.6)),
        ("Aux", "%.5f" % random.uniform(0, 0.01)),
    ]
    if enclosed:
        fields += [
            ("APres", "%.7e" % wobble(98.6, 0.3, 900)),
            ("DPres", "%.7e" % (8.4 if fault == "filter" else wobble(2.1, 0.1, 300))),
            ("AvgTemp", "%.7e" % wobble(18.9, 2.0, 600)),
            ("TempIn", "%.7e" % wobble(18.7, 2.0, 600)),
            ("TempOut", "%.7e" % wobble(19.1, 2.0, 600)),
            ("MeasFlowRate", "%.4f" % flow),
            ("VolFlowRate", "%.4f" % (flow * 1.05)),
            ("FlowPressure", "%.4f" % wobble(3.1, 0.1, 120)),
            ("FlowPower", "%.4f" % wobble(9.8, 0.2, 120)),
            ("FlowDrive", "%.2f" % drive),
        ]
    if ARGS.ch4:
        rssi = 7.0 if ARGS.fault == "mirror" else wobble(68.0, 3.0, 400)
        fields += [
            ("CH4", "%.6f" % wobble(1.95, 0.03, 250)),
            ("CH4D", "%.7e" % wobble(0.0812, 0.001, 250)),
            ("RSSI", "%.2f" % rssi),
            ("DIAG", "0"),
        ]
    if ARGS.sonic:
        fields += [
            ("U", "%.4f" % wobble(1.8, 1.2, 40)),
            ("V", "%.4f" % wobble(-0.4, 1.0, 55)),
            ("W", "%.4f" % wobble(0.02, 0.25, 17)),
            ("TS", "%.4f" % wobble(18.2, 1.8, 600)),
            ("SOS", "%.4f" % wobble(342.0, 2.0, 600)),
            ("AnemDiag", "1" if ARGS.fault == "sonic" else "0"),
        ]
    body = "".join("(%s %s)" % (k, v) for k, v in fields)
    return "(Data %s)" % body


def diagnostics_record():
    enclosed = ARGS.model == "7200"
    fault = ARGS.fault
    ss = 47.0 if fault == "dirty" else 92.0
    parts = [
        ("SYNCH", "TRUE"),
        ("PLL", "FALSE" if fault == "pll" else "TRUE"),
        ("DetOK", "TRUE"),
        ("Chopper", "FALSE" if fault == "chopper" else "TRUE"),
        ("Path", "%.1f" % ss),
    ]
    if enclosed:
        parts += [
            ("PDif", "FALSE" if fault == "pdif" else "TRUE"),
            ("AuxIn", "TRUE"),
            ("Tin", "FALSE" if fault == "tin" else "TRUE"),
            ("Tout", "TRUE"),
            ("Head", "1"),
        ]
    return "(Diagnostics %s)" % "".join("(%s %s)" % (k, v) for k, v in parts)


def last_ghg_file():
    now = datetime.datetime.now()
    if ARGS.fault == "smartflux":
        now = now - datetime.timedelta(hours=5)
    slot = now.replace(minute=0 if now.minute < 30 else 30, second=0, microsecond=0)
    return slot.strftime("%Y-%m-%dT%H%M%S") + "_AIU-1783.ghg"


def respond(query):
    """Returns the record for a query, or None when the query is not recognised."""
    q = query.replace(" ", "").replace("\t", "")
    enclosed = ARGS.model == "7200"
    model = ("LI-7200RS Enclosed CO2/H2O Analyzer" if enclosed
             else "LI-7500DS Open Path CO2/H2O Analyzer")
    serial = "72H-1783" if enclosed else "75H-2204"

    if q.startswith("(Data?"):
        return data_record()
    if q.startswith("(Diagnostics?"):
        return diagnostics_record()
    if q.startswith("(EmbeddedSW"):
        return ("(EmbeddedSW (Version 8.8.36)(Model %s)(ModelNum %s)(DSP 4.0.0)(FPGA 4.0.0))"
                % (model, ARGS.model))
    if q.startswith("(Coef"):
        return ("(Coef (Current (SerialNo %s)(Band (A 1.15))(CO2 (A 1.56704E+2)(B 2.15457E+4)"
                "(C 4.33894E+7)(D -1.24699E+10)(E 1.75102E+12)(XS 0.0023)(Z 0.0002))"
                "(H2O (A 5.24232E+3)(B 3.91896E+6)(C -2.33026E+8)(XS -0.0009)(Z 0.0185))"
                "(Pressure (A0 56.129)(A1 15.250))))" % serial)
    if q.startswith("(Network"):
        return ("(Network (Name %s)(IP (Type static)(Address %s)(Netmask 255.255.255.0)"
                "(Gateway 192.168.13.1)(MAC 00:1e:c0:11:22:33)))" % (ARGS.name, ARGS.ip))
    if q.startswith("(Outputs"):
        enet = ("(ENet (Freq %s)(Labels %s)(DiagRec TRUE)(EOL \"0D0A\")(Ndx TRUE)(DiagVal TRUE)"
                "(CO2Raw TRUE)(CO2D TRUE)(H2ORaw TRUE)(H2OD TRUE)(Temp TRUE)(Pres TRUE)"
                "(Aux TRUE)(Cooler TRUE))" % (ARGS.push, "FALSE" if ARGS.labelless else "TRUE"))
        return ("(Outputs (BW 10)(Delay 0)(SDM (Address 7))"
                "(Dac1 (Source NONE)(Zero 0)(Full 5))%s"
                "(Logging (Freq 10)(Split 30)(Zip TRUE)))" % enet)
    if q.startswith("(Inputs"):
        return ("(Inputs (Pressure (Source Measured)(UserVal 9.8e1))"
                "(Temperature (Source Measured)(UserVal 2.5e1))(Aux (A 1)(B 0)))")
    if q.startswith("(Calibrate"):
        return ("(Calibrate (ZeroCO2 (Val 0.8945)(Date 12 05 2026 10:37))"
                "(SpanCO2 (Val 1.0068)(Target 597.2)(Tdensity 23.154)(Date 12 05 2026 11:00))"
                "(ZeroH2O (Val 0.791075)(Date 12 05 2026 11:20))"
                "(SpanH2O (Val 1.00585)(Target 12.00)(Tdensity 447.421)(Date 12 05 2026 11:37)))")
    if q.startswith("(Info"):
        if ARGS.fault == "usb":
            return "(Info (USB (Size 32000)(Free 0)(State 3)))"
        free = 150 if ARGS.fault == "usbfull" else 21500
        return "(Info (USB (Size 32000)(Free %d)(State 1)))" % free
    if q.startswith("(Fluxes"):
        vin = 10.4 if ARGS.fault == "power" else 12.6
        sats = 2 if ARGS.fault == "gps" else 9
        return ("(Fluxes (Status (EddyPro (ConfigName carbonique_v4)(RunStatus Idle)"
                "(LastFile %s)(RunID 4471))"
                "(SmartFlux (Model SMARTFLUX3)(SerialNo SF3-0188)(Version 2.2.50)"
                "(EPVersion 7.0.9)(Vin %.2f)"
                "(USB (Size 32000)(Free 21500))"
                "(GPS (Lat 45.3891)(Long -73.9412)(Elev 41.2)(NumSat %d)))))"
                % (last_ghg_file(), vin, sats))
    if q.startswith("(CH4"):
        if not ARGS.ch4:
            return "(CH4 (Head None)(State 0)(DevList ))"
        return "(CH4 (Head li7700-0451)(State 1)(DevList li7700-0451))"
    if q.startswith("(FlowBox"):
        if not enclosed:
            return None
        flow = 0.0 if ARGS.fault == "flow" else 14.9
        return ("(FlowBox (BusAddress 32)(State 0)(SetFlowRate 15.00)(MeasFlowRate %.3f)"
                "(FlowPressure 3.11)(FlowPower 9.80)(FlowDrive %.1f))"
                % (flow, 100.0 if ARGS.fault == "flow" else 46.0))
    if q.startswith("(Clock"):
        now = datetime.datetime.now()
        return ("(Clock (Date %s)(Time %s)(PTP automatic)(Zone America/Toronto))"
                % (now.strftime("%d %m %Y"), now.strftime("%H:%M:%S")))
    if q.startswith("(FluxDevices"):
        return "(FluxDevices (DevList SF3-0188)(Device SF3-0188)(DeviceIP 192.168.13.40)(Connected 1))"
    if q.startswith("(MeteoSensors"):
        return None
    return None


def label_less_line():
    """Data in the tab delimited form the instrument uses with (Labels FALSE)."""
    rec = data_record()
    fields = ["Ndx", "DiagVal", "CO2Raw", "CO2D", "H2ORaw", "H2OD", "Temp", "Pres", "Aux", "Cooler"]
    values = {}
    depth = 0
    token = ""
    for part in rec.split("("):
        if not part:
            continue
        piece = part.split(")")[0]
        bits = piece.split(" ", 1)
        if len(bits) == 2:
            values[bits[0]] = bits[1]
    return "\t".join(values.get(f, "0") for f in fields)


class Handler(socketserver.BaseRequestHandler):

    def handle(self):
        peer = "%s:%d" % self.client_address
        print("[sim] %s connected" % peer, flush=True)
        self.request.settimeout(0.2)
        buf = ""
        stop = threading.Event()

        def pusher():
            if not ARGS.push:
                return
            interval = 1.0 / float(ARGS.push)
            while not stop.is_set():
                try:
                    line = label_less_line() if ARGS.labelless else data_record()
                    self.request.sendall((line + "\r\n").encode("ascii"))
                except OSError:
                    return
                stop.wait(interval)

        t = threading.Thread(target=pusher, daemon=True)
        t.start()

        try:
            while True:
                try:
                    chunk = self.request.recv(4096)
                except socket.timeout:
                    continue
                if not chunk:
                    break
                buf += chunk.decode("ascii", "ignore")
                while "\n" in buf:
                    line, buf = buf.split("\n", 1)
                    line = line.strip()
                    if not line:
                        continue
                    print("[sim] <- %s" % line, flush=True)
                    reply = respond(line)
                    if reply is None:
                        reply = "(Error (Received TRUE))"
                    print("[sim] -> %s" % reply[:110], flush=True)
                    self.request.sendall((reply + "\r\n").encode("ascii"))
        except OSError:
            pass
        finally:
            stop.set()
            print("[sim] %s disconnected" % peer, flush=True)


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


def main():
    global ARGS
    p = argparse.ArgumentParser(description="LI-7x00 analyzer simulator")
    p.add_argument("--port", type=int, default=7200)
    p.add_argument("--host", default="0.0.0.0")
    p.add_argument("--model", choices=["7200", "7500"], default="7200")
    p.add_argument("--name", default="UQAM-SIM-7550")
    p.add_argument("--ip", default="192.168.13.20")
    p.add_argument("--fault", default="none",
                   choices=["none", "dirty", "chopper", "pll", "pdif", "tin", "flow", "filter",
                            "delta", "usb", "usbfull", "smartflux", "power", "gps", "mirror", "sonic"])
    p.add_argument("--push", type=float, default=0,
                   help="also push data records at this rate in Hz, 0 to answer queries only")
    p.add_argument("--labelless", action="store_true",
                   help="push tab delimited values, as (Labels FALSE) does")
    p.add_argument("--no-ch4", dest="ch4", action="store_false", default=True)
    p.add_argument("--no-sonic", dest="sonic", action="store_false", default=True)
    ARGS = p.parse_args()

    print("[sim] LI-%s on %s:%d, fault=%s, push=%s Hz"
          % (ARGS.model, ARGS.host, ARGS.port, ARGS.fault, ARGS.push), flush=True)
    Server((ARGS.host, ARGS.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
