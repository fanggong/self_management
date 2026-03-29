import type {
  ApiResult,
  AuthUser,
  ChangePasswordPayload,
  LoginPayload,
  RegisterPayload,
  UpdateProfilePayload
} from '~/types/auth';

type MockUser = AuthUser & {
  password: string;
};

const USERS_KEY = 'sm_mock_users';
const SESSION_COOKIE_KEY = 'sm_mock_session_user';
const INITIAL_USERS: MockUser[] = [
  {
    id: 'u-demo-admin',
    displayName: 'Admin',
    principal: 'demo',
    email: 'admin@example.invalid',
    phone: '+1 555 010 9999',
    avatarUrl: '',
    role: 'admin',
    password: 'demo123456'
  }
];
const serverUsers: MockUser[] = [...INITIAL_USERS];

const wait = (ms = 300) => new Promise((resolve) => setTimeout(resolve, ms));

const sanitizeUser = (user: MockUser): AuthUser => {
  const { password: _password, ...rest } = user;
  return rest;
};

const parseUsers = (raw: string | null): MockUser[] => {
  if (!raw) {
    return [...INITIAL_USERS];
  }

  try {
    const parsed = JSON.parse(raw) as MockUser[];
    if (!Array.isArray(parsed) || parsed.length === 0) {
      return [...INITIAL_USERS];
    }

    return parsed;
  } catch {
    return [...INITIAL_USERS];
  }
};

const readUsers = (): MockUser[] => {
  if (!import.meta.client) {
    return [...serverUsers];
  }

  const raw = localStorage.getItem(USERS_KEY);
  return parseUsers(raw);
};

const writeUsers = (users: MockUser[]) => {
  if (!import.meta.client) {
    serverUsers.splice(0, serverUsers.length, ...users);
    return;
  }

  localStorage.setItem(USERS_KEY, JSON.stringify(users));
};

const readSessionUserId = () => {
  const sessionCookie = useCookie<string | null>(SESSION_COOKIE_KEY, { sameSite: 'lax' });
  return sessionCookie.value?.trim() || null;
};

const writeSessionUserId = (userId: string | null) => {
  const sessionCookie = useCookie<string | null>(SESSION_COOKIE_KEY, {
    sameSite: 'lax',
    maxAge: userId ? 7 * 24 * 60 * 60 : 0
  });
  sessionCookie.value = userId;
};

export const mockAuthApi = {
  async login(payload: LoginPayload): Promise<ApiResult<AuthUser>> {
    await wait();

    const principal = payload.principal?.trim().toLowerCase() ?? '';
    const password = payload.password ?? '';
    const users = readUsers();
    const user = users.find(
      (item) => item.principal.toLowerCase() === principal && item.password === password
    );

    if (!user) {
      return {
        success: false,
        message: 'Incorrect username or password. Please try again.',
        code: 'INVALID_CREDENTIALS'
      };
    }

    writeSessionUserId(user.id);

    return {
      success: true,
      data: sanitizeUser(user)
    };
  },

  async register(payload: RegisterPayload): Promise<ApiResult<AuthUser>> {
    await wait();

    const displayName = payload.displayName?.trim();
    const principal = payload.principal?.trim().toLowerCase();
    const password = payload.password ?? '';

    if (!displayName || !principal || !password) {
      return {
        success: false,
        message: 'Please complete all required registration fields.',
        code: 'VALIDATION_ERROR'
      };
    }

    if (password !== payload.confirmPassword) {
      return {
        success: false,
        message: 'Passwords do not match.',
        code: 'PASSWORD_MISMATCH'
      };
    }

    const users = readUsers();
    if (users.some((user) => user.principal.toLowerCase() === principal)) {
      return {
        success: false,
        message: 'This username already exists. Please choose another one.',
        code: 'PRINCIPAL_CONFLICT'
      };
    }

    const created: MockUser = {
      id: `u-${Date.now()}`,
      displayName,
      principal,
      email: '',
      phone: '',
      avatarUrl: '',
      role: 'member',
      password
    };

    users.push(created);
    writeUsers(users);

    return {
      success: true,
      data: sanitizeUser(created),
      message: 'Registration successful. Please sign in.'
    };
  },

  async logout(): Promise<ApiResult<null>> {
    await wait(150);
    writeSessionUserId(null);
    return { success: true, data: null };
  },

  async me(): Promise<ApiResult<AuthUser>> {
    await wait(150);

    const userId = readSessionUserId();
    if (!userId) {
      return {
        success: false,
        message: 'Invalid session. Please sign in again.',
        code: 'INVALID_TOKEN'
      };
    }

    const users = readUsers();
    const user = users.find((item) => item.id === userId);

    if (!user) {
      return {
        success: false,
        message: 'Your session has expired.',
        code: 'USER_NOT_FOUND'
      };
    }

    return {
      success: true,
      data: sanitizeUser(user)
    };
  },

  async updateProfile(payload: UpdateProfilePayload): Promise<ApiResult<AuthUser>> {
    await wait(250);

    const userId = readSessionUserId();
    if (!userId) {
      return {
        success: false,
        message: 'Invalid session. Please sign in again.',
        code: 'INVALID_TOKEN'
      };
    }

    const users = readUsers();
    const userIndex = users.findIndex((item) => item.id === userId);

    if (userIndex === -1) {
      return {
        success: false,
        message: 'Your session has expired.',
        code: 'USER_NOT_FOUND'
      };
    }

    if (!payload.displayName.trim()) {
      return {
        success: false,
        message: 'Nickname is required.',
        code: 'VALIDATION_ERROR'
      };
    }

    const updatedUser: MockUser = {
      ...users[userIndex],
      displayName: payload.displayName.trim(),
      email: payload.email.trim(),
      phone: payload.phone.trim(),
      avatarUrl: String(payload.avatarUrl ?? '').trim()
    };

    users.splice(userIndex, 1, updatedUser);
    writeUsers(users);

    return {
      success: true,
      data: sanitizeUser(updatedUser),
      message: 'Profile updated successfully.'
    };
  },

  async changePassword(payload: ChangePasswordPayload): Promise<ApiResult<null>> {
    await wait(300);

    const userId = readSessionUserId();
    if (!userId) {
      return {
        success: false,
        message: 'Invalid session. Please sign in again.',
        code: 'INVALID_TOKEN'
      };
    }

    const users = readUsers();
    const userIndex = users.findIndex((item) => item.id === userId);

    if (userIndex === -1) {
      return {
        success: false,
        message: 'Your session has expired.',
        code: 'USER_NOT_FOUND'
      };
    }

    const user = users[userIndex];

    if (payload.currentPassword !== user.password) {
      return {
        success: false,
        message: 'Current password is incorrect.',
        code: 'INVALID_CREDENTIALS'
      };
    }

    if (!payload.newPassword || payload.newPassword.length < 6) {
      return {
        success: false,
        message: 'New password must be at least 6 characters.',
        code: 'VALIDATION_ERROR'
      };
    }

    if (payload.newPassword !== payload.confirmPassword) {
      return {
        success: false,
        message: 'Passwords do not match.',
        code: 'PASSWORD_MISMATCH'
      };
    }

    users.splice(userIndex, 1, {
      ...user,
      password: payload.newPassword
    });
    writeUsers(users);

    return {
      success: true,
      data: null,
      message: 'Password updated successfully.'
    };
  }
};
