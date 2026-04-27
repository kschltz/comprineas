// ========================================================================
// Quint Specification: My Lists Dashboard
// ========================================================================
//
// Spec ID:     0007
// Feature:     Enhanced My Lists Dashboard
// Status:      draft
// Linked PRD:  docs/prd/0007-my-lists-dashboard.md
// Author:      pi
// Date:        2026-04-27
//
// Covers: FR-1 through FR-10, AC-1 through AC-18 from PRD-0007.
// Models the dashboard landing page showing active/completed lists,
// create/join/copy actions, and participant-based access control.
// ========================================================================

module myListsDashboard {
  // ========================================================================
  // Constants (nondeterministic choice domains)
  // ========================================================================

  pure val USER_IDS = Set(1, 2, 3)
  pure val DISPLAY_NAMES = Set("Alice", "Bob", "Charlie")
  pure val LIST_IDS = Set(101, 102, 103, 104, 105, 106)
  pure val LIST_CODES = Set("abc123", "def456", "ghi789", "jkl012", "mno345", "pqr678")
  pure val LIST_NAMES = Set("Groceries", "Weekend BBQ", "Party Supplies", "Home Depot", "Weekly Shop", "Office Snacks")
  pure val VALID_STATUSES = Set("active", "completed")

  // ========================================================================
  // Types
  // ========================================================================

  type User = {
    id: int,
    display_name: str
  }

  // NOTE: 'ShoppingList' is used instead of 'List' because 'List'
  // is a built-in Quint type name (e.g., List[int]).
  type ShoppingList = {
    id: int,
    code: str,
    name: str,
    status: str,
    created_by: int,
    version: int
  }

  type Participation = {
    user_id: int,
    list_id: int
  }

  // ========================================================================
  // State Variables
  // ========================================================================

  // All registered users in the system (populated from PRD-0002 auth)
  var users: Set[User]

  // All shopping lists (active, completed) — no soft-deleted lists here
  var lists: Set[ShoppingList]

  // All participation records linking users to lists they've joined
  var participants: Set[Participation]

  // ========================================================================
  // Pure Helper Functions
  // ========================================================================

  // Check if a user is a participant in a list
  pure def isParticipant(parts, userId, listId) =
    parts.exists(p => p.user_id == userId and p.list_id == listId)

  // All lists a user has access to (created or joined, excludes deleted)
  pure def userLists(allLs, parts, userId) =
    allLs.filter(l =>
      l.created_by == userId or isParticipant(parts, userId, l.id)
    )

  // All active lists a user can see on the dashboard
  pure def activeLists(allLs, parts, userId) =
    userLists(allLs, parts, userId).filter(l => l.status == "active")

  // All completed lists a user can see on the dashboard
  pure def completedLists(allLs, parts, userId) =
    userLists(allLs, parts, userId).filter(l => l.status == "completed")

  // ========================================================================
  // Actions
  // ========================================================================

