package com.clevercloud.testcontainers.warp10;

// Represents the cryptographic keys used by Warp10 for token generation.
public class Warp10CryptoKeys {
    private final String aesTokenKey;
    private final String sipHashApp;
    private final String sipHashToken;

    /**
     * @param aesTokenKey  The AES key for token encryption (64 hex chars = 32 bytes)
     * @param sipHashApp   The SipHash key for application hashing (32 hex chars = 16 bytes)
     * @param sipHashToken The SipHash key for token hashing (32 hex chars = 16 bytes)
     */
    public Warp10CryptoKeys(String aesTokenKey, String sipHashApp, String sipHashToken) {
        this.aesTokenKey = aesTokenKey;
        this.sipHashApp = sipHashApp;
        this.sipHashToken = sipHashToken;
    }

    public String getAesTokenKey() {
        return aesTokenKey;
    }

    public String getSipHashApp() {
        return sipHashApp;
    }

    public String getSipHashToken() {
        return sipHashToken;
    }

    public boolean isValid() {
        return aesTokenKey != null && aesTokenKey.length() == 64 &&
               sipHashApp != null && sipHashApp.length() == 32 &&
               sipHashToken != null && sipHashToken.length() == 32;
    }

    @Override
    public String toString() {
        return "Warp10CryptoKeys{" +
            "aesTokenKey='" + (aesTokenKey != null ? "[REDACTED:" + aesTokenKey.length() + " chars]" : "null") + '\'' +
            ", sipHashApp='" + (sipHashApp != null ? "[REDACTED:" + sipHashApp.length() + " chars]" : "null") + '\'' +
            ", sipHashToken='" + (sipHashToken != null ? "[REDACTED:" + sipHashToken.length() + " chars]" : "null") + '\'' +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Warp10CryptoKeys that = (Warp10CryptoKeys) o;
        return java.util.Objects.equals(aesTokenKey, that.aesTokenKey) &&
               java.util.Objects.equals(sipHashApp, that.sipHashApp) &&
               java.util.Objects.equals(sipHashToken, that.sipHashToken);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(aesTokenKey, sipHashApp, sipHashToken);
    }
}
