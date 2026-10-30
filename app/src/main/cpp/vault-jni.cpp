#include <jni.h>
#include <string>
#include <vector>
#include <cstdio>
#include <cstring>
#include <cstdint>
#include <unistd.h>
#include <sys/types.h>

#include "generated_secrets.h"

// --- Lightweight self-contained SHA-256 & HMAC-SHA256 Implementation ---

typedef struct {
    uint8_t data[64];
    uint32_t datalen;
    uint64_t bitlen;
    uint32_t state[8];
} SHA256_CTX;

#define DBL_INT_ADD(a,b,c) if (a > 0xffffffff - (c)) ++b; a += c;
#define ROTLEFT(a,b) (((a) << (b)) | ((a) >> (32-(b))))
#define ROTRIGHT(a,b) (((a) >> (b)) | ((a) << (32-(b))))

#define CH(x,y,z) (((x) & (y)) ^ (~(x) & (z)))
#define MAJ(x,y,z) (((x) & (y)) ^ ((x) & (z)) ^ ((y) & (z)))
#define EP0(x) (ROTRIGHT(x,2) ^ ROTRIGHT(x,13) ^ ROTRIGHT(x,22))
#define EP1(x) (ROTRIGHT(x,6) ^ ROTRIGHT(x,11) ^ ROTRIGHT(x,25))
#define SIG0(x) (ROTRIGHT(x,7) ^ ROTRIGHT(x,18) ^ ((x) >> 3))
#define SIG1(x) (ROTRIGHT(x,17) ^ ROTRIGHT(x,19) ^ ((x) >> 10))

static const uint32_t k[64] = {
    0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
    0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
    0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
    0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
    0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
    0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
    0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
    0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
};

static void sha256_transform(SHA256_CTX *ctx, const uint8_t data[]) {
    uint32_t a, b, c, d, e, f, g, h, i, j, t1, t2, m[64];
    for (i = 0, j = 0; i < 16; ++i, j += 4)
        m[i] = (data[j] << 24) | (data[j + 1] << 16) | (data[j + 2] << 8) | (data[j + 3]);
    for ( ; i < 64; ++i)
        m[i] = SIG1(m[i - 2]) + m[i - 7] + SIG0(m[i - 15]) + m[i - 16];

    a = ctx->state[0];
    b = ctx->state[1];
    c = ctx->state[2];
    d = ctx->state[3];
    e = ctx->state[4];
    f = ctx->state[5];
    g = ctx->state[6];
    h = ctx->state[7];

    for (i = 0; i < 64; ++i) {
        t1 = h + EP1(e) + CH(e,f,g) + k[i] + m[i];
        t2 = EP0(a) + MAJ(a,b,c);
        h = g;
        g = f;
        f = e;
        e = d + t1;
        d = c;
        c = b;
        b = a;
        a = t1 + t2;
    }

    ctx->state[0] += a;
    ctx->state[1] += b;
    ctx->state[2] += c;
    ctx->state[3] += d;
    ctx->state[4] += e;
    ctx->state[5] += f;
    ctx->state[6] += g;
    ctx->state[7] += h;
}

static void sha256_init(SHA256_CTX *ctx) {
    ctx->datalen = 0;
    ctx->bitlen = 0;
    ctx->state[0] = 0x6a09e667;
    ctx->state[1] = 0xbb67ae85;
    ctx->state[2] = 0x3c6ef372;
    ctx->state[3] = 0xa54ff53a;
    ctx->state[4] = 0x510e527f;
    ctx->state[5] = 0x9b05688c;
    ctx->state[6] = 0x1f83d9ab;
    ctx->state[7] = 0x5be0cd19;
}

static void sha256_update(SHA256_CTX *ctx, const uint8_t data[], size_t len) {
    for (size_t i = 0; i < len; ++i) {
        ctx->data[ctx->datalen] = data[i];
        ctx->datalen++;
        if (ctx->datalen == 64) {
            sha256_transform(ctx, ctx->data);
            ctx->bitlen += 512;
            ctx->datalen = 0;
        }
    }
}

