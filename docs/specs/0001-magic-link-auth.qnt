module magicLinkAuth {
  // ========================================================================
  // Constants
  // ========================================================================

  pure val EMAILS = Set("alice@example.com", "bob@example.com", "charlie@example.com")
  pure val TOKEN_IDS = Set("token1", "token2", "token3", "token4")
  pure val SESSION_IDS = Set("session1", "session2", "session3")

  pure val TOKEN_LIFETIME = 15
  pure val RATE_LIMIT_WINDOW = 15
  pure val RATE_LIMIT_MAX = 3
  pure val SESSION_LIFETIME = 24

  // ========================================================================
  // Types
  // ========================================================================

  type Token = {
    email: str,
    createdAt: int,
    usedAt: int,
    used: bool
  }

  type Session = {
    email: str,
    createdAt: int
  }

  type User = {
    displayName: str
  }

  // ========================================================================
  // State Variables
  // ========================================================================

  var users: str -> User
  var tokens: str -> Token
  var sessions: str -> Session
  var currentTime: int

  // ========================================================================
  // Pure Definitions
  // ========================================================================

  pure def isTokenValid(tokMap, tokenId, now) =
    tokenId.in(tokMap.keys()) and
    tokMap.get(tokenId).used == false and
    now - tokMap.get(tokenId).createdAt < TOKEN_LIFETIME

  pure def requestCountInWindow(tokMap, email, now) =
    tokMap.keys()
      .filter(tid => tokMap.get(tid).email == email)
      .filter(tid => now - tokMap.get(tid).createdAt < RATE_LIMIT_WINDOW)
      .size()

  pure def withinRateLimit(tokMap, email, now) =
    requestCountInWindow(tokMap, email, now) < RATE_LIMIT_MAX

  pure def hasDisplayName(userMap, email) =
    email.in(userMap.keys()) and userMap.get(email).displayName != ""

  pure def removeMapKey(m, k) =
    m.keys().exclude(Set(k)).mapBy(k2 => m.get(k2))

  // ========================================================================
  // Actions
  // ========================================================================

  action requestMagicLink(email: str): bool = {
    nondet tokenId = TOKEN_IDS.exclude(tokens.keys()).oneOf()
    all {
      email.in(EMAILS),
      withinRateLimit(tokens, email, currentTime),
      tokenId.in(TOKEN_IDS),
      not(tokenId.in(tokens.keys())),
      tokens' = tokens.put(tokenId, {
        email: email,
        createdAt: currentTime,
        usedAt: -1,
        used: false
      }),
      users' = users,
      sessions' = sessions,
      currentTime' = currentTime + 1,
    }
  }

  action requestMagicLinkBlocked(email: str): bool = all {
    email.in(EMAILS),
    not(withinRateLimit(tokens, email, currentTime)),
    tokens' = tokens,
    users' = users,
    sessions' = sessions,
    currentTime' = currentTime + 1,
  }

  action clickMagicLink(tokenId: str): bool = {
    nondet newSessionId = SESSION_IDS.exclude(sessions.keys()).oneOf()
    all {
      tokenId.in(TOKEN_IDS),
      isTokenValid(tokens, tokenId, currentTime),
      val email = tokens.get(tokenId).email
      all {
        tokens' = tokens.set(tokenId, {
          email: email,
          createdAt: tokens.get(tokenId).createdAt,
          usedAt: currentTime,
          used: true
        }),
        users' =
          if (email.in(users.keys()))
            users
          else
            users.put(email, { displayName: "" }),
        sessions' =
          if (hasDisplayName(users, email))
            sessions.put(newSessionId, {
              email: email,
              createdAt: currentTime
            })
          else
            sessions,
        if (hasDisplayName(users, email))
          newSessionId.in(SESSION_IDS) and not(newSessionId.in(sessions.keys()))
        else
          true,
        currentTime' = currentTime + 1,
      }
    }
  }

  action setDisplayName(email: str, name: str): bool = {
    nondet newSessionId = SESSION_IDS.exclude(sessions.keys()).oneOf()
    all {
      email.in(EMAILS),
      email.in(users.keys()),
      users.get(email).displayName == "",
      name != "",
      users' = users.put(email, { displayName: name }),
      newSessionId.in(SESSION_IDS),
      not(newSessionId.in(sessions.keys())),
      sessions' = sessions.put(newSessionId, {
        email: email,
        createdAt: currentTime
      }),
      tokens' = tokens,
      currentTime' = currentTime + 1,
    }
  }

  action logout(sessionId: str): bool = all {
    sessionId.in(sessions.keys()),
    sessions' = removeMapKey(sessions, sessionId),
    users' = users,
    tokens' = tokens,
    currentTime' = currentTime + 1,
  }

  action tick = all {
    currentTime' = currentTime + 1,
    users' = users,
    tokens' = tokens,
    sessions' = sessions,
  }

  // ========================================================================
  // Init & Step
  // ========================================================================

  action init = all {
    users' = Map(),
    tokens' = Map(),
    sessions' = Map(),
    currentTime' = 0,
  }

  action step = any {
    nondet email = EMAILS.oneOf()
    any {
      requestMagicLink(email),
      requestMagicLinkBlocked(email),
    },
    nondet tokenId = TOKEN_IDS.oneOf()
    clickMagicLink(tokenId),
    nondet email2 = EMAILS.oneOf()
    nondet name = Set("Alice", "Bob", "Charlie").oneOf()
    setDisplayName(email2, name),
    nondet sessionId = SESSION_IDS.oneOf()
    logout(sessionId),
    tick,
  }

  // ========================================================================
  // Invariants
  // ========================================================================

  // INV-1: No token is both valid and used (single-use enforcement)
  val invNoReplay =
    TOKEN_IDS.forall(tid =>
      if (tid.in(tokens.keys()))
        not(tokens.get(tid).used and isTokenValid(tokens, tid, currentTime))
      else
        true
    )

  // INV-2: Every session belongs to a user with a set display name
  val invSessionRequiresName =
    SESSION_IDS.forall(sid =>
      if (sid.in(sessions.keys()))
        hasDisplayName(users, sessions.get(sid).email)
      else
        true
    )

  // INV-3: Rate limit never exceeded
  val invRateLimit =
    EMAILS.forall(email =>
      requestCountInWindow(tokens, email, currentTime) <= RATE_LIMIT_MAX
    )

  // INV-4: Every used token was used within its lifetime
  val invUsedWithinLifetime =
    TOKEN_IDS.forall(tid =>
      if (tid.in(tokens.keys()) and tokens.get(tid).used)
        tokens.get(tid).usedAt - tokens.get(tid).createdAt < TOKEN_LIFETIME
      else
        true
    )

  // ========================================================================
  // Test Runs
  // ========================================================================

  run happyPath =
    init
      .then(requestMagicLink("alice@example.com"))
      .then(clickMagicLink("token1"))
      .then(setDisplayName("alice@example.com", "Alice"))

  run returningUser =
    init
      .then(requestMagicLink("bob@example.com"))
      .then(clickMagicLink("token1"))
      .then(setDisplayName("bob@example.com", "Bob"))
      .then(logout("session1"))
      .then(requestMagicLink("bob@example.com"))
      .then(clickMagicLink("token2"))
}
