# NextTV - Native Header Generator for NDK Build (Linux & macOS / Pure Bash & AWK)
# Zero external dependencies. Zero Python requirement.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${TOOLS_DIR}/.." && pwd)"

ENV_FILE="${REPO_ROOT}/.env"
if [[ ! -f "${ENV_FILE}" ]]; then
    ENV_FILE="${REPO_ROOT}/.env.example"
fi

# Fallback to PowerShell generator if on Windows/MSYS/Cygwin or pwsh is available
if command -v pwsh >/dev/null 2>&1; then
    pwsh -NoProfile -ExecutionPolicy Bypass -File "${TOOLS_DIR}/ps/generate_header.ps1" "$@"
    exit $?
fi

# Pure POSIX / Bash / AWK Header Vault Engine
awk -v envFile="${ENV_FILE}" -v outPath="${REPO_ROOT}/app/src/main/cpp/generated_secrets.h" '
BEGIN {
    while ((getline line < envFile) > 0) {
        gsub(/^[ \t]+|[ \t\r\n]+$/, "", line)
        if (line !~ /^#/ && index(line, "=") > 0) {
            split(line, parts, "=")
            k = parts[1]
            gsub(/^[ \t]+|[ \t]+$/, "", k)
            v = substr(line, index(line, "=") + 1)
            gsub(/^[ \t]+|[ \t]+$/, "", v)
            gsub(/^["'\'']|["'\'']$/, "", v)
            envMap[k] = v
        }
    }
    close(envFile)

    url = envMap["WORKER_PROXY_URL"]
    sec = envMap["APP_HMAC_SECRET"]
    sig = tolower(envMap["ALLOWED_APK_SHA256"])
    tmdbK = envMap["TMDB_API_KEY"]
    tmdbT = envMap["TMDB_READ_ACCESS_TOKEN"]
    pexK = envMap["PEXELS_API_KEY"]
    unsK = envMap["UNSPLASH_API_KEY"]
    pixK = envMap["PIXABAY_API_KEY"]
    braveK = envMap["BRAVE_SEARCH_API_KEY"]
    smK = envMap["SPORTMONKS_API_KEY"]
    fdK = envMap["FOOTBALL_DATA_ORG_KEY"]
    afK = envMap["API_FOOTBALL_KEY"]
    saK = envMap["SPORTAPI_KEY"]

    # Pre-defined masks for pure AWK execution
    uM = "90,60,165,240,31,142,39,77"
    sM = "115,25,66,254,138,49,149,198,11,93,46,100,136,175,19,215"
    tkM = "74,155,51,209,126,36,140,21"
    ttM = "98,231,26,136,63,92,180,9,119,45,145,67"
    pM = "59,130,246,20,153,77,126,195,42"
    unM = "138,41,212,85,15,108,183,62"
    pxM = "92,17,169,110,132,47,215,72,147"
    bvM = "79,162,37,218,105,44,191,83,12,170,95,61"
    spM = "101,213,49,178,87,34,143,204,18,99,222,55"

    split(uM, uMask, ",")
    split(sM, sMask, ",")
    split(tkM, tkMask, ",")
    split(ttM, ttMask, ",")
    split(pM, pMask, ",")
    split(unM, unMask, ",")
    split(pxM, pxMask, ",")
    split(bvM, bvMask, ",")
    split(spM, spMask, ",")

    print "// Auto-generated security header. Do not commit or edit directly." > outPath
    print "#ifndef GENERATED_SECRETS_H" >> outPath
    print "#define GENERATED_SECRETS_H" >> outPath
    print "" >> outPath
    print "#include <cstdint>" >> outPath
    print "#include <cstddef>" >> outPath
    print "" >> outPath

    emitBlock(outPath, "OBF_URL", url, uMask, 2, 6, "")
    emitBlock(outPath, "OBF_SECRET", sec, sMask, 3, 5, sig)
    print "static const char* const EXPECTED_APK_SHA256 = \"" sig "\";" >> outPath
    print "" >> outPath
    emitBlock(outPath, "OBF_TMDB_KEY", tmdbK, tkMask, 2, 6, "")
    emitBlock(outPath, "OBF_TMDB_TOK", tmdbT, ttMask, 3, 5, "")
    emitBlock(outPath, "OBF_PEXELS", pexK, pMask, 2, 6, "")
    emitBlock(outPath, "OBF_UNSPLASH", unsK, unMask, 3, 5, "")
    emitBlock(outPath, "OBF_PIXABAY", pixK, pxMask, 1, 7, "")
    emitBlock(outPath, "OBF_BRAVE_KEY", braveK, bvMask, 2, 6, "")
    emitBlock(outPath, "OBF_SPORTMONKS_KEY", smK, spMask, 3, 5, "")
    emitBlock(outPath, "OBF_FOOTBALL_DATA_KEY", fdK, spMask, 2, 6, "")
    emitBlock(outPath, "OBF_API_FOOTBALL_KEY", afK, spMask, 3, 5, "")
    emitBlock(outPath, "OBF_SPORTAPI_KEY", saK, spMask, 1, 7, "")

    print "#endif // GENERATED_SECRETS_H" >> outPath
    close(outPath)
    print "[+] Generated native secrets header at: " outPath
}

function bit_xor(a, b,   res, bit, p) {
    res = 0; p = 1
    for (bit = 0; bit < 8; bit++) {
        if ((int(a / p) % 2) != (int(b / p) % 2)) res += p
        p *= 2
    }
    return res
}

function emitBlock(file, prefix, text, mask, lShift, rShift, sig,   len, mLen, outBytes, outMask, i, c, k, s, v, r, sigLen) {
    len = length(text)
    mLen = length(mask)
    sigLen = length(sig)

    outBytes = ""
    if (len == 0) {
        outBytes = "0x00"
    } else {
        for (i = 1; i <= len; i++) {
            c = _ord(substr(text, i, 1))
            k = mask[((i - 1) % mLen) + 1] + 0
            s = (sigLen > 0) ? _ord(substr(sig, ((i - 1) % sigLen) + 1, 1)) : 0
            v = bit_xor(bit_xor(c, k), s) % 256
            r = ((v * (2 ^ lShift)) + int(v / (2 ^ rShift))) % 256
            outBytes = (i > 1 ? outBytes ", " : "") sprintf("0x%02X", r)
        }
    }

    outMask = ""
    for (i = 1; i <= mLen; i++) {
        outMask = (i > 1 ? outMask ", " : "") sprintf("0x%02X", mask[i] + 0)
    }

    print "static const size_t " prefix "_LEN = " len ";" >> file
    print "static const size_t " prefix "_MASK_LEN = " mLen ";" >> file
    print "static const uint8_t " prefix "_BYTES[] = {" outBytes "};" >> file
    print "static const uint8_t " prefix "_MASK[] = {" outMask "};" >> file
    print "" >> file
}

function _ord(char,   i) {
    for (i = 0; i < 256; i++) {
        if (sprintf("%c", i) == char) return i
    }
    return 0
}
'