static void sha256_final(SHA256_CTX *ctx, uint8_t hash[]) {
    uint32_t i = ctx->datalen;
    if (ctx->datalen < 56) {
        ctx->data[i++] = 0x80;
        while (i < 56) ctx->data[i++] = 0x00;
    } else {
        ctx->data[i++] = 0x80;
        while (i < 64) ctx->data[i++] = 0x00;
        sha256_transform(ctx, ctx->data);
        memset(ctx->data, 0, 56);
    }
    ctx->bitlen += ctx->datalen * 8;
    ctx->data[63] = ctx->bitlen;
    ctx->data[62] = ctx->bitlen >> 8;
    ctx->data[61] = ctx->bitlen >> 16;
    ctx->data[60] = ctx->bitlen >> 24;
    ctx->data[59] = ctx->bitlen >> 32;
    ctx->data[58] = ctx->bitlen >> 40;
    ctx->data[57] = ctx->bitlen >> 48;
    ctx->data[56] = ctx->bitlen >> 56;
    sha256_transform(ctx, ctx->data);

    for (i = 0; i < 4; ++i) {
        hash[i]      = (ctx->state[0] >> (24 - i * 8)) & 0x000000ff;
        hash[i + 4]  = (ctx->state[1] >> (24 - i * 8)) & 0x000000ff;
        hash[i + 8]  = (ctx->state[2] >> (24 - i * 8)) & 0x000000ff;
        hash[i + 12] = (ctx->state[3] >> (24 - i * 8)) & 0x000000ff;
        hash[i + 16] = (ctx->state[4] >> (24 - i * 8)) & 0x000000ff;
        hash[i + 20] = (ctx->state[5] >> (24 - i * 8)) & 0x000000ff;
        hash[i + 24] = (ctx->state[6] >> (24 - i * 8)) & 0x000000ff;
        hash[i + 28] = (ctx->state[7] >> (24 - i * 8)) & 0x000000ff;
    }
}

static void hmac_sha256(const uint8_t *key, size_t key_len, const uint8_t *data, size_t data_len, uint8_t *out_mac) {
    uint8_t k_pad[64];
    uint8_t key_buf[32];

    if (key_len > 64) {
        SHA256_CTX kctx;
        sha256_init(&kctx);
        sha256_update(&kctx, key, key_len);
        sha256_final(&kctx, key_buf);
        key = key_buf;
        key_len = 32;
    }

    // Inner pad: key XOR 0x36
    memset(k_pad, 0x36, 64);
    for (size_t i = 0; i < key_len; ++i) k_pad[i] ^= key[i];

    SHA256_CTX ictx;
    sha256_init(&ictx);
    sha256_update(&ictx, k_pad, 64);
    sha256_update(&ictx, data, data_len);
    uint8_t inner_hash[32];
    sha256_final(&ictx, inner_hash);

    // Outer pad: key XOR 0x5c
    memset(k_pad, 0x5c, 64);
    for (size_t i = 0; i < key_len; ++i) k_pad[i] ^= key[i];

    SHA256_CTX octx;
    sha256_init(&octx);
    sha256_update(&octx, k_pad, 64);
    sha256_update(&octx, inner_hash, 32);
    sha256_final(&octx, out_mac);

    memset(key_buf, 0, sizeof(key_buf));
    memset(k_pad, 0, sizeof(k_pad));
    memset(inner_hash, 0, sizeof(inner_hash));
}

// --- Anti-Frida & Tamper Check ---
static volatile int g_tampered = 0;

static void check_anti_tamper() {
    FILE *fp = fopen("/proc/self/maps", "r");
    if (fp) {
        char line[512];
        while (fgets(line, sizeof(line), fp)) {
            if (strstr(line, "frida") || strstr(line, "gum-js-loop") ||
                strstr(line, "gmain") || strstr(line, "xposed")) {
                g_tampered = 1;
                break;
            }
        }
        fclose(fp);
    }

    FILE *status = fopen("/proc/self/status", "r");
    if (status) {
        char line[256];
        while (fgets(line, sizeof(line), status)) {
            if (strncmp(line, "TracerPid:", 10) == 0) {
                int pid = 0;
                if (sscanf(line + 10, "%d", &pid) == 1 && pid != 0) {
                    g_tampered = 1;
                }
                break;
            }
        }
        fclose(status);
    }
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    check_anti_tamper();
    return JNI_VERSION_1_6;
}

