package sg.securedhello.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.security.lockout.LockoutProperties;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxBudgetTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SignedIn;

/**
 * The lockout-cardinality axis on {@code application.yml}'s values (ADR-015): a source that has driven {@code k}
 * distinct accounts into lockout gets 429 for any other username, members still proceed, membership is recorded only
 * at the lockout transition, and each member's hour runs from its first lockout. Each test works from its own source.
 */
class LockoutCardinalityTest extends CtxBudgetTest {

    private static final String WRONG = "not-the-password-at-all";

    @Autowired
    private LockoutCardinalityProperties cardinality;

    @Autowired
    private LockoutProperties lockout;

    @Autowired
    private JdbcTemplate jdbc;

    private Accounts accounts;
    private String source;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
        source = nextSource();
    }

    private ResultActions login(CsrfSession session, String username, String password) throws Exception {
        return mockMvc.perform(post("/api/login").with(session.inHeader()).with(request -> {
            request.setRemoteAddr(source);
            return request;
        }).contentType(MediaType.APPLICATION_JSON).content(SignedIn.credentials(username, password)));
    }

    private ResultActions login(String username, String password) throws Exception {
        return login(CsrfSession.bootstrap(mockMvc, source), username, password);
    }

    /** {@code times} wrong passwords for {@code account} from this test's source, all answered 401. */
    private void fail(Account account, int times) throws Exception {
        CsrfSession session = CsrfSession.bootstrap(mockMvc, source);
        for (int i = 0; i < times; i++) {
            login(session, account.username(), WRONG).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
        }
    }

    /** Drives {@code account} into lockout from this test's source. */
    private void lock(Account account) throws Exception {
        fail(account, lockout.threshold());
        assertThat(accounts.lockoutState(account).lockedAt(clock.instant())).as("locked").isTrue();
    }

    private List<Account> lockDistinct(int count) throws Exception {
        List<Account> locked = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Account account = accounts.user();
            lock(account);
            locked.add(account);
        }
        return locked;
    }

    @Test
    @Proves("T-RL-006")
    void aSourceThatLockedKAccountsGets429ForAnyOtherUsernameBeforeBcrypt() throws Exception {
        lockDistinct(cardinality.k());
        Account bystander = accounts.user();
        clearInvocations(passwordEncoder, userAccounts);

        login(bystander.username(), bystander.password())
                .andExpect(problem(ErrorCode.TOO_MANY_REQUESTS))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, Long.toString(cardinality.window().toSeconds())));
        login(Accounts.unknownUsername(), WRONG).andExpect(problem(ErrorCode.TOO_MANY_REQUESTS));

        verify(passwordEncoder, never()).matches(any(), any());
        verify(userAccounts, never()).findByUsername(any());
        assertThat(accounts.lockoutState(bystander).consecutiveFailuresSinceSuccess()).isZero();

        // Another source is not affected.
        source = nextSource();
        login(bystander.username(), bystander.password()).andExpect(result ->
                assertThat(result.getResponse().getStatus()).isEqualTo(200));
    }

    @Test
    @Proves("T-RL-007")
    void attemptingManyAccountsWithoutLockingAnyIsNeverRefused() throws Exception {
        for (int i = 0; i < cardinality.k() + 2; i++) {
            fail(accounts.user(), lockout.threshold() - 1);
        }
        Account next = accounts.user();
        login(next.username(), next.password()).andExpect(result ->
                assertThat(result.getResponse().getStatus()).isEqualTo(200));
    }

    @Test
    @Proves("T-RL-030")
    void reLockingAMemberDoesNotCountAndMembersProceedWhenTheSetIsFull() throws Exception {
        Account member = accounts.user();
        lock(member);
        clock.advance(lockout.ladder().rungs().getFirst());
        lock(member);
        List<Account> others = lockDistinct(cardinality.k() - 2);

        Account last = accounts.user();
        // The member was locked twice but counts once, so the set is one short of k and a newcomer is admitted.
        login(last.username(), WRONG).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
        lock(last);

        login(Accounts.unknownUsername(), WRONG).andExpect(problem(ErrorCode.TOO_MANY_REQUESTS));
        // A locked member still reaches the provider: 401, not 429.
        login(member.username(), member.password()).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));

        clock.advance(lockout.ladder().rungs().getFirst());
        login(Accounts.unknownUsername(), WRONG).andExpect(problem(ErrorCode.TOO_MANY_REQUESTS));
        for (Account account : List.of(member, others.getFirst(), last)) {
            login(account.username(), account.password()).andExpect(result ->
                    assertThat(result.getResponse().getStatus()).as("a member proceeds").isEqualTo(200));
        }
    }

    @Test
    @Proves("T-RL-031")
    void aMembersHourRunsFromItsFirstLockoutAndReLockingDoesNotExtendIt() throws Exception {
        Account first = accounts.user();
        lock(first);
        clock.advance(Duration.ofMinutes(30));
        lockDistinct(cardinality.k() - 1);

        clock.advance(Duration.ofMinutes(20));
        lock(first);
        String stranger = Accounts.unknownUsername();
        login(stranger, WRONG).andExpect(problem(ErrorCode.TOO_MANY_REQUESTS))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER,
                        Long.toString(cardinality.window().minus(Duration.ofMinutes(50)).toSeconds())));

        clock.advance(Duration.ofMinutes(10).minusSeconds(1));
        login(stranger, WRONG).andExpect(problem(ErrorCode.TOO_MANY_REQUESTS));

        clock.advance(Duration.ofSeconds(1));
        login(stranger, WRONG).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
    }
}
