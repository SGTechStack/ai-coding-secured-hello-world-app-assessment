package local.builderday.account.administration.service;

import java.time.Clock;
import java.time.Instant;
import local.builderday.account.administration.model.ListedAccount;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The User list: every Account, tombstones included, newest first. */
@Service
public class UserListService {
  // The id tiebreak keeps pages stable when several Accounts share a creation time.
  private static final Sort NEWEST_FIRST =
      Sort.by(Sort.Order.desc(UserEntity::getCreatedAt), Sort.Order.asc(UserEntity::getId));

  private final UserRepository userRepository;
  private final Clock clock;

  UserListService(UserRepository userRepository, Clock clock) {
    this.userRepository = userRepository;
    this.clock = clock;
  }

  /** @param page 0-based */
  @Transactional(readOnly = true)
  public Page<ListedAccount> page(int page, int size) {
    Instant now = clock.instant();
    return userRepository.findAll(PageRequest.of(page, size, NEWEST_FIRST)).map(user -> ListedAccounts.of(user, now));
  }
}