// Helper: Extract APK Signing Certificate SHA-256 via JNI
static std::string get_apk_signature_sha256(JNIEnv* env, jobject context) {
    if (!context) return "";

    jclass context_cls = env->GetObjectClass(context);
    jmethodID get_pm_mid = env->GetMethodID(context_cls, "getPackageManager", "()Landroid/content/pm/PackageManager;");
    jmethodID get_pkg_name_mid = env->GetMethodID(context_cls, "getPackageName", "()Ljava/lang/String;");

    jobject pm = env->CallObjectMethod(context, get_pm_mid);
    jstring pkg_name = (jstring)env->CallObjectMethod(context, get_pkg_name_mid);

    jclass pm_cls = env->GetObjectClass(pm);
    // GET_SIGNING_CERTIFICATES = 0x08000000, fallback GET_SIGNATURES = 0x00000040
    jmethodID get_pkg_info_mid = env->GetMethodID(pm_cls, "getPackageInfo", "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;");

    jobject pkg_info = env->CallObjectMethod(pm, get_pkg_info_mid, pkg_name, 0x08000000);
    if (!pkg_info) {
        // Fallback for older Android versions
        pkg_info = env->CallObjectMethod(pm, get_pkg_info_mid, pkg_name, 0x00000040);
    }
    if (!pkg_info) return "";

    jclass pkg_info_cls = env->GetObjectClass(pkg_info);
    jobjectArray sigs_array = nullptr;

    jfieldID signing_info_fid = env->GetFieldID(pkg_info_cls, "signingInfo", "Landroid/content/pm/SigningInfo;");
    if (signing_info_fid) {
        jobject signing_info = env->GetObjectField(pkg_info, signing_info_fid);
        if (signing_info) {
            jclass signing_info_cls = env->GetObjectClass(signing_info);
            jmethodID get_signers_mid = env->GetMethodID(signing_info_cls, "getApkContentsSigners", "()[Landroid/content/pm/Signature;");
            if (get_signers_mid) {
                sigs_array = (jobjectArray)env->CallObjectMethod(signing_info, get_signers_mid);
            }
        }
    }

    if (!sigs_array) {
        jfieldID signatures_fid = env->GetFieldID(pkg_info_cls, "signatures", "[Landroid/content/pm/Signature;");
        if (signatures_fid) {
            sigs_array = (jobjectArray)env->GetObjectField(pkg_info, signatures_fid);
        }
    }

    if (!sigs_array || env->GetArrayLength(sigs_array) == 0) return "";

    jobject sig = env->GetObjectArrayElement(sigs_array, 0);
    jclass sig_cls = env->GetObjectClass(sig);
    jmethodID to_byte_array_mid = env->GetMethodID(sig_cls, "toByteArray", "()[B");
    jbyteArray cert_bytes = (jbyteArray)env->CallObjectMethod(sig, to_byte_array_mid);

    jsize len = env->GetArrayLength(cert_bytes);
    jbyte* bytes = env->GetByteArrayElements(cert_bytes, nullptr);

    uint8_t sha256_hash[32];
    SHA256_CTX ctx;
    sha256_init(&ctx);
    sha256_update(&ctx, (uint8_t*)bytes, (size_t)len);
    sha256_final(&ctx, sha256_hash);

    env->ReleaseByteArrayElements(cert_bytes, bytes, JNI_ABORT);

    char hex[65];
    for (int i = 0; i < 32; i++) {
        sprintf(hex + i * 2, "%02x", sha256_hash[i]);
    }
    hex[64] = '\0';
    return std::string(hex);
}

