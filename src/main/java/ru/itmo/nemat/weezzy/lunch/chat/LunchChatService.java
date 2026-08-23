package ru.itmo.nemat.weezzy.lunch.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.lunch.chat.dto.CreateLunchChatMessageRequest;
import ru.itmo.nemat.weezzy.lunch.chat.dto.LunchChatMessagePageResponse;
import ru.itmo.nemat.weezzy.lunch.chat.dto.LunchChatMessageResponse;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupMember;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LunchChatService {
	private final LunchChatAccessService accessService;
	private final LunchChatMessageRepository messageRepository;
	private final LunchChatCursorCodec cursorCodec;

	@Transactional
	public LunchChatSendResult send(
			UUID userId,
			CreateLunchChatMessageRequest request
	) {
		LunchGroupMember membership = accessService
				.requireActiveMembershipForUpdate(userId);
		UUID senderProfileId = membership.getProfile().getId();
		Optional<LunchChatMessage> existing = messageRepository
				.findBySenderProfileIdAndClientMessageId(
						senderProfileId,
						request.clientMessageId()
				);
		if (existing.isPresent()) {
			LunchChatMessage message = existing.orElseThrow();
			if (!message.getGroup().getId().equals(membership.getGroup().getId())
					|| !message.getContent().equals(request.content())) {
				throw new LunchChatMessageConflictException(request.clientMessageId());
			}
			return new LunchChatSendResult(message, false);
		}

		LunchChatMessage message = new LunchChatMessage();
		message.setGroup(membership.getGroup());
		message.setSenderProfile(membership.getProfile());
		message.setClientMessageId(request.clientMessageId());
		message.setContent(request.content());
		return new LunchChatSendResult(messageRepository.saveAndFlush(message), true);
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public LunchChatMessagePageResponse findPage(
			UUID userId,
			String encodedBefore,
			String encodedAfter,
			int limit
	) {
		LunchGroupMember membership = accessService.requireActiveMembershipForRead(
				userId
		);
		if (hasText(encodedBefore) && hasText(encodedAfter)) {
			throw new InvalidLunchChatPageRequestException();
		}

		UUID groupId = membership.getGroup().getId();
		LunchChatCursor before = cursorCodec.decode(encodedBefore);
		LunchChatCursor after = cursorCodec.decode(encodedAfter);
		validateGroup(before, groupId);
		validateGroup(after, groupId);

		PageRequest pageRequest = PageRequest.of(0, limit + 1);
		if (after != null) {
			return findAfter(groupId, after, limit, pageRequest);
		}
		return findLatestOrBefore(groupId, before, limit, pageRequest);
	}

	private LunchChatMessagePageResponse findLatestOrBefore(
			UUID groupId,
			LunchChatCursor before,
			int limit,
			PageRequest pageRequest
	) {
		List<LunchChatMessage> fetched = before == null
				? messageRepository.findLatest(groupId, pageRequest)
				: messageRepository.findBefore(
						groupId,
						before.createdAt(),
						before.messageId(),
						pageRequest
				);
		boolean hasMoreBefore = fetched.size() > limit;
		List<LunchChatMessage> page = chronologicalPage(fetched, limit, true);
		String nextBeforeCursor = hasMoreBefore
				? cursorCodec.encode(toCursor(page.getFirst()))
				: null;
		String nextAfterCursor = before == null && !page.isEmpty()
				? cursorCodec.encode(toCursor(page.getLast()))
				: null;
		return response(page, nextBeforeCursor, nextAfterCursor);
	}

	private LunchChatMessagePageResponse findAfter(
			UUID groupId,
			LunchChatCursor after,
			int limit,
			PageRequest pageRequest
	) {
		List<LunchChatMessage> fetched = messageRepository.findAfter(
				groupId,
				after.createdAt(),
				after.messageId(),
				pageRequest
		);
		List<LunchChatMessage> page = chronologicalPage(fetched, limit, false);
		String nextAfterCursor = page.isEmpty()
				? cursorCodec.encode(after)
				: cursorCodec.encode(toCursor(page.getLast()));
		return response(page, null, nextAfterCursor);
	}

	private List<LunchChatMessage> chronologicalPage(
			List<LunchChatMessage> fetched,
			int limit,
			boolean reverse
	) {
		List<LunchChatMessage> page = new ArrayList<>(
				fetched.subList(0, Math.min(limit, fetched.size()))
		);
		if (reverse) {
			Collections.reverse(page);
		}
		return List.copyOf(page);
	}

	private LunchChatMessagePageResponse response(
			List<LunchChatMessage> page,
			String nextBeforeCursor,
			String nextAfterCursor
	) {
		return new LunchChatMessagePageResponse(
				page.stream().map(LunchChatMessageResponse::from).toList(),
				nextBeforeCursor,
				nextAfterCursor
		);
	}

	private LunchChatCursor toCursor(LunchChatMessage message) {
		return new LunchChatCursor(
				message.getGroup().getId(),
				message.getCreatedAt(),
				message.getId()
		);
	}

	private void validateGroup(LunchChatCursor cursor, UUID groupId) {
		if (cursor != null && !cursor.groupId().equals(groupId)) {
			throw new InvalidLunchChatCursorException();
		}
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
