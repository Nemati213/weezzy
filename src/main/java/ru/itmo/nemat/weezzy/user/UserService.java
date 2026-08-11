package ru.itmo.nemat.weezzy.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public User register(String email, String rawPassword) {
		String normalizedEmail = normalizeEmail(email);
		if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
			throw new DuplicateUserException(normalizedEmail);
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

	@Transactional(readOnly = true)
	public User findByEmail(String email) {
		String normalizedEmail = normalizeEmail(email);
		return userRepository.findByEmailIgnoreCase(normalizedEmail)
				.orElseThrow(() -> new UserNotFoundException(normalizedEmail));
	}

	@Transactional(readOnly = true)
	public Optional<User> findOptionalByEmail(String email) {
		return userRepository.findByEmailIgnoreCase(normalizeEmail(email));
	}

	@Transactional(readOnly = true)
	public User authenticate(String email, String rawPassword) {
		String normalizedEmail = normalizeEmail(email);
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

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
