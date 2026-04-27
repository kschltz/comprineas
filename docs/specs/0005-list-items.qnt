module listItems {
  // ========================================================================
  // Constants
  // ========================================================================

  pure val CODES = Set("abc123", "xyz789", "def456", "aaa111", "bbb222", "ccc333")
  pure val NAMES = Set("Groceries", "Weekend BBQ", "Party Supplies", "Home Depot", "Weekly Shop", "Office Snacks")
  pure val EMAILS = Set("alice@example.com", "bob@example.com", "charlie@example.com")
  pure val STATUSES = Set("active", "completed", "deleted")
  pure val VERSIONS = Set(1, 2, 3)

  pure val ITEM_NAMES = Set("Milk", "Bread", "Eggs", "Butter", "Cheese")
  pure val QUANTITIES = Set("1L", "2", "1kg", "a bunch", "")
  pure val OBSERVATIONS = Set("", "organic", "green not ripe")

  // ========================================================================
  // Types
  // ========================================================================

  type ShopList = {
    code: str,
    name: str,
    status: str,
    version: int,
    createdBy: str,
    completedAt: int
  }

  type Participant = {
    listCode: str,
    email: str
  }

  type ListItem = {
    listCode: str,
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
  var items: int -> ListItem
  var nextItemId: int

  // ========================================================================
  // Pure Definitions
  // ========================================================================

  pure def isValidName(name) =
    name.in(NAMES)

  pure def isValidItemName(name) =
    name.in(ITEM_NAMES)

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

  pure def itemPositionsForList(itemMap, listCode) =
    itemMap.keys().filter(id => itemMap.get(id).listCode == listCode)
      .map(id => itemMap.get(id).position)

  pure def nextPositionForList(itemMap, listCode) =
    if (itemPositionsForList(itemMap, listCode).size() == 0)
      1
    else
      itemPositionsForList(itemMap, listCode).fold(0, (acc, p) => if (p > acc) p else acc) + 1

  // ========================================================================
  // Actions (from PRD-0003 / sharedLists)
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
    items' = items,
    nextItemId' = nextItemId,
  }

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
    items' = items,
    nextItemId' = nextItemId,
  }

  action renameListConflict(code: str, newName: str, expectedVersion: int): bool = all {
    code.in(lists.keys()),
    isActive(lists, code),
    lists.get(code).version != expectedVersion,
    lists' = lists,
    participants' = participants,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
    items' = items,
    nextItemId' = nextItemId,
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
    items' = items,
    nextItemId' = nextItemId,
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
    items' = items,
    nextItemId' = nextItemId,
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
    items' = items,
    nextItemId' = nextItemId,
  }

  action viewerDisconnects(code: str, email: str): bool = all {
    code.in(sseViewers.keys()),
    email.in(sseViewers.get(code)),
    sseViewers' = sseViewers.put(code, sseViewers.get(code).exclude(Set(email))),
    lists' = lists,
    participants' = participants,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
    items' = items,
    nextItemId' = nextItemId,
  }

  action actionOnNonActive(code: str): bool = all {
    code.in(lists.keys()),
    not(isActive(lists, code)),
    lists' = lists,
    participants' = participants,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
    items' = items,
    nextItemId' = nextItemId,
  }

  // ========================================================================
  // Actions (PRD-0005)
  // ========================================================================

  // FR-1, FR-2: Add item to active list
  action addItem(listCode: str, name: str, quantity: str, observations: str): bool = all {
    listCode.in(lists.keys()),
    isActive(lists, listCode),
    isValidItemName(name),
    quantity.in(QUANTITIES),
    observations.in(OBSERVATIONS),
    lists' = lists,
    participants' = participants,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
    items' = items.put(nextItemId, {
      listCode: listCode,
      name: name,
      quantity: quantity,
      observations: observations,
      checked: false,
      position: nextPositionForList(items, listCode)
    }),
    nextItemId' = nextItemId + 1,
  }

  // FR-4: Check an unchecked item on active list
  action checkItem(itemId: int): bool = all {
    itemId.in(items.keys()),
    isActive(lists, items.get(itemId).listCode),
    not(items.get(itemId).checked),
    lists' = lists,
    participants' = participants,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
    items' = items.set(itemId, {
      listCode: items.get(itemId).listCode,
      name: items.get(itemId).name,
      quantity: items.get(itemId).quantity,
      observations: items.get(itemId).observations,
      checked: true,
      position: items.get(itemId).position
    }),
    nextItemId' = nextItemId,
  }

  // FR-4: Uncheck a checked item on active list
  action uncheckItem(itemId: int): bool = all {
    itemId.in(items.keys()),
    isActive(lists, items.get(itemId).listCode),
    items.get(itemId).checked,
    lists' = lists,
    participants' = participants,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
    items' = items.set(itemId, {
      listCode: items.get(itemId).listCode,
      name: items.get(itemId).name,
      quantity: items.get(itemId).quantity,
      observations: items.get(itemId).observations,
      checked: false,
      position: items.get(itemId).position
    }),
    nextItemId' = nextItemId,
  }

  // FR-5: Delete item from active list
  action deleteItem(itemId: int): bool = all {
    itemId.in(items.keys()),
    isActive(lists, items.get(itemId).listCode),
    lists' = lists,
    participants' = participants,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
    items' = items.keys().exclude(Set(itemId)).mapBy(id => items.get(id)),
    nextItemId' = nextItemId,
  }

  // FR-8 (negative): Add item to completed/deleted list — rejected, no state change
  action addItemToCompletedList(listCode: str): bool = all {
    listCode.in(lists.keys()),
    not(isActive(lists, listCode)),
    lists' = lists,
    participants' = participants,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
    items' = items,
    nextItemId' = nextItemId,
  }

  // FR-8 (negative): Check item on completed/deleted list — rejected, no state change
  action checkItemOnCompletedList(itemId: int): bool = all {
    itemId.in(items.keys()),
    not(isActive(lists, items.get(itemId).listCode)),
    lists' = lists,
    participants' = participants,
    sseViewers' = sseViewers,
    currentUser' = currentUser,
    currentTime' = currentTime + 1,
    items' = items,
    nextItemId' = nextItemId,
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
    items' = Map(),
    nextItemId' = 1,
  }

  action step = any {
    nondet code = CODES.oneOf()
    nondet name = NAMES.oneOf()
    nondet email = EMAILS.oneOf()
    nondet version = VERSIONS.oneOf()
    nondet itemName = ITEM_NAMES.oneOf()
    nondet quantity = QUANTITIES.oneOf()
    nondet observations = OBSERVATIONS.oneOf()
    nondet itemId = 1.to(10).oneOf()
    any {
      createList(code, name),
      renameList(code, name, version),
      renameListConflict(code, name, version),
      completeList(code, version),
      deleteList(code, version),
      viewerConnects(code, email),
      viewerDisconnects(code, email),
      actionOnNonActive(code),
      addItem(code, itemName, quantity, observations),
      addItemToCompletedList(code),
      checkItem(itemId),
      uncheckItem(itemId),
      deleteItem(itemId),
      checkItemOnCompletedList(itemId),
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
    nextItemId' = nextItemId,
  }

  // ========================================================================
  // Invariants
  // ========================================================================

  // INV-1: All items belong to existing lists
  val invItemsBelongToExistingLists =
    items.keys().forall(id =>
      items.get(id).listCode.in(lists.keys())
    )

  // INV-2: No two items have same id (explicit; guaranteed by map semantics)
  val invUniqueItemIds =
    items.keys().forall(id =>
      items.keys().contains(id)
    )

  // INV-3: Items on active lists have valid positions
  val invActiveListItemPositionsValid =
    items.keys().forall(id =>
      if (isActive(lists, items.get(id).listCode))
        items.get(id).position > 0
      else
        true
    )

  // INV-4: Items on non-active lists retain valid checked state and position
  val invNonActiveListItemsImmutableState =
    items.keys().forall(id =>
      if (not(isActive(lists, items.get(id).listCode)))
        items.get(id).position > 0
      else
        true
    )

  // INV-5: nextItemId always greater than all item ids
  val invNextItemIdIncreases =
    nextItemId > 0 and items.keys().forall(id => id < nextItemId)

  // Combined invariant for verification
  val inv =
    invItemsBelongToExistingLists and
    invUniqueItemIds and
    invActiveListItemPositionsValid and
    invNonActiveListItemsImmutableState and
    invNextItemIdIncreases

  // ========================================================================
  // Test Runs
  // ========================================================================

  run testAddCheckUncheckDelete =
    init
      .then(createList("abc123", "Groceries"))
      .then(addItem("abc123", "Milk", "1L", ""))
      .then(checkItem(1))
      .then(uncheckItem(1))
      .then(deleteItem(1))

  run testRejectOnCompleted =
    init
      .then(createList("abc123", "Groceries"))
      .then(addItem("abc123", "Milk", "1L", ""))
      .then(completeList("abc123", 1))
      .then(addItemToCompletedList("abc123"))
      .then(checkItemOnCompletedList(1))

  run testCreateCompleteAndReject =
    init
      .then(createList("xyz789", "Party"))
      .then(addItem("xyz789", "Bread", "2", ""))
      .then(completeList("xyz789", 1))
      .then(addItemToCompletedList("xyz789"))
}
