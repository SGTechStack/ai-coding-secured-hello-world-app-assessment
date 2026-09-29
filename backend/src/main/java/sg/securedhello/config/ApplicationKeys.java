package sg.securedhello.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The application's three keys, plus any retired TOTP keys, each distinct material (ADR-022; ADR-052; ADR-054): one key, one purpose (NIST SP
 * 800-57 Part 1 Rev 5 §5.2).
 *
 * @param totpEncryption        the current TOTP seed encryption key
 * @param tombstoneHmac         the tombstone email HMAC key
 * @param logHmac               the log correlation HMAC key
 * @param retiredTotpEncryption earlier TOTP keys, each with the version it was current under (ADR-022)
 */
public record ApplicationKeys(KeyMaterial totpEncryption, KeyMaterial tombstoneHmac, KeyMaterial logHmac,
        List<KeyMaterial> retiredTotpEncryption) {

    /**
     * @throws IllegalStateException naming both properties when two keys share material, or naming the retired key
     *                               when it claims the current TOTP key version or repeats another retired version
     */
    public ApplicationKeys {
        retiredTotpEncryption = List.copyOf(retiredTotpEncryption);
        Set<Integer> versions = new HashSet<>();
        for (KeyMaterial retired : retiredTotpEncryption) {
            if (!versions.add(retired.version())) {
                throw new IllegalStateException("Startup refused: " + retired.property()
                        + " repeats a retired key version; each version holds one key");
            }
            if (retired.version().equals(totpEncryption.version())) {
                throw new IllegalStateException("Startup refused: " + retired.property()
                        + " is the current key version; a retired key needs the version it was current under");
            }
        }
        // The fields are not assigned yet inside a compact constructor, so the list is built from the parameters.
        List<KeyMaterial> keys = new ArrayList<>(List.of(totpEncryption, tombstoneHmac, logHmac));
        keys.addAll(retiredTotpEncryption);
        for (int i = 0; i < keys.size(); i++) {
            for (int j = i + 1; j < keys.size(); j++) {
                if (keys.get(i).sameMaterialAs(keys.get(j))) {
                    throw new IllegalStateException("Startup refused: " + keys.get(i).property() + " and "
                            + keys.get(j).property() + " hold the same key; each key must be distinct material");
                }
            }
        }
    }

    /** The three current keys, in declaration order, then the retired TOTP keys. */
    public List<KeyMaterial> all() {
        List<KeyMaterial> keys = new ArrayList<>(List.of(totpEncryption, tombstoneHmac, logHmac));
        keys.addAll(retiredTotpEncryption);
        return keys;
    }
}
