module joinListByCode {
  // ========================================================================
  // Constants
  // ========================================================================

  pure val CODES = Set("abc123", "xyz789", "def456")
  pure val NAMES = Set("Groceries", "Weekend BBQ", "Party Supplies", "Home Depot", "Weekly Shop", "Office Snacks")
  pure val EMAILS = Set("alice@example.com", "bob@example.com", "charlie@example.com")
  pure val STATUSES = Set("active", "completed", "deleted")
  pure val VERSIONS = Set(1, 2, 3)

  // ========================================================================
  // Types
  // ========================================================================

  type ShopList = {
    code: str,
    name: str,
    status: str,
    version: int,
    createdBy: str,
    completedAt: int  // -1 means not completed
  }

  type Participant = {
    listCode: str,
    email: str
  }

  // ========================================================================
  // State Variables
  // ========================================================================

  var lists: str -> ShopList
  var participants: Set[Participant]
  var currentUser: str
  var sseViewers: str -> Set[str]
  var currentTime: int
  var rateLimitAttempts: str -> int   // email -> attempt count in current window
  var rateLimitWindowStart: str -> int // email -> window start time

  // ========================================================================
  // Pure Definitions
  // ========================================================================

  pure def isValidName(name) =
    name.in(NAMES)

  pure def isActive(listMap, code) =
    code.in(listMap.keys()) and listMap.get(code).status == "active"

  pure def isCompleted(listMap, code) =
    code.in(listMap.keys()) and listMap.get(code).status == "completed"

  pure def isDeleted(listMap, code) =
    code.in(listMap.keys()) and listMap.get(code).status == "deleted"

  pure def isParticipant(parts, code, email) =
    parts.contains({ listCode: code, email: email })

  pure def codeNotInUse(listMap, code) =
    not(code.in(listMap.keys()))

  // Rate-limit helpers
  pure def currentAttempts(attempts, windowStart, user, time) =
    if (user.in(windowStart.keys()))
      if (time - windowStart.get(user) >= 60)
        0
      else if (user.in(attempts.keys()))
        attempts.get(user)
      else
        0
    else
      0

  pure def isRateLimited(attempts, windowStart, user, time) =
    currentAttempts(attempts, windowStart, user, time) >= 10

  // ========================================================================
  // PRD-0003 Actions (reused for spec setup)
  // ========================================================================

