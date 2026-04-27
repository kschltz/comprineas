module sharedLists {
  // ========================================================================
  // Constants
  // ========================================================================

  pure val CODES = Set("abc123", "xyz789", "def456", "aaa111", "bbb222", "ccc333")
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

  var lists: str -> ShopList     // code -> ShopList
  var participants: Set[Participant]
  var currentUser: str           // email of the logged-in user
  var sseViewers: str -> Set[str] // code -> set of emails viewing this list
  var currentTime: int

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

  pure def userLists(listMap, parts, email) =
    listMap.keys().filter(code =>
      listMap.get(code).createdBy == email or
      isParticipant(parts, code, email)
    ).filter(code => listMap.get(code).status != "deleted")

  pure def codeNotInUse(listMap, code) =
    not(code.in(listMap.keys()))

  // ========================================================================
  // Actions
  // ========================================================================

  // FR-1, FR-2: Create a new list
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
  }

  // Note: Invalid name rejection (empty or >100 chars) cannot be modeled
  // in Quint due to lack of string length operators. The implementation
  // must enforce 1-100 character names. AC-2 covers this acceptance criterion.

  // FR-5: Rename an active list (optimistic locking)
  action renameList(code: str, newName: str, expectedVersion: int): bool = all {
    code.in(lists.keys()),
    isActive(lists, code),
    isValidName(newName),
    lists.get(code).version == expectedVersion,
    lists' = lists.set(code, {
      code: code,
      name: newName,
      status: lists.get(code).status,
      version: lists.get(code).version + 1,
      createdBy: lists.get(code).createdBy,
      completedAt: lists.get(code).completedAt
    }),
    participants' = participants,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
  }

  // FR-6: Rename conflict (version mismatch — no state change)
  action renameListConflict(code: str, newName: str, expectedVersion: int): bool = all {
    code.in(lists.keys()),
    isActive(lists, code),
    lists.get(code).version != expectedVersion,
    lists' = lists,
    participants' = participants,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
  }

  // FR-7: Complete a list
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
  }

  // FR-8: Soft-delete a list
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
  }

  // FR-10: SSE viewer connects to a list
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
  }

  // FR-10: SSE viewer disconnects
  action viewerDisconnects(code: str, email: str): bool = all {
    code.in(sseViewers.keys()),
    email.in(sseViewers.get(code)),
    sseViewers' = sseViewers.put(code, sseViewers.get(code).exclude(Set(email))),
    lists' = lists,
    participants' = participants,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
  }

  // FR-11 (negative): Attempt action on non-active list (no state change)
  action actionOnNonActive(code: str): bool = all {
    code.in(lists.keys()),
    not(isActive(lists, code)),
    lists' = lists,
    participants' = participants,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
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
  }

  action step = any {
    nondet code = CODES.oneOf()
    nondet name = NAMES.oneOf()
    nondet email = EMAILS.oneOf()
    nondet version = VERSIONS.oneOf()
    any {
      createList(code, name),
      renameList(code, name, version),
      renameListConflict(code, name, version),
      completeList(code, version),
      deleteList(code, version),
      viewerConnects(code, email),
      viewerDisconnects(code, email),
      actionOnNonActive(code),
    },
    tick,
  }

  action tick = all {
    currentTime' = currentTime + 1,
    lists' = lists,
    participants' = participants,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
  }

  // ========================================================================
  // Invariants
  // ========================================================================

  // INV-1: All lists have valid status
  val invValidStatus =
    lists.keys().forall(code =>
      lists.get(code).status.in(STATUSES)
    )

  // INV-2: All lists have version >= 1
  val invValidVersion =
    lists.keys().forall(code =>
      lists.get(code).version >= 1
    )

  // INV-3: Creator is always a participant
  val invCreatorIsParticipant =
    lists.keys().forall(code =>
      isParticipant(participants, code, lists.get(code).createdBy)
    )

  // INV-4: Completed lists have completedAt >= 0
  val invCompletedAtSet =
    lists.keys().forall(code =>
      if (isCompleted(lists, code))
        lists.get(code).completedAt >= 0
      else
        true
    )

  // INV-5: Non-active lists have version >= 2 (modified at least once)
  val invNonActiveVersionIncremented =
    lists.keys().forall(code =>
      if (not(isActive(lists, code)))
        lists.get(code).version >= 2
      else
        true
    )

  // Combined invariant for verification
  val inv =
    invValidStatus and
    invValidVersion and
    invCreatorIsParticipant and
    invCompletedAtSet and
    invNonActiveVersionIncremented

  // ========================================================================
  // Test Runs
  // ========================================================================

  run createAndRename =
    init
      .then(createList("abc123", "Groceries"))
      .then(renameList("abc123", "Weekly Groceries", 1))

  run createCompleteAndReject =
    init
      .then(createList("xyz789", "Party"))
      .then(completeList("xyz789", 1))
      .then(actionOnNonActive("xyz789"))

  run createDelete =
    init
      .then(createList("def456", "Home Depot"))
      .then(deleteList("def456", 1))

  run optimisticLockConflict =
    init
      .then(createList("aaa111", "List A"))
      .then(renameListConflict("aaa111", "List B", 999))
}