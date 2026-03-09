package com.otw.adminapi.auth;

import com.otw.adminapi.user.AuthUserView;

public record AuthLoginView(String token, AuthUserView user) {
}
