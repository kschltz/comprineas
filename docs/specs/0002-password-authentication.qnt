module passwordAuth {
  // ========================================================================
  // Constants
  // ========================================================================

  pure val EMAILS = Set("alice@example.com", "bob@example.com", "charlie@example.com")
  pure val SESSION_IDS = Set("s1", "s2", "s3")
  pure val RESET_TOKEN_IDS = Set("r1", "r2", "r3")
  pure val NAMES = Set("Alice", "Bob", "Charlie")
  pure val PASSWORDS = Set("short", "validpass1", "validpass2")
  pure val VALID_PASSWORDS = Set("validpass1", "validpass2")

  pure val RESET_TOKEN_LIFETIME = 30
  pure val LOGIN_RATE_LIMIT_MAX = 5
  pure val LOGIN_RATE_LIMIT_WINDOW = 10

  // ========================================================================
  // Types
  // ========================================================================

  type User = {
    displayName: str,
    passwordHash: str
  }

  type Session = {
    email: str,
    createdAt: int
  }

  type ResetToken = {
    email: str,
    createdAt: int,
    usedAt: int,
    used: bool
  }

  // ========================================================================
  // State Variables
  // ========================================================================

  var users: str -> User
  var sessions: str -> Session
  var resetTokens: str -> ResetToken
  var failedLogins: str -> Set[int]
  var currentTime: int

  // ========================================================================
  // Pure Definitions
  // ========================================================================

  pure def hasPassword(userMap, email) =
    email.in(userMap.keys()) and userMap.get(email).passwordHash != ""

  pure def failedCountInWindow(fails, email, now, window) =
    if (email.in(fails.keys()))
      fails.get(email).filter(t => now - t < window).size()
    else
      0

  pure def withinLoginRateLimit(fails, email, now) =
    failedCountInWindow(fails, email, now, LOGIN_RATE_LIMIT_WINDOW) < LOGIN_RATE_LIMIT_MAX

  pure def isResetTokenValid(tokMap, tokenId, now) =
    tokenId.in(tokMap.keys()) and
    tokMap.get(tokenId).used == false and
    now - tokMap.get(tokenId).createdAt < RESET_TOKEN_LIFETIME

  pure def removeMapKey(m, k) =
    m.keys().exclude(Set(k)).mapBy(k2 => m.get(k2))

  // ========================================================================
  // Actions
  // ========================================================================

  // Simulate a user arriving from magic-link auth (PRD-0001) with no password.
  // This action exists so the "set password" flow (FR-14) is modelled.
  action createMagicLinkUser(email: str, name: str): bool = all {
    email.in(EMAILS),
    not(email.in(users.keys())),
    name.in(NAMES),
    users' = users.put(email, { displayName: name, passwordHash: "" }),
    sessions' = sessions,
    resetTokens' = resetTokens,
    failedLogins' = failedLogins,
    currentTime' = currentTime + 1,
  }

  action register(email: str, password: str, name: str): bool = {
    nondet sid = SESSION_IDS.exclude(sessions.keys()).oneOf()
    all {
      email.in(EMAILS),
      not(email.in(users.keys())),
      password.in(VALID_PASSWORDS),
      name.in(NAMES),
      users' = users.put(email, { displayName: name, passwordHash: password }),
      sessions' = sessions.put(sid, { email: email, createdAt: currentTime }),
      resetTokens' = resetTokens,
      failedLogins' = failedLogins,
      sid.in(SESSION_IDS),
      not(sid.in(sessions.keys())),
      currentTime' = currentTime + 1,
    }
  }

  action loginSuccess(email: str, password: str): bool = {
    nondet sid = SESSION_IDS.exclude(sessions.keys()).oneOf()
    all {
      email.in(EMAILS),
      email.in(users.keys()),
      hasPassword(users, email),
      users.get(email).passwordHash == password,
      withinLoginRateLimit(failedLogins, email, currentTime),
      users' = users,
      sessions' = sessions.put(sid, { email: email, createdAt: currentTime }),
      resetTokens' = resetTokens,
      failedLogins' = failedLogins,
      sid.in(SESSION_IDS),
      not(sid.in(sessions.keys())),
      currentTime' = currentTime + 1,
    }
  }

  // Failed login: either user doesn't exist, or password doesn't match.
  // Rate-limit guard applies to all attempts.
  action loginFail(email: str, password: str): bool = all {
    email.in(EMAILS),
    not(email.in(users.keys()) and users.get(email).passwordHash == password),
    withinLoginRateLimit(failedLogins, email, currentTime),
    users' = users,
    sessions' = sessions,
    resetTokens' = resetTokens,
    failedLogins' =
      if (email.in(failedLogins.keys()))
        failedLogins.set(email, failedLogins.get(email).union(Set(currentTime)))
      else
        failedLogins.put(email, Set(currentTime)),
    currentTime' = currentTime + 1,
  }

  // Login blocked by rate limit.
  action loginBlocked(email: str): bool = all {
    email.in(EMAILS),
    not(withinLoginRateLimit(failedLogins, email, currentTime)),
    users' = users,
    sessions' = sessions,
    resetTokens' = resetTokens,
    failedLogins' = failedLogins,
    currentTime' = currentTime + 1,
  }

  action requestPasswordReset(email: str): bool = {
    nondet tokenId = RESET_TOKEN_IDS.exclude(resetTokens.keys()).oneOf()
    all {
      email.in(EMAILS),
      tokenId.in(RESET_TOKEN_IDS),
      not(tokenId.in(resetTokens.keys())),
      resetTokens' = resetTokens.put(tokenId, {
        email: email,
        createdAt: currentTime,
        usedAt: -1,
        used: false
      }),
      users' = users,
      sessions' = sessions,
      failedLogins' = failedLogins,
      currentTime' = currentTime + 1,
    }
  }

  action resetPassword(tokenId: str, newPassword: str): bool = all {
    tokenId.in(RESET_TOKEN_IDS),
    isResetTokenValid(resetTokens, tokenId, currentTime),
    newPassword.in(VALID_PASSWORDS),
    val email = resetTokens.get(tokenId).email
    all {
      resetTokens' = resetTokens.set(tokenId, {
        email: email,
        createdAt: resetTokens.get(tokenId).createdAt,
        usedAt: currentTime,
        used: true
      }),
      users' =
        if (email.in(users.keys()))
          users.set(email, {
            displayName: users.get(email).displayName,
            passwordHash: newPassword
          })
        else
          users,
      sessions' = sessions,
      failedLogins' = failedLogins,
      currentTime' = currentTime + 1,
    }
  }

  action setPassword(email: str, password: str): bool = all {
    email.in(EMAILS),
    email.in(users.keys()),
    not(hasPassword(users, email)),
    password.in(VALID_PASSWORDS),
    users' = users.set(email, {
      displayName: users.get(email).displayName,
      passwordHash: password
    }),
    sessions' = sessions,
    resetTokens' = resetTokens,
    failedLogins' = failedLogins,
    currentTime' = currentTime + 1,
  }

  action logout(sessionId: str): bool = all {
    sessionId.in(sessions.keys()),
    sessions' = removeMapKey(sessions, sessionId),
    users' = users,
    resetTokens' = resetTokens,
    failedLogins' = failedLogins,
    currentTime' = currentTime + 1,
  }

  action tick = all {
    currentTime' = currentTime + 1,
    users' = users,
    sessions' = sessions,
    resetTokens' = resetTokens,
    failedLogins' = failedLogins,
  }

  // ========================================================================
  // Init & Step
  // ========================================================================

  action init = all {
    users' = Map(),
    sessions' = Map(),
    resetTokens' = Map(),
    failedLogins' = Map(),
    currentTime' = 0,
  }

  action step = any {
    nondet email = EMAILS.oneOf()
    nondet password = PASSWORDS.oneOf()
    nondet name = NAMES.oneOf()
    any {
      createMagicLinkUser(email, name),
      register(email, password, name),
      loginSuccess(email, password),
      loginFail(email, password),
      loginBlocked(email),
      requestPasswordReset(email),
      setPassword(email, password),
    },
    nondet tokenId = RESET_TOKEN_IDS.oneOf()
    nondet newPassword = PASSWORDS.oneOf()
    resetPassword(tokenId, newPassword),
    nondet sessionId = SESSION_IDS.oneOf()
    logout(sessionId),
    tick,
  }

  // ========================================================================
  // Invariants
  // ========================================================================

  // INV-1: Every session belongs to an existing user.
  val invSessionRequiresUser =
    SESSION_IDS.forall(sid =>
      if (sid.in(sessions.keys()))
        sessions.get(sid).email.in(users.keys())
      else
        true
    )

  // INV-2: No reset token is both used and still valid (single-use enforcement).
  val invNoReplayReset =
    RESET_TOKEN_IDS.forall(tid =>
      if (tid.in(resetTokens.keys()))
        not(resetTokens.get(tid).used and isResetTokenValid(resetTokens, tid, currentTime))
      else
        true
    )

  // INV-3: Failed login attempts never exceed rate limit.
  val invRateLimit =
    EMAILS.forall(email =>
      failedCountInWindow(failedLogins, email, currentTime, LOGIN_RATE_LIMIT_WINDOW) <= LOGIN_RATE_LIMIT_MAX
    )

  // INV-4: Every used reset token was consumed within its lifetime.
  val invUsedWithinLifetime =
    RESET_TOKEN_IDS.forall(tid =>
      if (tid.in(resetTokens.keys()) and resetTokens.get(tid).used)
        resetTokens.get(tid).usedAt - resetTokens.get(tid).createdAt < RESET_TOKEN_LIFETIME
      else
        true
    )

  // INV-5: Any user with a password has a valid (modelled as "long enough") password.
  val invValidPassword =
    EMAILS.forall(email =>
      if (email.in(users.keys()) and users.get(email).passwordHash != "")
        users.get(email).passwordHash.in(VALID_PASSWORDS)
      else
        true
    )

  // Combined invariant for verification
  val inv =
    invSessionRequiresUser and
    invNoReplayReset and
    invRateLimit and
    invUsedWithinLifetime and
    invValidPassword

  // ========================================================================
  // Test Runs
  // ========================================================================

  run happyPath =
    init
      .then(register("alice@example.com", "validpass1", "Alice"))
      .then(loginSuccess("alice@example.com", "validpass1"))

  run resetFlow =
    init
      .then(register("bob@example.com", "validpass1", "Bob"))
      .then(requestPasswordReset("bob@example.com"))
      .then(resetPassword("r1", "validpass2"))
      .then(loginSuccess("bob@example.com", "validpass2"))

  run setPasswordFlow =
    init
      .then(createMagicLinkUser("charlie@example.com", "Charlie"))
      .then(setPassword("charlie@example.com", "validpass1"))
      .then(loginSuccess("charlie@example.com", "validpass1"))

  run rateLimitTest =
    init
      .then(register("alice@example.com", "validpass1", "Alice"))
      .then(loginFail("alice@example.com", "short"))
      .then(loginFail("alice@example.com", "short"))
      .then(loginFail("alice@example.com", "short"))
      .then(loginFail("alice@example.com", "short"))
      .then(loginFail("alice@example.com", "short"))
      .then(loginBlocked("alice@example.com"))
}
