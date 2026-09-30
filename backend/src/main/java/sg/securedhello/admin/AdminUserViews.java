package sg.securedhello.admin;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import sg.securedhello.mfa.TotpUserDetails;
import sg.securedhello.mfa.TotpUserDetailsRepository;
import sg.securedhello.user.PasswordLockoutState;
import sg.securedhello.user.TrustedDevice;
import sg.securedhello.user.TrustedDeviceRepository;
import sg.securedhello.user.UserAccount;

/**
 * Builds the admin surface's {@link AdminUserView}s, each with its {@link SignInStatus} as it stands on the shared
 * clock (ADR-075). A list reads the locked devices and the factors once, not once per account.
 */
@Component
class AdminUserViews {

    private final TrustedDeviceRepository devices;
    private final TotpUserDetailsRepository factors;
    private final Clock clock;

    AdminUserViews(TrustedDeviceRepository devices, TotpUserDetailsRepository factors, Clock clock) {
        this.devices = devices;
        this.factors = factors;
        this.clock = clock;
    }

    /** The view of one account. */
    AdminUserView of(UserAccount account) {
        Instant now = clock.instant();
        List<TrustedDevice> locked = devices.findByUserIdOrderByCreatedAtDesc(account.getId()).stream()
                .filter(device -> device.getLockState().lockedAt(now)).toList();
        return AdminUserView.of(account, status(account, locked, factors.findById(account.getId()), now));
    }

    /** The views of {@code accounts}, in their order. */
    List<AdminUserView> of(List<UserAccount> accounts) {
        Instant now = clock.instant();
        Map<UUID, List<TrustedDevice>> locked = devices.findByLockedUntilAfter(now).stream()
                .collect(Collectors.groupingBy(TrustedDevice::getUserId));
        Map<UUID, TotpUserDetails> factorsById = factors.findAll().stream()
                .collect(Collectors.toMap(TotpUserDetails::getUserId, Function.identity()));
        return accounts.stream()
                .map(account -> AdminUserView.of(account, status(account,
                        locked.getOrDefault(account.getId(), List.of()),
                        Optional.ofNullable(factorsById.get(account.getId())), now)))
                .toList();
    }

    private static SignInStatus status(UserAccount account, List<TrustedDevice> lockedDevices,
            Optional<TotpUserDetails> factor, Instant now) {
        PasswordLockoutState lockout = account.getLockoutState();
        return new SignInStatus(
                lockout.lockedAt(now) ? lockout.lockedUntil() : null,
                lockout.consecutiveFailuresSinceSuccess(),
                lockedDevices.size(),
                lockedDevices.stream().map(device -> device.getLockState().lockedUntil())
                        .min(Comparator.naturalOrder()).orElse(null),
                lockout.passwordDisabledAt(),
                factor.flatMap(enrolled -> enrolled.lockedUntil(now)).orElse(null),
                factor.map(TotpUserDetails::isDisabled).orElse(false));
    }
}
