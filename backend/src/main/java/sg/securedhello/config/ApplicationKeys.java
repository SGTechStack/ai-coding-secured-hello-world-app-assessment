package sg.securedhello.config;

import java.util.List;

/**
 * The application's three keys, each distinct material (ADR-022; ADR-052; ADR-054): one key, one purpose (NIST SP
 * 800-57 Part 1 Rev 5 §5.2).
 *
 * @param totpEncryption the TOTP seed encryption key
 * @param tombstoneHmac  the tombstone email HMAC key
 * @param logHmac        the log correlation HMAC key
 */
public record ApplicationKeys(KeyMaterial totpEncryption, KeyMaterial tombstoneHmac, KeyMaterial logHmac) {

    /** @throws IllegalStateException naming both properties when two keys share material */
    public ApplicationKeys {
        List<KeyMaterial> keys = List.of(totpEncryption, tombstoneHmac, logHmac);
        for (int i = 0; i < keys.size(); i++) {
            for (int j = i + 1; j < keys.size(); j++) {
                if (keys.get(i).sameMaterialAs(keys.get(j))) {
                    throw new IllegalStateException("Startup refused: " + keys.get(i).property() + " and "
                            + keys.get(j).property() + " hold the same key; each key must be distinct material");
                }
            }
        }
    }

    /** The keys, in declaration order. */
    public List<KeyMaterial> all() {
        return List.of(totpEncryption, tombstoneHmac, logHmac);
    }
}