// Helper: Self-Poisoning HMAC Secret Decryption
// The secret is reconstructed by combining compile-time obfuscated chunks with runtime APK signature bytes.
static std::string decrypt_hmac_secret(const std::string& apk_signature) {
    if (g_tampered || apk_signature.empty()) {
        return "poisoned_tampered_null_key";
    }

    std::string secret = "";
    secret.reserve(OBF_SECRET_LEN);

    for (size_t i = 0; i < OBF_SECRET_LEN; i++) {
        uint8_t b = OBF_SECRET_BYTES[i];
        // Reverse bit-rotation
        uint8_t unrotated = (uint8_t)((b >> 3) | (b << 5));
        // Reverse rolling XOR with compile-time mask
        uint8_t mask_byte = OBF_XOR_MASK[i % OBF_MASK_LEN];
        // Self-poisoning blend with runtime APK signature
        uint8_t sig_byte = (uint8_t)apk_signature[i % apk_signature.length()];
        uint8_t original = unrotated ^ mask_byte ^ sig_byte;
        secret.push_back((char)original);
    }
    return secret;
}

// Helper: Decrypt Proxy Base URL
static std::string decrypt_proxy_url() {
    std::string url = "";
    url.reserve(OBF_URL_LEN);
    for (size_t i = 0; i < OBF_URL_LEN; i++) {
        uint8_t b = OBF_URL_BYTES[i];
        uint8_t unrotated = (uint8_t)((b >> 2) | (b << 6));
        uint8_t mask_byte = OBF_URL_MASK[i % OBF_URL_MASK_LEN];
        url.push_back((char)(unrotated ^ mask_byte));
    }
    return url;
}

// JNI Entry Point 1: Get Proxy Base URL
extern "C" JNIEXPORT jstring JNICALL
Java_com_next_tv_launcher_security_NativeSecurityBridge_getProxyBaseUrl(JNIEnv* env, jclass clazz, jobject context) {
    std::string url = decrypt_proxy_url();
    return env->NewStringUTF(url.c_str());
}

// JNI Entry Point 2: Generate Signed Request Headers JSON
extern "C" JNIEXPORT jstring JNICALL
Java_com_next_tv_launcher_security_NativeSecurityBridge_generateRequestHeadersJson(
        JNIEnv* env, jclass clazz, jobject context, jstring jquery, jlong timestamp) {

    check_anti_tamper();

    std::string apk_sig = get_apk_signature_sha256(env, context);
    std::string hmac_secret = decrypt_hmac_secret(apk_sig);

    const char* q_str = env->GetStringUTFChars(jquery, nullptr);
    std::string query = q_str ? q_str : "";
    if (q_str) env->ReleaseStringUTFChars(jquery, q_str);

    char ts_str[32];
    sprintf(ts_str, "%lld", (long long)timestamp);

    // Message format: query|timestamp|apkSignature
    std::string message = query + "|" + ts_str + "|" + apk_sig;

    uint8_t out_mac[32];
    hmac_sha256((const uint8_t*)hmac_secret.data(), hmac_secret.length(),
                (const uint8_t*)message.data(), message.length(), out_mac);

    // Immediately sanitize secret buffer from memory
    memset((void*)hmac_secret.data(), 0, hmac_secret.length());

    char mac_hex[65];
    for (int i = 0; i < 32; i++) {
        sprintf(mac_hex + i * 2, "%02x", out_mac[i]);
    }
    mac_hex[64] = '\0';

    // Build JSON response
    char json_buf[512];
    snprintf(json_buf, sizeof(json_buf),
             "{\"timestamp\":\"%s\",\"apk_signature\":\"%s\",\"signature\":\"%s\"}",
             ts_str, apk_sig.c_str(), mac_hex);

    return env->NewStringUTF(json_buf);
}

// Helper: Decrypt TMDB API Key
static std::string decrypt_tmdb_key() {
    std::string key = "";
    key.reserve(OBF_TMDB_KEY_LEN);
    for (size_t i = 0; i < OBF_TMDB_KEY_LEN; i++) {
        uint8_t b = OBF_TMDB_KEY_BYTES[i];
        uint8_t unrotated = (uint8_t)((b >> 2) | (b << 6));
        uint8_t mask_byte = OBF_TMDB_KEY_MASK[i % OBF_TMDB_KEY_MASK_LEN];
        key.push_back((char)(unrotated ^ mask_byte));
    }
    return key;
}

