"""
Generates royalty-free synthesized audio loops + SFX for ActionCut's built-in
music/SFX library. All content is procedurally generated here (no third-party
samples), so it is unencumbered by any licence.

Outputs 16-bit PCM mono WAV files into app/src/main/res/raw/.
"""
import math
import os
import random
import struct
import wave

SR = 22050  # sample rate (Hz), mono — small + universally decodable
OUT = os.path.join("app", "src", "main", "res", "raw")
random.seed(42)


def midi(n):
    return 440.0 * (2.0 ** ((n - 69) / 12.0))


def env(i, total, attack=0.01, release=0.05):
    """Simple attack/release amplitude envelope (avoids clicks)."""
    t = i / SR
    dur = total / SR
    a = min(1.0, t / attack) if attack > 0 else 1.0
    r = min(1.0, (dur - t) / release) if release > 0 else 1.0
    return max(0.0, min(a, r))


def write_wav(name, samples):
    # soft-clip + normalise to int16
    out = []
    peak = max(1e-6, max(abs(s) for s in samples))
    gain = 0.89 / peak if peak > 0.89 else 1.0
    for s in samples:
        v = math.tanh(s * gain)  # gentle saturation
        out.append(int(max(-1.0, min(1.0, v)) * 32767))
    path = os.path.join(OUT, name + ".wav")
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", v) for v in out))
    secs = len(samples) / SR
    print(f"  {name}.wav  {secs:.1f}s  {os.path.getsize(path)//1024}KB")


def note(freq, dur_s, amp=0.25, harmonics=(1.0, 0.35, 0.18), vibrato=0.0):
    n = int(dur_s * SR)
    buf = [0.0] * n
    for i in range(n):
        t = i / SR
        vib = 1.0 + vibrato * math.sin(2 * math.pi * 5.0 * t)
        s = 0.0
        for h, ha in enumerate(harmonics, start=1):
            s += ha * math.sin(2 * math.pi * freq * h * vib * t)
        buf[i] = s * amp * env(i, n, attack=0.012, release=max(0.06, dur_s * 0.3))
    return buf


def chord(notes, dur_s, amp=0.18):
    n = int(dur_s * SR)
    buf = [0.0] * n
    for f in notes:
        nb = note(f, dur_s, amp=amp, harmonics=(1.0, 0.3, 0.12), vibrato=0.004)
        for i in range(n):
            buf[i] += nb[i]
    return buf


def kick(dur_s=0.18, amp=0.9):
    n = int(dur_s * SR)
    buf = [0.0] * n
    for i in range(n):
        t = i / SR
        f = 120.0 * math.exp(-18.0 * t) + 45.0
        buf[i] = amp * math.sin(2 * math.pi * f * t) * math.exp(-12.0 * t)
    return buf


def hat(dur_s=0.05, amp=0.25):
    n = int(dur_s * SR)
    return [amp * (random.random() * 2 - 1) * math.exp(-60.0 * (i / SR)) for i in range(n)]


def mix_into(base, src, start):
    for i, s in enumerate(src):
        j = start + i
        if 0 <= j < len(base):
            base[j] += s


def lofi():
    bpm = 75
    beat = 60.0 / bpm
    prog = [
        [midi(57), midi(60), midi(64)],  # Am
        [midi(53), midi(57), midi(60)],  # F
        [midi(48), midi(52), midi(55)],  # C
        [midi(55), midi(59), midi(62)],  # G
    ]
    total = int(len(prog) * 2 * beat * SR)
    buf = [0.0] * total
    pos = 0
    for ch in prog:
        dur = 2 * beat
        mix_into(buf, chord(ch, dur, amp=0.16), pos)
        # soft heartbeat kick on each beat
        mix_into(buf, kick(amp=0.5), pos)
        mix_into(buf, kick(amp=0.4), pos + int(beat * SR))
        pos += int(dur * SR)
    # gentle noise floor (vinyl warmth)
    for i in range(total):
        buf[i] += (random.random() * 2 - 1) * 0.006
    write_wav("music_lofi", buf)


def upbeat():
    bpm = 120
    beat = 60.0 / bpm
    chords = [
        [midi(48), midi(52), midi(55)],  # C
        [midi(55), midi(59), midi(62)],  # G
        [midi(57), midi(60), midi(64)],  # Am
        [midi(53), midi(57), midi(60)],  # F
    ]
    total = int(len(chords) * 2 * beat * SR)
    buf = [0.0] * total
    pos = 0
    for ch in chords:
        bars = 2 * beat
        # arpeggio eighth notes
        arp = [ch[0], ch[1], ch[2], ch[1]] * 2
        step = bars / len(arp)
        for k, f in enumerate(arp):
            mix_into(buf, note(f * 2, step, amp=0.16, harmonics=(1.0, 0.5, 0.25)),
                     pos + int(k * step * SR))
        # pad underneath
        mix_into(buf, chord(ch, bars, amp=0.08), pos)
        # drums
        for b in range(2):
            mix_into(buf, kick(amp=0.8), pos + int(b * beat * SR))
            mix_into(buf, hat(amp=0.18), pos + int((b + 0.5) * beat * SR))
        pos += int(bars * SR)
    write_wav("music_upbeat", buf)


def ambient():
    dur = 10.0
    n = int(dur * SR)
    buf = [0.0] * n
    voices = [midi(48), midi(55), midi(60), midi(48) + 0.6, midi(60) - 0.5]
    for f in voices:
        for i in range(n):
            t = i / SR
            swell = 0.5 - 0.5 * math.cos(2 * math.pi * t / dur)  # slow in/out
            trem = 0.85 + 0.15 * math.sin(2 * math.pi * 0.15 * t)
            buf[i] += 0.12 * swell * trem * math.sin(2 * math.pi * f * t)
    write_wav("music_ambient", buf)


def whoosh():
    dur = 0.8
    n = int(dur * SR)
    buf = [0.0] * n
    prev = 0.0
    for i in range(n):
        t = i / SR
        white = random.random() * 2 - 1
        # moving lowpass (smoothing factor sweeps) for a "swoosh"
        a = 0.02 + 0.4 * (t / dur)
        prev = prev + a * (white - prev)
        bell = math.sin(math.pi * t / dur) ** 2
        buf[i] = prev * bell * 0.9
    write_wav("sfx_whoosh", buf)


def pop():
    dur = 0.22
    n = int(dur * SR)
    buf = [0.0] * n
    for i in range(n):
        t = i / SR
        f = 600.0 * math.exp(-22.0 * t) + 180.0
        buf[i] = 0.8 * math.sin(2 * math.pi * f * t) * math.exp(-26.0 * t)
    write_wav("sfx_pop", buf)


def click():
    dur = 0.06
    n = int(dur * SR)
    buf = []
    for i in range(n):
        t = i / SR
        win = math.exp(-90.0 * t)
        tone = math.sin(2 * math.pi * 1800.0 * t)
        noise = (random.random() * 2 - 1) * 0.5
        buf.append(0.7 * win * (tone + noise))
    write_wav("sfx_click", buf)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    print("Generating royalty-free audio into", OUT)
    lofi()
    upbeat()
    ambient()
    whoosh()
    pop()
    click()
    print("done")
