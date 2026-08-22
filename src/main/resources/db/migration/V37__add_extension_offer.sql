ALTER TABLE lunch_requests
	ADD COLUMN extension_offer_id UUID,
	ADD COLUMN extension_expires_at TIMESTAMP WITHOUT TIME ZONE,
	ADD COLUMN extension_target_time_slot TIMESTAMP WITHOUT TIME ZONE,
	ADD CONSTRAINT uq_lunch_requests_extension_offer_id
		UNIQUE (extension_offer_id);