  // FR-6: Create a new list — requires existing creator, unique id/code.
  // Adds creator as the first participant.
  action createList(creatorId: int, listId: int, code: str, name: str): bool = all {
    users.exists(u => u.id == creatorId),
    not(lists.exists(l => l.id == listId)),
    not(lists.exists(l => l.code == code)),
    lists' = lists.union(Set({
      id: listId,
      code: code,
      name: name,
      status: "active",
      created_by: creatorId,
      version: 1
    })),
    participants' = participants.union(Set({ user_id: creatorId, list_id: listId })),
    users' = users,
  }

  // FR-7: Join an existing active list — user must not already be a participant.
  action joinList(userId: int, listId: int): bool = all {
    users.exists(u => u.id == userId),
    lists.exists(l => l.id == listId and l.status == "active"),
    not(isParticipant(participants, userId, listId)),
    participants' = participants.union(Set({ user_id: userId, list_id: listId })),
    lists' = lists,
    users' = users,
  }

  // FR-8, FR-15: Complete a list — only active lists can be completed.
  // Increments version on state change.
  action completeList(userId: int, listId: int): bool = all {
    isParticipant(participants, userId, listId),
    lists.exists(l => l.id == listId and l.status == "active"),
    lists' = lists.map(l =>
      if (l.id == listId)
        {
          id: l.id,
          code: l.code,
          name: l.name,
          status: "completed",
          created_by: l.created_by,
          version: l.version + 1
        }
      else
        l
    ),
    participants' = participants,
    users' = users,
  }

  // FR-8 (copy action on dashboard): Copy a completed list as new active list.
  // The copying user becomes the creator and first participant of the new list.
  action copyList(userId: int, sourceListId: int, newListId: int, newCode: str, newName: str): bool = all {
    isParticipant(participants, userId, sourceListId),
    lists.exists(l => l.id == sourceListId and l.status == "completed"),
    not(lists.exists(l => l.id == newListId)),
    not(lists.exists(l => l.code == newCode)),
    lists' = lists.union(Set({
      id: newListId,
      code: newCode,
      name: newName,
      status: "active",
      created_by: userId,
      version: 1
    })),
    participants' = participants.union(Set({ user_id: userId, list_id: newListId })),
    users' = users,
  }

  // FR-1, FR-2: View dashboard — no-op that shows what the user sees.
  // Models the read-only act of loading the dashboard page.
  action viewDashboard(userId: int): bool = all {
    users.exists(u => u.id == userId),
    lists' = lists,
    participants' = participants,
    users' = users,
  }

  // ========================================================================
  // Init & Step
  // ========================================================================

  // Initial state: pre-populated users (from PRD-0002 auth system),
  // empty lists and participants.
  action init = all {
    users' = Set(
      { id: 1, display_name: "Alice" },
      { id: 2, display_name: "Bob" },
      { id: 3, display_name: "Charlie" }
    ),
    lists' = Set(),
    participants' = Set(),
  }

  // Step: nondeterministically pick values and try every action.
  action step = any {
    nondet userId = USER_IDS.oneOf()
    nondet listId = LIST_IDS.oneOf()
    nondet newListId = LIST_IDS.oneOf()
    nondet code = LIST_CODES.oneOf()
    nondet newCode = LIST_CODES.oneOf()
    nondet name = LIST_NAMES.oneOf()
    nondet newName = LIST_NAMES.oneOf()
    any {
      createList(userId, listId, code, name),
      joinList(userId, listId),
      completeList(userId, listId),
      copyList(userId, listId, newListId, newCode, newName),
      viewDashboard(userId),
    }
  }

  // ========================================================================
  // Invariants
  // ========================================================================

  // INV-1: Every list returned by userLists is genuinely accessible
  // (user created it OR is a participant). Catches helper-function bugs.
  val invDashboardOnlyParticipantLists =
    users.forall(u =>
      userLists(lists, participants, u.id).forall(l =>
        l.created_by == u.id or isParticipant(participants, u.id, l.id)
      )
    )

  // INV-2: activeLists returns only "active" lists; completedLists
  // returns only "completed" lists. No status leakage between sections.
  val invActiveCompletedSegregated =
    users.forall(u =>
      activeLists(lists, participants, u.id).forall(l => l.status == "active") and
      completedLists(lists, participants, u.id).forall(l => l.status == "completed")
    )

  // INV-3: A user with no participations and no created lists gets
  // an empty userLists set (empty state for new users — AC-12).
  val invNewUserEmpty =
    users.forall(u =>
      (not(participants.exists(p => p.user_id == u.id)) and
       not(lists.exists(l => l.created_by == u.id)))
      implies
      userLists(lists, participants, u.id).size() == 0
    )

  // INV-4: No two distinct Participation records share the same
  // (user_id, list_id) pair — no duplicate participations.
  val invNoDuplicateParticipations =
    participants.forall(p1 =>
      participants.forall(p2 =>
        not(p1.user_id == p2.user_id and p1.list_id == p2.list_id and p1 != p2)
      )
    )

  // INV-5: All lists have valid status values.
  val invValidStatus =
    lists.forall(l => l.status.in(VALID_STATUSES))

  // INV-6: All lists have version >= 1.
  val invValidVersion =
    lists.forall(l => l.version >= 1)

  // Combined invariant for verification
  val inv =
    invDashboardOnlyParticipantLists and
    invActiveCompletedSegregated and
    invNewUserEmpty and
    invNoDuplicateParticipations and
    invValidStatus and
    invValidVersion

  // ========================================================================
  // Test Runs
  // ========================================================================

  // Test 1: Alice creates a single list.
  run createSingleList =
    init.then(createList(1, 101, "abc123", "Groceries"))

  // Test 2: Alice creates a list, Bob joins it.
  run createAndJoin =
    init
      .then(createList(1, 101, "abc123", "Groceries"))
      .then(joinList(2, 101))

  // Test 3: Full lifecycle: create -> complete -> copy.
  run createCompleteAndCopy =
    init
      .then(createList(1, 101, "abc123", "Groceries"))
      .then(completeList(1, 101))
      .then(copyList(1, 101, 102, "def456", "Copy of Groceries"))

  // Test 4: View dashboard after creating a list.
  run viewDashboardAfterCreate =
    init
      .then(createList(1, 101, "abc123", "Groceries"))
      .then(viewDashboard(1))

  // Test 5: Completing an already-completed list should fail.
  run completeCompletedListFails =
    init
      .then(createList(1, 101, "abc123", "Groceries"))
      .then(completeList(1, 101))
      .then(completeList(1, 101).fail())

  // Test 6: Joining a non-existent list should fail.
  run joinNonExistentListFails =
    init
      .then(joinList(1, 999).fail())

  // Test 7: Copying an active (non-completed) list should fail.
  run copyActiveListFails =
    init
      .then(createList(1, 101, "abc123", "Groceries"))
      .then(copyList(1, 101, 102, "def456", "Copy").fail())
}