  action createList(code: str, name: str): bool = all {
    currentUser.in(EMAILS),
    code.in(CODES),
    codeNotInUse(lists, code),
    isValidName(name),
    lists' = lists.put(code, {
      code: code,
      name: name,
      status: "active",
      version: 1,
      createdBy: currentUser,
      completedAt: -1
    }),
    participants' = participants.union(Set({ listCode: code, email: currentUser })),
    sseViewers' = sseViewers,
    currentTime' = currentTime + 1,
    currentUser' = currentUser,
    rateLimitAttempts' = rateLimitAttempts,
    rateLimitWindowStart' = rateLimitWindowStart,
  }

  action completeList(code: str, expectedVersion: int): bool = all {
    code.in(lists.keys()),
    isActive(lists, code),
    lists.get(code).version == expectedVersion,
    lists' = lists.set(code, {
      code: code,
      name: lists.get(code).name,
      status: "completed",
      version: lists.get(code).version + 1,
      createdBy: lists.get(code).createdBy,
      completedAt: currentTime
    }),
    participants' = participants,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
    rateLimitAttempts' = rateLimitAttempts,
    rateLimitWindowStart' = rateLimitWindowStart,
  }

  action deleteList(code: str, expectedVersion: int): bool = all {
    code.in(lists.keys()),
    isActive(lists, code),
    lists.get(code).version == expectedVersion,
    lists' = lists.set(code, {
      code: code,
      name: lists.get(code).name,
      status: "deleted",
      version: lists.get(code).version + 1,
      createdBy: lists.get(code).createdBy,
      completedAt: lists.get(code).completedAt
    }),
    participants' = participants,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
    rateLimitAttempts' = rateLimitAttempts,
    rateLimitWindowStart' = rateLimitWindowStart,
  }

  action viewerConnects(code: str, email: str): bool = all {
    code.in(lists.keys()),
    not(isDeleted(lists, code)),
    sseViewers' = sseViewers.put(code,
      if (code.in(sseViewers.keys()))
        sseViewers.get(code).union(Set(email))
      else
        Set(email)),
    lists' = lists,
    participants' = participants,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
    rateLimitAttempts' = rateLimitAttempts,
    rateLimitWindowStart' = rateLimitWindowStart,
  }

  action viewerDisconnects(code: str, email: str): bool = all {
    code.in(sseViewers.keys()),
    email.in(sseViewers.get(code)),
    sseViewers' = sseViewers.put(code, sseViewers.get(code).exclude(Set(email))),
    lists' = lists,
    participants' = participants,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
    rateLimitAttempts' = rateLimitAttempts,
    rateLimitWindowStart' = rateLimitWindowStart,
  }

  // ========================================================================
  // PRD-0004 Actions
  // ========================================================================

  // Helper to advance rate-limit counters for a user on an attempt
  pure def nextRateLimitAttempts(attempts, windowStart, user, time) =
    if (user.in(windowStart.keys()) and time - windowStart.get(user) < 60)
      attempts.get(user) + 1
    else
      1

  pure def nextRateLimitWindowStart(windowStart, user, time) =
    if (user.in(windowStart.keys()) and time - windowStart.get(user) < 60)
      windowStart.get(user)
    else
      time

  // FR-2 through FR-8: Join an active list as a new participant
  action joinList(code: str): bool = all {
    currentUser.in(EMAILS),
    code.in(CODES),
    code.in(lists.keys()),
    isActive(lists, code),
    not(isParticipant(participants, code, currentUser)),
    not(isRateLimited(rateLimitAttempts, rateLimitWindowStart, currentUser, currentTime)),
    participants' = participants.union(Set({ listCode: code, email: currentUser })),
    rateLimitAttempts' = rateLimitAttempts.put(currentUser,
      nextRateLimitAttempts(rateLimitAttempts, rateLimitWindowStart, currentUser, currentTime)),
    rateLimitWindowStart' = rateLimitWindowStart.put(currentUser,
      nextRateLimitWindowStart(rateLimitWindowStart, currentUser, currentTime)),
    lists' = lists,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
  }

  // FR-7: Already a participant — redirect without duplicate row
  action joinAlreadyParticipant(code: str): bool = all {
    currentUser.in(EMAILS),
    code.in(CODES),
    code.in(lists.keys()),
    isActive(lists, code),
    isParticipant(participants, code, currentUser),
    not(isRateLimited(rateLimitAttempts, rateLimitWindowStart, currentUser, currentTime)),
    participants' = participants,
    rateLimitAttempts' = rateLimitAttempts.put(currentUser,
      nextRateLimitAttempts(rateLimitAttempts, rateLimitWindowStart, currentUser, currentTime)),
    rateLimitWindowStart' = rateLimitWindowStart.put(currentUser,
      nextRateLimitWindowStart(rateLimitWindowStart, currentUser, currentTime)),
    lists' = lists,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
  }

  // FR-9: Rate limit exceeded — request rejected, no state change
  action rateLimitExceeded(user: str): bool = all {
    user.in(EMAILS),
    isRateLimited(rateLimitAttempts, rateLimitWindowStart, user, currentTime),
    lists' = lists,
    participants' = participants,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
    rateLimitAttempts' = rateLimitAttempts,
    rateLimitWindowStart' = rateLimitWindowStart,
  }

  // Negative case: invalid code, list not found, or list not active
  action actionOnInvalidCode(code: str): bool = all {
    currentUser.in(EMAILS),
    not(isRateLimited(rateLimitAttempts, rateLimitWindowStart, currentUser, currentTime)),
    (not(code.in(CODES)) or not(code.in(lists.keys())) or not(isActive(lists, code))),
    participants' = participants,
    rateLimitAttempts' = rateLimitAttempts.put(currentUser,
      nextRateLimitAttempts(rateLimitAttempts, rateLimitWindowStart, currentUser, currentTime)),
    rateLimitWindowStart' = rateLimitWindowStart.put(currentUser,
      nextRateLimitWindowStart(rateLimitWindowStart, currentUser, currentTime)),
    lists' = lists,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
  }

  // Test helper: switch current user
  action switchUser(email: str): bool = all {
    email.in(EMAILS),
    currentUser' = email,
    lists' = lists,
    participants' = participants,
    sseViewers' = sseViewers,
    currentTime' = currentTime + 1,
    rateLimitAttempts' = rateLimitAttempts,
    rateLimitWindowStart' = rateLimitWindowStart,
  }

  // ========================================================================
  // Init & Step
  // ========================================================================

  action init = all {
    lists' = Map(),
    participants' = Set(),
    currentUser' = "alice@example.com",
    sseViewers' = Map(),
    currentTime' = 0,
    rateLimitAttempts' = Map(),
    rateLimitWindowStart' = Map(),
  }

  action tick = all {
    currentTime' = currentTime + 1,
    lists' = lists,
    participants' = participants,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    rateLimitAttempts' = rateLimitAttempts,
    rateLimitWindowStart' = rateLimitWindowStart,
  }

  action step = any {
    nondet code = CODES.oneOf()
    nondet name = NAMES.oneOf()
    nondet user = EMAILS.oneOf()
    any {
      createList(code, name),
      joinList(code),
      joinAlreadyParticipant(code),
      rateLimitExceeded(user),
      actionOnInvalidCode(code),
      switchUser(user),
    },
    tick,
  }

  action tick_OLD = all {
    currentTime' = currentTime + 1,
    lists' = lists,
    participants' = participants,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    rateLimitAttempts' = rateLimitAttempts,
    rateLimitWindowStart' = rateLimitWindowStart,
  }

  // ========================================================================
  // Invariants
  // ========================================================================

  // INV-1: Rate limit never exceeds 10 attempts within window
  val invRateLimitMax10 =
    EMAILS.forall(user =>
      currentAttempts(rateLimitAttempts, rateLimitWindowStart, user, currentTime) <= 10
    )

  // INV-2: Participants always have valid list code
  val invParticipantsValidCode =
    participants.forall(p =>
      p.listCode.in(lists.keys())
    )

  // INV-3: No duplicate participants (listCode, email) is unique
  // Sets inherently prevent duplicates; this invariant formalises uniqueness.
  val invNoDuplicateParticipants =
    participants.forall(p1 =>
      participants.forall(p2 =>
        if (p1.listCode == p2.listCode and p1.email == p2.email)
          p1 == p2
        else
          true
      )
    )

  // INV-4: All rate limit counts are non-negative
  val invRateLimitNonNegative =
    rateLimitAttempts.keys().forall(user =>
      rateLimitAttempts.get(user) >= 0
    )

  // Combined invariant for verification (does not include INV-3, which is
  // tautological for Set[Participant]; see separate test assertion below)
  val inv =
    invRateLimitMax10 and
    invParticipantsValidCode and
    invRateLimitNonNegative

  // ========================================================================
  // Test Runs
  // ========================================================================

  run joinActiveList =
    init
      .then(createList("abc123", "Groceries"))
      .then(switchUser("bob@example.com"))
      .then(joinList("abc123"))

  run runJoinAlreadyParticipant =
    init
      .then(createList("abc123", "Groceries"))
      .then(switchUser("bob@example.com"))
      .then(joinList("abc123"))
      .then(joinAlreadyParticipant("abc123"))

  run runRateLimitExceeded =
    init
      .then(createList("abc123", "Groceries"))
      .then(switchUser("bob@example.com"))
      .then(joinList("abc123"))
      .then(9.reps(_ => joinAlreadyParticipant("abc123")))
      .then(rateLimitExceeded("bob@example.com"))

  run runJoinCompletedList =
    init
      .then(createList("abc123", "Groceries"))
      .then(completeList("abc123", 1))
      .then(switchUser("bob@example.com"))
      .then(actionOnInvalidCode("abc123"))

  run runJoinDeletedList =
    init
      .then(createList("abc123", "Groceries"))
      .then(deleteList("abc123", 1))
      .then(switchUser("bob@example.com"))
      .then(actionOnInvalidCode("abc123"))

  run runRateLimitWindowReset =
    init
      .then(createList("abc123", "Groceries"))
      .then(switchUser("bob@example.com"))
      .then(joinList("abc123"))
      .then(9.reps(_ => joinAlreadyParticipant("abc123")))
      .then(60.reps(_ => tick))
      .then(joinList("abc123"))
}
