package ru.itmo.nemat.weezzy.connection.match;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.connection.match.dto.ProfileMatchResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class ProfileMatchController {
	private final ProfileMatchService service;

	@GetMapping("/{profileId}/matches")
	public ResponseEntity<List<ProfileMatchResponse>> findAllMatchesByProfileId(@PathVariable UUID profileId) {
		return ResponseEntity.ok(service.findAllMatchesByProfileId(profileId));
	}
}
