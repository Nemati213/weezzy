package ru.itmo.nemat.weezzy.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AccountAccessService accountAccessService;

	@Transactional
	public User register(String email, String rawPassword) {
		String normalizedEmail = EmailNormalizer.normalize(email);
		if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
			throw new DuplicateUserException(normalizedEmail);
		}
		if (accountAccessService.isEmailRestrictedForRegistration(normalizedEmail)) {
			throw new RegistrationRestrictedException();
		}
		User user = new User();
		user.setEmail(normalizedEmail);
		user.setPasswordHash(passwordEncoder.encode(rawPassword));
		user.setRole(UserRole.USER);

		return userRepository.save(user);
	}

	@Transactional(readOnly = true)
	public User findById(UUID id) {
		return userRepository.findById(id)
				.orElseThrow(() -> new UserNotFoundException(id));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public List<User> findAllByIdsForUpdate(Collection<UUID> userIds) {
		return userIds.isEmpty() ? List.of() : userRepository.findAllByIdForUpdate(userIds);
	}

	@Transactional(readOnly = true)
	public User findByEmail(String email) {
		String normalizedEmail = EmailNormalizer.normalize(email);
		return userRepository.findByEmailIgnoreCase(normalizedEmail)
				.orElseThrow(() -> new UserNotFoundException(normalizedEmail));
	}

	@Transactional(readOnly = true)
	public Optional<User> findOptionalByEmail(String email) {
		return userRepository.findByEmailIgnoreCase(EmailNormalizer.normalize(email));
	}

	@Transactional(readOnly = true)
	public User authenticate(String email, String rawPassword) {
		String normalizedEmail = EmailNormalizer.normalize(email);
		User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
				.orElseThrow(InvalidCredentialsException::new);

		if (!passwordMatches(user, rawPassword)) {
			throw new InvalidCredentialsException();
		}

		return user;
	}

	@Transactional(readOnly = true)
	public User findAdminById(UUID id) {
		User user = findById(id);
		if (user.getRole() != UserRole.ADMIN) {
			throw new AdminRoleRequiredException(id);
		}
		return user;
	}

	public boolean passwordMatches(User user, String rawPassword) {
		return passwordEncoder.matches(rawPassword, user.getPasswordHash());
	}

	@Transactional
	public void updatePassword(User user, String newRawPassword) {
		user.setPasswordHash(passwordEncoder.encode(newRawPassword));
		userRepository.save(user);
	}
}
