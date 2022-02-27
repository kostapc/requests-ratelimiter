package com.arnoldgalovics.blog.ratelimiterfeign;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSessionValidationResponse {
    private boolean valid;
    private String sessionId;
}
