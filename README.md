# multi-bt-audio

Play your Windows system audio through **2 or more Bluetooth speakers/headphones at the same time**.

It captures what your PC is playing (WASAPI loopback) and streams it to every device you pick,
each with an optional delay to compensate for Bluetooth latency differences.

## Requirements
- Windows 10/11
- Python 3.10+
- Two or more Bluetooth devices already paired (use the **Stereo** profile, not "Hands-Free")

## Install
```powershell
git clone https://github.com/hamza012535/multi-bt-audio.git
cd multi-bt-audio
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
```

## Use
```powershell
# 1. See your devices
python multi_bt_audio.py list

# 2. Stream to two or more (index or part of the name)
python multi_bt_audio.py run -o "JBL" "Sony" "Realtek"

# 3. Sync them if one is late (add delay to the FASTER device)
python multi_bt_audio.py run -o "JBL" "Sony" --delay "JBL=150"
```

Run `python multi_bt_audio.py run` with no `-o` for an interactive picker.

## Tips
- **Avoid hearing double:** the capture source is your current default output. If that device is
  also in your `-o` list it plays twice. Set the default output to something you are not
  listening to (or use `--source`), then list only the Bluetooth devices in `-o`.
- **Echo/offset between speakers:** raise `--delay` on the device that sounds earlier, in 20-50 ms steps.
- **Dropouts:** most Bluetooth adapters handle 2-3 audio links well. Use a good BT 5.x adapter and
  keep devices close. More devices = more airtime = more stutter.
- Video will lag slightly behind the picture because of Bluetooth latency; this is normal.

## How it works
`pyaudiowpatch` (a PyAudio fork with loopback support) captures the mix -> each device gets its own
buffered output stream, resampled/channel-matched to that device's native format.

## Roadmap
- [ ] System-tray GUI with per-device volume
- [ ] Auto-calibrate delay
- [ ] Optional BLE (Bleak) module for connecting to multiple BLE gadgets
- [ ] Packaged `.exe` via PyInstaller

## License
MIT