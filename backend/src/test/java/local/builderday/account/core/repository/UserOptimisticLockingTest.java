package local.builderday.account.core.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import local.builderday.account.core.repository.entity.UserEntity;
import local.builderday.account.core.service.AccountModel;
import local.builderday.support.Accounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Optimistic locking on Accounts (ADR 0013) at the persistence seam, on H2: a whole-row save built from a read taken
 * before another write is rejected, not applied. The four in-place updates are covered one by one, which proves each
 * increments the version, plus the headline race: a stale enable saved after a deletion.
 */
@SpringBootTest
class UserOptimisticLockingTest {
  private static final Instant NOW = Instant.parse("2026-09-30T00:00:00Z");

  @Autowired UserRepository userRepository;
  @Autowired PlatformTransactionManager transactions;

  private UUID id;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
    id = UUID.randomUUID();
    userRepository.save(new UserEntity(id, "johndoe", "johndoe@test.example.com", "old-hash", "USER", true));
  }

  static Stream<Arguments> inPlaceUpdates() {
    return Stream.of(
        Arguments.of("failed-login increment",
            (BiConsumer<UserRepository, UUID>) (repository, id) ->
                repository.incrementFailedLoginAttempts("johndoe", NOW),
            (Consumer<UserEntity>) row -> assertThat(row.getFailedLoginAttempts()).isEqualTo(1)),
        Arguments.of("lock at threshold",
            (BiConsumer<UserRepository, UUID>) (repository, id) ->
                repository.lockWhenThresholdReached("johndoe", 0, NOW.plus(Duration.ofMinutes(15)), NOW),
            (Consumer<UserEntity>) row -> assertThat(row.getLockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)))),
        Arguments.of("successful login",
            (BiConsumer<UserRepository, UUID>) (repository, id) -> repository.recordSuccessfulLogin("johndoe", NOW),
            (Consumer<UserEntity>) row -> assertThat(row.getLastLoginAt()).isEqualTo(NOW)),
        Arguments.of("Password reset",
            (BiConsumer<UserRepository, UUID>) (repository, id) -> repository.resetPassword(id, "new-hash", NOW),
            (Consumer<UserEntity>) row -> assertThat(row.getPasswordHash()).isEqualTo("new-hash")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("inPlaceUpdates")
  void should_rejectAStaleWholeRowSave_when_anInPlaceUpdateCameBetween(String name,
      BiConsumer<UserRepository, UUID> update,
      Consumer<UserEntity> survived) {
    var stale = userRepository.findById(id).orElseThrow();

    inTransaction(() -> update.accept(userRepository, id));

    Accounts.disable(stale, NOW);
    assertThatThrownBy(() -> userRepository.save(stale)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    var current = userRepository.findById(id).orElseThrow();
    survived.accept(current);
    assertThat(current.isEnabled()).isTrue();
  }

  @Test
  void should_keepTheTombstone_when_aStaleEnableIsSavedAfterADeletion() {
    var staleForEnable = userRepository.findById(id).orElseThrow();
    Accounts.disable(staleForEnable, NOW);
    userRepository.save(staleForEnable);
    staleForEnable = userRepository.findById(id).orElseThrow();

    var forDelete = userRepository.findById(id).orElseThrow();
    userRepository.save(Accounts.markDeleted(forDelete, NOW));

    var user = AccountModel.of(staleForEnable);
    user.enable(NOW);
    AccountModel.applyTo(user, staleForEnable);
    var staleEnable = staleForEnable;
    assertThatThrownBy(() -> userRepository.save(staleEnable))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);

    var tombstone = userRepository.findById(id).orElseThrow();
    assertThat(tombstone.getDeletedAt()).isNotNull();
    assertThat(tombstone.isEnabled()).isFalse();
  }

  private void inTransaction(Runnable work) {
    new TransactionTemplate(transactions).executeWithoutResult(status -> work.run());
  }
}
