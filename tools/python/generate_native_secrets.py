#!/usr/bin/env python3
"""
Generate C++ header containing obfuscated secrets with bit-rotation and XOR mask.
Enforces Level 3 Self-Poisoning Native Vault & Level 4 BFF Proxy architecture.
"""
import os
import sys

def parse_env_file(filepath):
    env_vars = {}
    if not os.path.exists(filepath):
        return env_vars
    with open(filepath, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            if '=' in line:
                key, val = line.split('=', 1)
                env_vars[key.strip()] = val.strip()
    return env_vars

def main():
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
    env_path = os.path.join(repo_root, '.env')
    env_example = os.path.join(repo_root, '.env.example')

    env = parse_env_file(env_path)
    if not env:
        env = parse_env_file(env_example)

    url = env.get('WORKER_PROXY_URL', '')
    secret = env.get('APP_HMAC_SECRET', '')
    apk_sig = env.get('ALLOWED_APK_SHA256', '').lower().strip()
    tmdb_key = env.get('TMDB_API_KEY', '')
    tmdb_token = env.get('TMDB_READ_ACCESS_TOKEN', '')

    pexels_key = env.get('PEXELS_API_KEY', '')
    unsplash_key = env.get('UNSPLASH_API_KEY', '')
    pixabay_key = env.get('PIXABAY_API_KEY', '')

    url_mask = [0x5A, 0x3C, 0xA5, 0xF0, 0x1F, 0x8E, 0x27, 0x4D]
    enc_url = []
    for i, c in enumerate(url):
        val = (ord(c) ^ url_mask[i % len(url_mask)]) & 0xFF
        rot = ((val << 2) | (val >> 6)) & 0xFF
        enc_url.append(rot)

    xor_mask = [0x73, 0x19, 0x42, 0xFE, 0x8A, 0x31, 0x95, 0xC6, 0x0B, 0x5D, 0x2E, 0x64, 0x88, 0xAF, 0x13, 0xD7]
    enc_sec = []
    for i, c in enumerate(secret):
        sig_b = ord(apk_sig[i % len(apk_sig)]) if apk_sig else 0x41
        val = (ord(c) ^ xor_mask[i % len(xor_mask)] ^ sig_b) & 0xFF
        rot = ((val << 3) | (val >> 5)) & 0xFF
        enc_sec.append(rot)

    tmdb_key_mask = [0x4A, 0x9B, 0x33, 0xD1, 0x7E, 0x24, 0x8C, 0x15]
    enc_tmdb_key = []
    for i, c in enumerate(tmdb_key):
        val = (ord(c) ^ tmdb_key_mask[i % len(tmdb_key_mask)]) & 0xFF
        rot = ((val << 2) | (val >> 6)) & 0xFF
        enc_tmdb_key.append(rot)

    tmdb_tok_mask = [0x62, 0xE7, 0x1A, 0x88, 0x3F, 0x5C, 0xB4, 0x09, 0x77, 0x2D, 0x91, 0x43]
    enc_tmdb_tok = []
    for i, c in enumerate(tmdb_token):
        val = (ord(c) ^ tmdb_tok_mask[i % len(tmdb_tok_mask)]) & 0xFF
        rot = ((val << 3) | (val >> 5)) & 0xFF
        enc_tmdb_tok.append(rot)

    pexels_mask = [0x3B, 0x82, 0xF6, 0x14, 0x99, 0x4D, 0x7E, 0xC3, 0x2A]
    enc_pexels = []
    for i, c in enumerate(pexels_key):
        val = (ord(c) ^ pexels_mask[i % len(pexels_mask)]) & 0xFF
        rot = ((val << 2) | (val >> 6)) & 0xFF
        enc_pexels.append(rot)

    unsplash_mask = [0x8A, 0x29, 0xD4, 0x55, 0x0F, 0x6C, 0xB7, 0x3E]
    enc_unsplash = []
    for i, c in enumerate(unsplash_key):
        val = (ord(c) ^ unsplash_mask[i % len(unsplash_mask)]) & 0xFF
        rot = ((val << 3) | (val >> 5)) & 0xFF
        enc_unsplash.append(rot)

    pixabay_mask = [0x5C, 0x11, 0xA9, 0x6E, 0x84, 0x2F, 0xD7, 0x48, 0x93]
    enc_pixabay = []
    for i, c in enumerate(pixabay_key):
        val = (ord(c) ^ pixabay_mask[i % len(pixabay_mask)]) & 0xFF
        rot = ((val << 1) | (val >> 7)) & 0xFF
        enc_pixabay.append(rot)

    header_dir = os.path.join(repo_root, 'app', 'src', 'main', 'cpp')
    os.makedirs(header_dir, exist_ok=True)
    header_path = os.path.join(header_dir, 'generated_secrets.h')

    with open(header_path, 'w', encoding='utf-8') as f:
        f.write('// Auto-generated security header. Do not commit or edit directly.\n')
        f.write('#ifndef GENERATED_SECRETS_H\n')
        f.write('#define GENERATED_SECRETS_H\n\n')
        f.write('#include <cstdint>\n#include <cstddef>\n\n')

        # URL
        f.write(f'static const size_t OBF_URL_LEN = {len(enc_url)};\n')
        f.write(f'static const size_t OBF_URL_MASK_LEN = {len(url_mask)};\n')
        f.write('static const uint8_t OBF_URL_BYTES[] = {' + ', '.join(f'0x{b:02X}' for b in enc_url) + '};\n')
        f.write('static const uint8_t OBF_URL_MASK[] = {' + ', '.join(f'0x{b:02X}' for b in url_mask) + '};\n\n')

        # HMAC Secret
        f.write(f'static const size_t OBF_SECRET_LEN = {len(enc_sec)};\n')
        f.write(f'static const size_t OBF_MASK_LEN = {len(xor_mask)};\n')
        f.write('static const uint8_t OBF_SECRET_BYTES[] = {' + ', '.join(f'0x{b:02X}' for b in enc_sec) + '};\n')
        f.write('static const uint8_t OBF_XOR_MASK[] = {' + ', '.join(f'0x{b:02X}' for b in xor_mask) + '};\n\n')

        # Expected APK SHA-256 for self-poisoning
        f.write(f'static const char* const EXPECTED_APK_SHA256 = "{apk_sig}";\n\n')

        # TMDB Key
        f.write(f'static const size_t OBF_TMDB_KEY_LEN = {len(enc_tmdb_key)};\n')
        f.write(f'static const size_t OBF_TMDB_KEY_MASK_LEN = {len(tmdb_key_mask)};\n')
        f.write('static const uint8_t OBF_TMDB_KEY_BYTES[] = {' + ', '.join(f'0x{b:02X}' for b in enc_tmdb_key) + '};\n')
        f.write('static const uint8_t OBF_TMDB_KEY_MASK[] = {' + ', '.join(f'0x{b:02X}' for b in tmdb_key_mask) + '};\n\n')

        # TMDB Token
        f.write(f'static const size_t OBF_TMDB_TOK_LEN = {len(enc_tmdb_tok)};\n')
        f.write(f'static const size_t OBF_TMDB_TOK_MASK_LEN = {len(tmdb_tok_mask)};\n')
        f.write('static const uint8_t OBF_TMDB_TOK_BYTES[] = {' + ', '.join(f'0x{b:02X}' for b in enc_tmdb_tok) + '};\n')
        f.write('static const uint8_t OBF_TMDB_TOK_MASK[] = {' + ', '.join(f'0x{b:02X}' for b in tmdb_tok_mask) + '};\n\n')

        # Pexels
        f.write(f'static const size_t OBF_PEXELS_LEN = {len(enc_pexels)};\n')
        f.write(f'static const size_t OBF_PEXELS_MASK_LEN = {len(pexels_mask)};\n')
        f.write('static const uint8_t OBF_PEXELS_BYTES[] = {' + ', '.join(f'0x{b:02X}' for b in enc_pexels) + '};\n')
        f.write('static const uint8_t OBF_PEXELS_MASK[] = {' + ', '.join(f'0x{b:02X}' for b in pexels_mask) + '};\n\n')

        # Unsplash
        f.write(f'static const size_t OBF_UNSPLASH_LEN = {len(enc_unsplash)};\n')
        f.write(f'static const size_t OBF_UNSPLASH_MASK_LEN = {len(unsplash_mask)};\n')
        f.write('static const uint8_t OBF_UNSPLASH_BYTES[] = {' + ', '.join(f'0x{b:02X}' for b in enc_unsplash) + '};\n')
        f.write('static const uint8_t OBF_UNSPLASH_MASK[] = {' + ', '.join(f'0x{b:02X}' for b in unsplash_mask) + '};\n\n')

        # Pixabay
        f.write(f'static const size_t OBF_PIXABAY_LEN = {len(enc_pixabay)};\n')
        f.write(f'static const size_t OBF_PIXABAY_MASK_LEN = {len(pixabay_mask)};\n')
        f.write('static const uint8_t OBF_PIXABAY_BYTES[] = {' + ', '.join(f'0x{b:02X}' for b in enc_pixabay) + '};\n')
        f.write('static const uint8_t OBF_PIXABAY_MASK[] = {' + ', '.join(f'0x{b:02X}' for b in pixabay_mask) + '};\n\n')

        f.write('#endif // GENERATED_SECRETS_H\n')

    print(f'Generated native secrets header at: {header_path}')

if __name__ == '__main__':
    main()
