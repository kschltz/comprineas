module copyCompletedList {
  // ========================================================================
  // Constants
  // ========================================================================

  pure val CODES = Set("abc123", "xyz789", "def456", "aaa111")
  pure val NAMES = Set("Groceries", "Weekend BBQ", "Party Supplies", "Empty List")
  pure val EMAILS = Set("alice@example.com", "bob@example.com")
  pure val STATUSES = Set("active", "completed", "deleted")
  pure val VERSIONS = Set(1, 2)
  pure val ITEM_NAMES = Set("Milk", "Bread")
  pure val QUANTITIES = Set("1 gallon", "2 loaves")
  pure val OBSERVATIONS = Set("", "whole grain")

  // ========================================================================
  // Types
  // ========================================================================

  type ShopList = {
    code: str,
    name: str,
    status: str,
    version: int,
    createdBy: str,
    completedAt: int,
    copiedFrom: str
  }

  type Participant = {
    listCode: str,
    email: str
  }

  type ArchivedItem = {
    name: str,
    quantity: str,
    observations: str,
    checked: bool,
    position: int
  }

  // ========================================================================
  // State Variables
  // ========================================================================

  var lists: str -> ShopList
  var participants: Set[Participant]
  var currentUser: str
  var sseViewers: str -> Set[str]
  var currentTime: int
  var archivedItems: str -> Set[ArchivedItem]
  var copiedFrom: str -> str
  var items: str -> Set[ArchivedItem]

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

  pure def makeUnchecked(itm) = {
    name: itm.name,
    quantity: itm.quantity,
    observations: itm.observations,
    checked: false,
    position: itm.position
  }

  pure def copyArchivedItems(archivedSet) =
    archivedSet.map(makeUnchecked)

  // ========================================================================
  // Actions
  // ========================================================================

  // Create a new list (from PRD-0003 FR-1, FR-2)
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
      completedAt: -1,
      copiedFrom: ""
    }),
    participants' = participants.union(Set({ listCode: code, email: currentUser })),
    items' = items.put(code, Set()),
    archivedItems' = archivedItems.put(code, Set()),
    sseViewers' = sseViewers,
    currentTime' = currentTime + 1,
    currentUser' = currentUser,
    copiedFrom' = copiedFrom,
  }

  // Add an item to an active list
  action addItem(code: str, itemName: str, qty: str, obs: str): bool = all {
    code.in(lists.keys()),
    isActive(lists, code),
    itemName.in(ITEM_NAMES),
    qty.in(QUANTITIES),
    obs.in(OBSERVATIONS),
    items' = items.set(code, items.get(code).union(Set({
      name: itemName,
      quantity: qty,
      observations: obs,
      checked: false,
      position: items.get(code).size() + 1
    }))),
    lists' = lists,
    participants' = participants,
    archivedItems' = archivedItems,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
    copiedFrom' = copiedFrom,
  }

  // Complete a list (from PRD-0003 FR-7). Snapshots items to archivedItems.
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
      completedAt: currentTime,
      copiedFrom: lists.get(code).copiedFrom
    }),
    archivedItems' = archivedItems.set(code, items.get(code)),
    items' = items,
    participants' = participants,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
    copiedFrom' = copiedFrom,
  }

  // FR-1 through FR-8: Copy a completed list as a new active list.
  action copyCompletedList(originalCode: str, newCode: str, newName: str): bool = all {
    currentUser.in(EMAILS),
    originalCode.in(lists.keys()),
    isCompleted(lists, originalCode),
    originalCode.in(archivedItems.keys()),
    newCode.in(CODES),
    codeNotInUse(lists, newCode),
    isValidName(newName),
    lists' = lists.put(newCode, {
      code: newCode,
      name: newName,
      status: "active",
      version: 1,
      createdBy: currentUser,
      completedAt: -1,
      copiedFrom: originalCode
    }),
    participants' = participants.union(Set({ listCode: newCode, email: currentUser })),
    items' = items.put(newCode, copyArchivedItems(archivedItems.get(originalCode))),
    archivedItems' = archivedItems.put(newCode, Set()),
    copiedFrom' = copiedFrom.put(newCode, originalCode),
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
  }

  // FR-10 (negative): Attempt to copy an active list — rejected, no state change.
  action rejectCopyActiveList(originalCode: str, newCode: str, newName: str): bool = all {
    currentUser.in(EMAILS),
    originalCode.in(lists.keys()),
    isActive(lists, originalCode),
    newCode.in(CODES),
    codeNotInUse(lists, newCode),
    isValidName(newName),
    lists' = lists,
    participants' = participants,
    items' = items,
    archivedItems' = archivedItems,
    copiedFrom' = copiedFrom,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
  }

  // FR-9 (negative): Attempt to copy a completed list whose archive is missing.
  action rejectCopyMissingArchive(originalCode: str): bool = all {
    currentUser.in(EMAILS),
    originalCode.in(lists.keys()),
    isCompleted(lists, originalCode),
    not(originalCode.in(archivedItems.keys())),
    lists' = lists,
    participants' = participants,
    items' = items,
    archivedItems' = archivedItems,
    copiedFrom' = copiedFrom,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
  }

  // ========================================================================
  // SSE viewer actions (from PRD-0003 FR-10)
  // ========================================================================

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
    items' = items,
    archivedItems' = archivedItems,
    copiedFrom' = copiedFrom,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
  }

  action viewerDisconnects(code: str, email: str): bool = all {
    code.in(sseViewers.keys()),
    email.in(sseViewers.get(code)),
    sseViewers' = sseViewers.put(code, sseViewers.get(code).exclude(Set(email))),
    lists' = lists,
    participants' = participants,
    items' = items,
    archivedItems' = archivedItems,
    copiedFrom' = copiedFrom,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
  }

  // ========================================================================
  // Tick and Init
  // ========================================================================

  action init = all {
    lists' = Map(),
    participants' = Set(),
    currentUser' = "alice@example.com",
    sseViewers' = Map(),
    currentTime' = 0,
    archivedItems' = Map(),
    copiedFrom' = Map(),
    items' = Map(),
  }

  action initWithCompletedNoArchive = all {
    lists' = Map("xyz789" -> {
      code: "xyz789",
      name: "Weekend BBQ",
      status: "completed",
      version: 2,
      createdBy: "alice@example.com",
      completedAt: 1,
      copiedFrom: ""
    }),
    participants' = Set({ listCode: "xyz789", email: "alice@example.com" }),
    items' = Map("xyz789" -> Set()),
    archivedItems' = Map(),
    sseViewers' = Map(),
    currentUser' = "alice@example.com",
    currentTime' = 1,
    copiedFrom' = Map(),
  }

  action step = any {
    nondet code = CODES.oneOf()
    nondet name = NAMES.oneOf()
    nondet email = EMAILS.oneOf()
    nondet version = VERSIONS.oneOf()
    nondet originalCode = CODES.oneOf()
    nondet newCode = CODES.oneOf()
    nondet newName = NAMES.oneOf()
    nondet itemName = ITEM_NAMES.oneOf()
    nondet qty = QUANTITIES.oneOf()
    nondet obs = OBSERVATIONS.oneOf()
    any {
      createList(code, name),
      addItem(code, itemName, qty, obs),
      completeList(code, version),
      copyCompletedList(originalCode, newCode, newName),
      rejectCopyActiveList(originalCode, newCode, newName),
      rejectCopyMissingArchive(originalCode),
      viewerConnects(code, email),
      viewerDisconnects(code, email),
    },
    tick,
  }

  action tick = all {
    currentTime' = currentTime + 1,
    lists' = lists,
    participants' = participants,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    items' = items,
    archivedItems' = archivedItems,
    copiedFrom' = copiedFrom,
  }

  // ========================================================================
  // Invariants
  // ========================================================================

  // INV-1: All copiedFrom references on ShopList records point to existing lists.
  val invCopiedFromRefersToExisting =
    lists.keys().forall(code =>
      if (lists.get(code).copiedFrom != "")
        lists.get(code).copiedFrom.in(lists.keys())
      else
        true
    )

  // INV-2: Copied lists are created with status "active" (version == 1 means
  //         it has not been modified since creation).
  val invCopiedListsAreActive =
    lists.keys().forall(code =>
      if (lists.get(code).copiedFrom != "" and lists.get(code).version == 1)
        lists.get(code).status == "active"
      else
        true
    )

  // INV-3: Copied lists always have the copier (createdBy) as a participant.
  val invCopiedListHasCreatorAsParticipant =
    lists.keys().forall(code =>
      if (lists.get(code).copiedFrom != "")
        isParticipant(participants, code, lists.get(code).createdBy)
      else
        true
    )

  // INV-4: Original lists are never modified by copy — any list referenced
  //        as a copiedFrom source must be completed (only completed lists
  //        are eligible for copying, and our actions never mutate originals).
  val invOriginalListsAreCompleted =
    lists.keys().forall(originalCode =>
      not(lists.keys().exists(code => lists.get(code).copiedFrom == originalCode))
      or isCompleted(lists, originalCode)
    )

  // INV-5: All items on copied lists are unchecked.
  val invCopiedItemsUnchecked =
    lists.keys().forall(code =>
      if (lists.get(code).copiedFrom != "")
        items.get(code).forall(itm => not(itm.checked))
      else
        true
    )

  // Consistency: copiedFrom map matches ShopList.copiedFrom field.
  val invCopiedFromSync =
    lists.keys().forall(code =>
      if (lists.get(code).copiedFrom != "")
        copiedFrom.get(code) == lists.get(code).copiedFrom
      else
        not(code.in(copiedFrom.keys()))
    )

  // Combined invariant for model checking
  val inv =
    invCopiedFromRefersToExisting and
    invCopiedListsAreActive and
    invCopiedListHasCreatorAsParticipant and
    invOriginalListsAreCompleted and
    invCopiedItemsUnchecked and
    invCopiedFromSync

  // ========================================================================
  // Test Runs
  // ========================================================================

  // Copy a completed list with archived items.
  run createCompleteAndCopy =
    init
      .then(createList("abc123", "Groceries"))
      .then(addItem("abc123", "Milk", "1 gallon", ""))
      .then(addItem("abc123", "Bread", "2 loaves", "whole grain"))
      .then(completeList("abc123", 1))
      .then(copyCompletedList("abc123", "xyz789", "Party Supplies"))

  // Copy an empty completed list (FR-7).
  run createEmptyCompleteAndCopy =
    init
      .then(createList("def456", "Empty List"))
      .then(completeList("def456", 1))
      .then(copyCompletedList("def456", "aaa111", "Weekend BBQ"))

  // Reject copying an active list (FR-10).
  run rejectCopyActive =
    init
      .then(createList("abc123", "Groceries"))
      .then(rejectCopyActiveList("abc123", "def456", "Party Supplies"))

  // Reject copying when archive is missing (FR-9).
  run rejectCopyMissingArchiveTest =
    initWithCompletedNoArchive
      .then(rejectCopyMissingArchive("xyz789"))

  // Viewer connects to a copied list.
  run createCopyAndViewer =
    init
      .then(createList("abc123", "Groceries"))
      .then(completeList("abc123", 1))
      .then(copyCompletedList("abc123", "xyz789", "Groceries (Copy)"))
      .then(viewerConnects("xyz789", "alice@example.com"))
}
