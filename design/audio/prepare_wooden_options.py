"""Prepare the CC0 recordings documented in sources/wooden_options/manifest.json.
Run: python3 design/audio/prepare_wooden_options.py
Requires NumPy, SciPy and system libsndfile for offline asset preparation only.
"""
from pathlib import Path
import ctypes as ct
import ctypes.util
import hashlib
import json
import wave

import numpy as np
from scipy.signal import butter, resample_poly, sosfilt

ROOT = Path(__file__).resolve().parents[2]
SOURCES = Path(__file__).parent / 'sources/wooden_options'
OUTPUT = ROOT / 'app/src/main/res/raw'
RATE = 22050


class SoundInfo(ct.Structure):
    _fields_ = [('frames', ct.c_longlong), ('samplerate', ct.c_int),
                ('channels', ct.c_int), ('format', ct.c_int),
                ('sections', ct.c_int), ('seekable', ct.c_int)]


def read_audio(path):
    library = ctypes.util.find_library('sndfile')
    if not library:
        raise RuntimeError('Install libsndfile to decode the retained source recordings')
    lib = ct.CDLL(library)
    lib.sf_open.argtypes = [ct.c_char_p, ct.c_int, ct.POINTER(SoundInfo)]
    lib.sf_open.restype = ct.c_void_p
    lib.sf_readf_float.argtypes = [ct.c_void_p, ct.POINTER(ct.c_float), ct.c_longlong]
    lib.sf_readf_float.restype = ct.c_longlong
    lib.sf_close.argtypes = [ct.c_void_p]
    info = SoundInfo()
    handle = lib.sf_open(str(path).encode(), 0x10, ct.byref(info))
    if not handle:
        raise RuntimeError(f'Cannot decode {path}')
    try:
        samples = np.empty((info.frames, info.channels), dtype=np.float32)
        count = lib.sf_readf_float(handle, samples.ctypes.data_as(ct.POINTER(ct.c_float)), info.frames)
        if count != info.frames:
            raise RuntimeError(f'Incomplete recording: {path}')
        return info.samplerate, samples.mean(axis=1).astype(np.float64)
    finally:
        lib.sf_close(handle)


def prepare(entry):
    source = SOURCES / entry['source_file']
    assert hashlib.sha256(source.read_bytes()).hexdigest() == entry['source_sha256']
    rate, samples = read_audio(source)
    start, end = entry['trim_seconds']
    samples = samples[round(start * rate):round(end * rate)]
    # Remove room rumble and soften high-frequency clicks, retaining the real wood contacts.
    samples = sosfilt(butter(2, 80, 'highpass', fs=rate, output='sos'), samples)
    samples = sosfilt(butter(2, entry['lowpass_hz'], 'lowpass', fs=rate, output='sos'), samples)
    samples = resample_poly(samples, RATE, rate)[:round((end - start) * RATE)]
    samples -= samples.mean()
    attack, release = round(.001 * RATE), round(.015 * RATE)
    samples[:attack] *= np.linspace(0, 1, attack)
    samples[-release:] *= np.linspace(1, 0, release)
    samples *= .65 / max(np.max(np.abs(samples)), .000001)
    result = np.zeros(round(entry['duration_seconds'] * RATE))
    offset = round(entry['lead_seconds'] * RATE)
    assert offset + len(samples) <= len(result)
    result[offset:offset + len(samples)] = samples
    pcm = np.rint(result * 32767).astype('<i2')
    with wave.open(str(OUTPUT / entry['output_file']), 'wb') as output:
        output.setparams((1, 2, RATE, len(pcm), 'NONE', 'not compressed'))
        output.writeframes(pcm.tobytes())
    print(entry['output_file'], f'{len(pcm) / RATE:.3f}s', f'peak={max(abs(result)):.3f}')


if __name__ == '__main__':
    for entry in json.loads((SOURCES / 'manifest.json').read_text())['sounds']:
        prepare(entry)