// Helper: Decrypt TMDB Read Access Token
static std::string decrypt_tmdb_token() {
    std::string token = "";
    token.reserve(OBF_TMDB_TOK_LEN);
    for (size_t i = 0; i < OBF_TMDB_TOK_LEN; i++) {
        uint8_t b = OBF_TMDB_TOK_BYTES[i];
        uint8_t unrotated = (uint8_t)((b >> 3) | (b << 5));
        uint8_t mask_byte = OBF_TMDB_TOK_MASK[i % OBF_TMDB_TOK_MASK_LEN];
        token.push_back((char)(unrotated ^ mask_byte));
    }
    return token;
}

// JNI Entry Point 3: Get TMDB API Key
extern "C" JNIEXPORT jstring JNICALL
Java_com_next_tv_launcher_security_NativeSecurityBridge_getTmdbApiKey(JNIEnv* env, jclass clazz, jobject context) {
    std::string key = decrypt_tmdb_key();
    return env->NewStringUTF(key.c_str());
}

// JNI Entry Point 4: Get TMDB Read Access Token
extern "C" JNIEXPORT jstring JNICALL
Java_com_next_tv_launcher_security_NativeSecurityBridge_getTmdbReadAccessToken(JNIEnv* env, jclass clazz, jobject context) {
    std::string token = decrypt_tmdb_token();
    return env->NewStringUTF(token.c_str());
}

// Helper: Decrypt Pexels API Key
static std::string decrypt_pexels_key() {
    std::string key = "";
    key.reserve(OBF_PEXELS_LEN);
    for (size_t i = 0; i < OBF_PEXELS_LEN; i++) {
        uint8_t b = OBF_PEXELS_BYTES[i];
        uint8_t unrotated = (uint8_t)((b >> 2) | (b << 6));
        uint8_t mask_byte = OBF_PEXELS_MASK[i % OBF_PEXELS_MASK_LEN];
        key.push_back((char)(unrotated ^ mask_byte));
    }
    return key;
}

// Helper: Decrypt Unsplash API Key
static std::string decrypt_unsplash_key() {
    std::string key = "";
    key.reserve(OBF_UNSPLASH_LEN);
    for (size_t i = 0; i < OBF_UNSPLASH_LEN; i++) {
        uint8_t b = OBF_UNSPLASH_BYTES[i];
        uint8_t unrotated = (uint8_t)((b >> 3) | (b << 5));
        uint8_t mask_byte = OBF_UNSPLASH_MASK[i % OBF_UNSPLASH_MASK_LEN];
        key.push_back((char)(unrotated ^ mask_byte));
    }
    return key;
}

// Helper: Decrypt Pixabay API Key
static std::string decrypt_pixabay_key() {
    std::string key = "";
    key.reserve(OBF_PIXABAY_LEN);
    for (size_t i = 0; i < OBF_PIXABAY_LEN; i++) {
        uint8_t b = OBF_PIXABAY_BYTES[i];
        uint8_t unrotated = (uint8_t)((b >> 1) | (b << 7));
        uint8_t mask_byte = OBF_PIXABAY_MASK[i % OBF_PIXABAY_MASK_LEN];
        key.push_back((char)(unrotated ^ mask_byte));
    }
    return key;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_next_tv_launcher_security_NativeSecurityBridge_getPexelsApiKey(JNIEnv* env, jclass clazz, jobject context) {
    std::string key = decrypt_pexels_key();
    return env->NewStringUTF(key.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_next_tv_launcher_security_NativeSecurityBridge_getUnsplashApiKey(JNIEnv* env, jclass clazz, jobject context) {
    std::string key = decrypt_unsplash_key();
    return env->NewStringUTF(key.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_next_tv_launcher_security_NativeSecurityBridge_getPixabayApiKey(JNIEnv* env, jclass clazz, jobject context) {
    std::string key = decrypt_pixabay_key();
    return env->NewStringUTF(key.c_str());
}
