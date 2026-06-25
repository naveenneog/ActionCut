"""Generates test assets that reproduce the Media3 'mixed audio tracks' export error:
a short MP4 *with* an AAC audio track, plus a PNG image (no audio). A sequence of
[image, video-with-audio] triggers the error unless experimentalSetForceAudioTrack(true).
Outputs into core/media/src/androidTest/assets/."""
import os
import math
import numpy as np
import av
from PIL import Image

OUT = os.path.join("core", "media", "src", "androidTest", "assets")
os.makedirs(OUT, exist_ok=True)

# --- video + audio MP4 ---
path = os.path.join(OUT, "clip_audio.mp4")
container = av.open(path, mode="w")
vstream = container.add_stream("libx264", rate=24)
vstream.width, vstream.height, vstream.pix_fmt = 640, 480, "yuv420p"
astream = container.add_stream("aac", rate=44100)
astream.layout = "stereo"

fps, dur = 24, 2.0
nframes = int(fps * dur)
for i in range(nframes):
    img = np.zeros((480, 640, 3), dtype=np.uint8)
    img[:, :, 0] = (i * 9) % 255
    img[:, :, 1] = 90
    img[:, :, 2] = (255 - i * 5) % 255
    frame = av.VideoFrame.from_ndarray(img, format="rgb24")
    for packet in vstream.encode(frame):
        container.mux(packet)
for packet in vstream.encode():
    container.mux(packet)

sr = 44100
chunk = 1024
total = int(sr * dur)
pts = 0
for start in range(0, total, chunk):
    n = min(chunk, total - start)
    t = (np.arange(start, start + n) / sr)
    tone = (0.2 * np.sin(2 * math.pi * 440 * t)).astype(np.float32)
    samples = np.stack([tone, tone])  # stereo planar (fltp)
    aframe = av.AudioFrame.from_ndarray(samples, format="fltp", layout="stereo")
    aframe.sample_rate = sr
    aframe.pts = pts
    pts += n
    for packet in astream.encode(aframe):
        container.mux(packet)
for packet in astream.encode():
    container.mux(packet)
container.close()
print("wrote", path, os.path.getsize(path), "bytes")

# --- still image (no audio) ---
img_path = os.path.join(OUT, "still.png")
im = Image.new("RGB", (640, 480))
px = im.load()
for y in range(480):
    for x in range(640):
        px[x, y] = ((x // 3) % 255, (y // 2) % 255, 140)
im.save(img_path)
print("wrote", img_path, os.path.getsize(img_path), "bytes")
