"""
multi-bt-audio: play your Windows system audio to 2 or more Bluetooth
(or any other) output devices at the same time.

How it works:
  1. Captures system audio with WASAPI loopback (from the default output device,
     or one you choose with --source).
  2. Sends that audio to every selected output device, each in its own stream.
  3. Optional per-device delay (--delay) so devices with different Bluetooth
     latency line up.
"""
from __future__ import annotations

import argparse
import sys
import threading

import numpy as np

try:
    import pyaudiowpatch as pyaudio
except ImportError:  # pragma: no cover
    sys.exit("Missing dependency. Run: pip install -r requirements.txt")


class Sink:
    """One output device with its own buffer, fed by the capture callback."""

    def __init__(self, pa: "pyaudio.PyAudio", info: dict, src_rate: int,
                 src_channels: int, delay_ms: int = 0, max_buffer_ms: int = 800):
        self.name = info["name"]
        self.rate = int(info["defaultSampleRate"])
        self.channels = 2 if info["maxOutputChannels"] >= 2 else 1
        self.src_rate = src_rate
        self.src_channels = src_channels
        self.lock = threading.Lock()
        self.frame_bytes = self.channels * 4  # float32
        self.max_bytes = int(self.rate * max_buffer_ms / 1000) * self.frame_bytes
        silence = int(self.rate * delay_ms / 1000) * self.frame_bytes
        self.buf = bytearray(silence)
        self.stream = pa.open(
            format=pyaudio.paFloat32,
            channels=self.channels,
            rate=self.rate,
            output=True,
            output_device_index=info["index"],
            frames_per_buffer=1024,
            stream_callback=self._callback,
        )

    def push(self, data: np.ndarray) -> None:
        """data: float32 array shaped (frames, src_channels)."""
        if self.channels == 2:
            out = np.repeat(data, 2, axis=1) if data.shape[1] == 1 else data[:, :2]
        else:
            out = data.mean(axis=1, keepdims=True)
        if self.rate != self.src_rate:
            n_out = max(1, round(out.shape[0] * self.rate / self.src_rate))
            x_old = np.linspace(0.0, 1.0, out.shape[0], endpoint=False)
            x_new = np.linspace(0.0, 1.0, n_out, endpoint=False)
            out = np.stack(
                [np.interp(x_new, x_old, out[:, c]) for c in range(out.shape[1])],
                axis=1,
            )
        raw = np.ascontiguousarray(out, dtype=np.float32).tobytes()
        with self.lock:
            self.buf += raw
            overflow = len(self.buf) - self.max_bytes
            if overflow > 0:  # drop oldest audio rather than drift further behind
                overflow -= overflow % self.frame_bytes
                del self.buf[:overflow]

    def _callback(self, _in, frame_count, _time, _status):
        need = frame_count * self.frame_bytes
        with self.lock:
            chunk = bytes(self.buf[:need])
            del self.buf[:need]
        if len(chunk) < need:
            chunk += bytes(need - len(chunk))  # underrun -> silence
        return chunk, pyaudio.paContinue

    def close(self) -> None:
        self.stream.stop_stream()
        self.stream.close()


def output_devices(pa) -> list[dict]:
    wasapi = pa.get_host_api_info_by_type(pyaudio.paWASAPI)
    devs = []
    for i in range(pa.get_device_count()):
        d = pa.get_device_info_by_index(i)
        if (d["hostApi"] == wasapi["index"] and d["maxOutputChannels"] > 0
                and not d.get("isLoopbackDevice", False)):
            devs.append(d)
    return devs


def find_device(devs: list[dict], key: str) -> dict:
    if key.isdigit():
        for d in devs:
            if d["index"] == int(key):
                return d
    matches = [d for d in devs if key.lower() in d["name"].lower()]
    if not matches:
        sys.exit(f"No output device matches '{key}'. Run: python multi_bt_audio.py list")
    if len(matches) > 1:
        names = "\n  ".join(f"[{d['index']}] {d['name']}" for d in matches)
        sys.exit(f"'{key}' is ambiguous, be more specific or use the index:\n  {names}")
    return matches[0]


def cmd_list(pa) -> None:
    print("Output devices (use the number or part of the name):")
    for d in output_devices(pa):
        print(f"  [{d['index']}] {d['name']}")
    print("\nLoopback sources (for --source):")
    for d in pa.get_loopback_device_info_generator():
        print(f"  [{d['index']}] {d['name']}")


def cmd_run(pa, args) -> None:
    devs = output_devices(pa)

    if args.source:
        loop = next((d for d in pa.get_loopback_device_info_generator()
                     if args.source.lower() in d["name"].lower()
                     or args.source == str(d["index"])), None)
        if loop is None:
            sys.exit(f"No loopback source matches '{args.source}'.")
    else:
        loop = pa.get_default_wasapi_loopback()

    keys = args.outputs
    if not keys:
        cmd_list(pa)
        keys = input("\nOutput devices to use (comma separated): ").split(",")
        keys = [k.strip() for k in keys if k.strip()]
    if len(keys) < 1:
        sys.exit("Pick at least one output device.")

    delays = {}
    for item in args.delay or []:
        name, _, ms = item.rpartition("=")
        delays[name.lower()] = int(ms)

    src_rate = int(loop["defaultSampleRate"])
    src_ch = int(loop["maxInputChannels"])
    sinks: list[Sink] = []
    for key in keys:
        d = find_device(devs, key)
        if d["name"] == loop["name"].replace(" [Loopback]", ""):
            print(f"Warning: '{d['name']}' is also the capture source; it already plays "
                  "natively, so you will hear it twice. Mute it or pick another --source.")
        delay = next((ms for n, ms in delays.items() if n in d["name"].lower()), 0)
        sinks.append(Sink(pa, d, src_rate, src_ch, delay_ms=delay))
        print(f"-> {d['name']}  ({sinks[-1].rate} Hz, {sinks[-1].channels} ch, delay {delay} ms)")

    def capture(in_data, _frames, _time, _status):
        data = np.frombuffer(in_data, dtype=np.float32).reshape(-1, src_ch)
        for s in sinks:
            s.push(data)
        return None, pyaudio.paContinue

    stream = pa.open(
        format=pyaudio.paFloat32, channels=src_ch, rate=src_rate, input=True,
        input_device_index=loop["index"], frames_per_buffer=1024,
        stream_callback=capture,
    )
    print(f"\nCapturing: {loop['name']}\nStreaming to {len(sinks)} device(s). Ctrl+C to stop.")
    try:
        threading.Event().wait()
    except KeyboardInterrupt:
        pass
    finally:
        stream.stop_stream()
        stream.close()
        for s in sinks:
            s.close()


def main() -> None:
    p = argparse.ArgumentParser(description="Play system audio to multiple output devices.")
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("list", help="show output devices and loopback sources")
    r = sub.add_parser("run", help="start streaming")
    r.add_argument("-o", "--outputs", nargs="+", help="device index or name fragment(s)")
    r.add_argument("-s", "--source", help="loopback source (default: current default output)")
    r.add_argument("-d", "--delay", nargs="+", metavar="NAME=MS",
                   help='extra delay per device, e.g. --delay "JBL=120"')
    args = p.parse_args()

    if sys.platform != "win32":
        sys.exit("This tool uses WASAPI loopback and only runs on Windows.")
    pa = pyaudio.PyAudio()
    try:
        cmd_list(pa) if args.cmd == "list" else cmd_run(pa, args)
    finally:
        pa.terminate()


if __name__ == "__main__":
    main()