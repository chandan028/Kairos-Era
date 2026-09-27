#!/usr/bin/env python3
"""Synthesizes the built-in reminder sounds (original, royalty-free) into app/src/main/res/raw.
Pure standard library, so the sounds are reproducible from source."""
import math, os, struct, wave

RATE = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")

def write(name, samples):
    os.makedirs(OUT, exist_ok=True)
    peak = max(1e-9, max(abs(s) for s in samples))
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(s / peak * 0.8 * 32767)) for s in samples))

def tone(freq, dur, decay, partials=((1, 1.0),), attack=0.004):
    n = int(RATE * dur)
    out = []
    for i in range(n):
        t = i / RATE
        env = min(1.0, t / attack) * math.exp(-t * decay)
        out.append(env * sum(a * math.sin(2 * math.pi * freq * m * t) for m, a in partials))
    return out

def mix(*tracks):
    n = max(off + len(tr) for off, tr in tracks)
    buf = [0.0] * n
    for off, tr in tracks:
        for i, s in enumerate(tr):
            buf[off + i] += s
    return buf

# Kairos Bell: a warm bell with inharmonic partials, one strike and a gentle second.
bell = ((1, 1.0), (2.01, 0.45), (2.76, 0.3), (5.4, 0.12))
write("kairos_bell", mix((0, tone(784, 1.8, 2.6, bell)), (int(RATE * 0.45), [s * 0.6 for s in tone(1046.5, 1.4, 3.0, bell)])))
# Soft Chime: two soft ascending notes.
chime = ((1, 1.0), (3, 0.08))
write("soft_chime", mix((0, tone(1046.5, 0.9, 4.5, chime, attack=0.02)), (int(RATE * 0.22), tone(1318.5, 1.0, 4.0, chime, attack=0.02))))
# Focus: three short, calm pulses.
pulse = ((1, 1.0), (2, 0.2))
write("focus_tone", mix(*[(int(RATE * 0.18 * k), tone(659.3, 0.16, 18, pulse, attack=0.008)) for k in range(3)]))
print("sounds written")
