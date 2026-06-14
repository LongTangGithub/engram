package com.engram.quiz;

import java.util.UUID;

public record ClozeCard(
        UUID   conceptId,
        String prompt,   // source_span with the masked term replaced by [___]
        String answer    // the masked term
) {}
