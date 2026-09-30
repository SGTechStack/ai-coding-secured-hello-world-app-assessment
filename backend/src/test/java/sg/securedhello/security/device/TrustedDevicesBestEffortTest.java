package sg.securedhello.security.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import sg.securedhello.user.TrustedDeviceRepository;
import sg.securedhello.user.UserAccountRepository;

/**
 * {@link TrustedDevices#trust} is best effort (ADR-075): it runs after a sign-in or a factor grant has already
 * succeeded, so a failure to read the presented claim or to open the issuing transaction sets no cookie and throws
 * nothing.
 */
class TrustedDevicesBestEffortTest {

    private final DeviceCookieCodec codec = new DeviceCookieCodec(new byte[32]);
    private final TrustedDeviceRepository devices = mock(TrustedDeviceRepository.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final TrustedDevices trusted = new TrustedDevices(codec, devices, mock(UserAccountRepository.class),
            transactions, Clock.systemUTC(), Duration.ofDays(30), false);

    @Test
    void aFailedClaimLookupSetsNoCookieAndThrowsNothing() {
        when(devices.findById(any())).thenThrow(new QueryTimeoutException("lookup"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(TrustedDevices.DEV_COOKIE, codec.sign(UUID.randomUUID())));
        MockHttpServletResponse response = new MockHttpServletResponse();

        trusted.trust(UUID.randomUUID(), request, response);

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void aTransactionThatCannotOpenSetsNoCookieAndThrowsNothing() {
        when(transactions.execute(any())).thenThrow(new CannotCreateTransactionException("pool"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        trusted.trust(UUID.randomUUID(), new MockHttpServletRequest(), response);

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }
}
