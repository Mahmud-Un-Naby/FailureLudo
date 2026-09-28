"""Prepare the retained CC0 hiss preview for offline capture playback.
Run: python3 design/audio/prepare_snake_hiss.py
Uses the same development-only NumPy/SciPy/libsndfile tools as the wooden options.
"""
from pathlib import Path
import hashlib
import json
import wave

import numpy as np
from scipy.signal import butter, resample_poly, sosfilt
from prepare_wooden_options import read_audio

ROOT = Path(__file__).resolve().parents[2]
SOURCE_DIR = Path(__file__).parent / 'sources/snake_hiss'
RATE = 22050


def prepare():
    entry = json.loads((SOURCE_DIR / 'manifest.json').read_text())
    source = SOURCE_DIR / entry['source_file']
    assert hashlib.sha256(source.read_bytes()).hexdigest() == entry['source_sha256']
    rate, samples = read_audio(source)
    start, end = entry['trim_seconds']
    samples = samples[round(start * rate):round(end * rate)]
    samples = sosfilt(butter(2, [200, 6500], 'bandpass', fs=rate, output='sos'), samples)
    samples = resample_poly(samples, RATE, rate)[:round((end - start) * RATE)]
    samples -= samples.mean()
    attack, release = round(.004 * RATE), round(.040 * RATE)
    samples[:attack] *= np.linspace(0, 1, attack)
    samples[-release:] *= np.linspace(1, 0, release)
    samples *= .60 / max(np.max(np.abs(samples)), .000001)
    result = np.zeros(round(entry['duration_seconds'] * RATE))
    assert len(samples) <= len(result)
    result[:len(samples)] = samples
    pcm = np.rint(result * 32767).astype('<i2')
    with wave.open(str(ROOT / 'app/src/main/res/raw' / entry['output_file']), 'wb') as output:
        output.setparams((1, 2, RATE, len(pcm), 'NONE', 'not compressed'))
        output.writeframes(pcm.tobytes())
    print(entry['output_file'], f'{len(pcm) / RATE:.3f}s', f'peak={max(abs(result)):.3f}')


if __name__ == '__main__':
    prepare()
