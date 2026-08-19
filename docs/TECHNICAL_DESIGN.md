# DisplayName API - Technical Design Document

## 1. Overview

DisplayName API is a server-side Minecraft mod (NeoForge 1.21.1, build 21.1.235) that provides infrastructure for composing player nicknames from multiple prefixes and suffixes. The API is designed for consumption by other mods, not for end-user configuration.

### 1.1 Core Principles

- **Server-side only**: No client-side mod required. Works with vanilla clients.
- **Infrastructure API**: Does not resolve placeholders or manage game logic. Consumers provide complete Components.
- **Deterministic composition**: Priority-based ordering with strict collision detection.
- **Event-driven updates**: No tick-based polling. Refreshes occur only when data changes.
- **Runtime-only storage**: No persistence. Consumers must re-register data after server restart or player rejoin.
- **Visual isolation**: Each prefix/suffix is visually independent via automatic reset insertion.

### 1.2 Scope

- **Applies to**: `ServerPlayer` entities only.
- **Does not apply to**: Generic entities, NPCs, mobs, or offline players.
- **Three synchronized destinations**: Tab list, nameplate (above head), and chat author name. All three share the exact same composed state.

---

## 2. Architecture

### 2.1 High-Level Flow

```
Consumer Mod
    ↓
DisplayName API (register/update/remove)
    ↓
NicknameManager (per-player state)
    ↓
Composition Engine (priority sorting, collision detection)
    ↓
Refresh Coordinator
    ├── ServerPlayer.refreshTabListName()
    ├── ServerPlayer.refreshDisplayName()
    └── (Chat uses displayName automatically via ChatType.Bound)
```

### 2.2 NeoForge Integration Points

| Destination | NeoForge Event | Refresh Method |
|---|---|---|
| Tab List | `PlayerEvent.TabListNameFormat` | `ServerPlayer.refreshTabListName()` |
| Nameplate | `PlayerEvent.NameFormat` | `ServerPlayer.refreshDisplayName()` |
| Chat | Inherits from `NameFormat` via `ChatType.Bound` | N/A (future messages use updated displayName) |

### 2.3 What We Do NOT Touch

- `GameProfile.name` (real username remains unchanged)
- `Entity.setCustomName()` (generic entity custom name system)
- Custom network packets (maximum vanilla compatibility)
- Signed chat messages (secure chat system remains intact)
- Scoreboard teams (out of scope; if teams are used, behavior is undefined)

---

## 3. Data Model

### 3.1 Core Entities

#### `NicknameData`
Per-player container holding all registered prefixes and suffixes.

```java
class NicknameData {
    UUID playerId;
    Map<String, Prefix> prefixes;  // id → Prefix
    Map<String, Suffix> suffixes;  // id → Suffix
}
```

#### `Prefix` / `Suffix`
Immutable value objects representing a single prefix or suffix.

```java
record Prefix(String id, int priority, Component value) {}
record Suffix(String id, int priority, Component value) {}
```

- **id**: Unique identifier within the API namespace. Format: `displayname-api:{user-chosen-id}`.
- **priority**: Integer (positive, zero, or negative). Higher priority = closer to player name.
- **value**: Minecraft `Component`. Must be fully resolved by the consumer (no placeholders).

### 3.2 Composition Rules

#### Ordering
- **Prefixes**: Highest priority → Lowest priority → Player Name
- **Suffixes**: Player Name → Highest priority → Lowest priority

Example:
```
Prefixes: P3 (priority 10), P2 (priority 5), P1 (priority 1)
Name: Steve
Suffixes: S1 (priority 10), S2 (priority 5), S3 (priority 1)

Result: P3 P2 P1 Steve S1 S2 S3
```

#### Collision Detection
If two prefixes (or two suffixes) have the **same priority**, composition fails with an error. No partial updates are applied.

Example:
```
Prefix A: priority 10
Prefix B: priority 10
Result: ERROR - Priority collision detected
```

#### Visual Isolation
Each component (prefix, suffix, player name) is automatically separated by a reset code. The API ensures:
```
Prefix1 + RESET + Prefix2 + RESET + PlayerName + RESET + Suffix1 + RESET + Suffix2 + RESET
```

This prevents formatting from leaking between components.

### 3.3 ID Namespace

The API owns the namespace. When a consumer registers a prefix with id `"muertos-de-hambre"`, the internal ID becomes `displayname-api:muertos-de-hambre`. This prevents collisions between different consumers using the same logical ID.

---

## 4. API Contracts

### 4.1 Public API Interface

```java
public interface DisplayNameApi {
    /**
     * Registers or updates a prefix for a player.
     * 
     * @param player Target player (must be online)
     * @param id Unique identifier (will be namespaced as displayname-api:{id})
     * @param priority Priority value (higher = closer to name)
     * @param value Fully resolved Component
     * @throws IllegalArgumentException if player is null or offline
     * @throws PriorityCollisionException if another prefix has the same priority
     */
    void setPrefix(ServerPlayer player, String id, int priority, Component value);
    
    /**
     * Registers or updates a suffix for a player.
     */
    void setSuffix(ServerPlayer player, String id, int priority, Component value);
    
    /**
     * Removes a specific prefix from a player.
     * 
     * @return true if the prefix existed and was removed
     */
    boolean removePrefix(ServerPlayer player, String id);
    
    /**
     * Removes a specific suffix from a player.
     */
    boolean removeSuffix(ServerPlayer player, String id);
    
    /**
     * Removes all prefixes and suffixes from a player.
     * After this call, the player's display name is their vanilla username.
     * This operation cannot be canceled or denied by event listeners.
     */
    void resetNickname(ServerPlayer player);
    
    /**
     * Retrieves the current composed nickname for a player.
     * 
     * @return Composed Component, or null if no prefixes/suffixes are registered
     */
    @Nullable Component getComposedNickname(ServerPlayer player);
}
```

### 4.2 Error Handling

#### `PriorityCollisionException`
Thrown when `setPrefix` or `setSuffix` would create a priority collision. The operation is aborted, and no changes are applied to the player's nickname.

```java
class PriorityCollisionException extends RuntimeException {
    int conflictingPriority;
    String existingId;
    String newId;
}
```

#### Validation Errors
- **Null player**: `IllegalArgumentException`
- **Offline player**: `IllegalArgumentException`
- **Empty id**: `IllegalArgumentException`
- **Null value**: `IllegalArgumentException`

### 4.3 Refresh Behavior

Every successful `setPrefix`, `setSuffix`, `removePrefix`, `removeSuffix`, or `resetNickname` call immediately triggers:
1. Recalculation of the composed nickname
2. `ServerPlayer.refreshTabListName()`
3. `ServerPlayer.refreshDisplayName()`

Chat messages sent **after** the refresh will use the new nickname. Previously sent messages are not retroactively updated.

---

## 5. Commands

### 5.1 `/setprefix`

**Syntax**:
```
/setprefix <target> <id> <priority> <value>
```

**Arguments**:
- `target`: Player name or entity selector (`@a`, `@p`, `@r`, `@s`, `@e[type=player]`)
- `id`: String identifier (will be namespaced as `displayname-api:{id}`)
- `priority`: Integer (positive, zero, or negative)
- `value`: String with legacy formatting codes using `$` instead of `§`

**Example**:
```
/setprefix Steve clan-leader 10 "$6[$eLeader$6]"
/setprefix @a[r=10] vip-status 5 "$b[VIP]"
```

**Behavior**:
- Parses `value` using the legacy parser (see section 6)
- Calls `DisplayNameApi.setPrefix()` for each matched player
- On success: Sends confirmation message to executor only
- On failure: Sends error message to executor only

**Success Message**:
```
Set prefix 'displayname-api:clan-leader' for player 'Steve'
```

**Error Messages**:
```
Target 'UnknownPlayer' does not exist or is offline
Invalid priority: must be an integer
Priority collision: another prefix already has priority 10
```

### 5.2 `/setsuffix`

**Syntax**:
```
/setsuffix <target> <id> <priority> <value>
```

Identical behavior to `/setprefix`, but for suffixes.

### 5.3 `/resetnickname`

**Syntax**:
```
/resetnickname <target>
```

**Behavior**:
- Calls `DisplayNameApi.resetNickname()` for each matched player
- Removes all prefixes and suffixes
- Player's display name reverts to vanilla username
- Cannot be canceled by event listeners

**Success Message**:
```
Reset nickname for player 'Steve'
```

### 5.4 Permissions

All commands require operator level 2 (default `/op` permission level in Minecraft).

### 5.5 Target Validation

- If a target player does not exist or is offline, the command fails for that specific target
- If using a selector that matches multiple players, the command succeeds for valid targets and reports errors for invalid ones
- Example: `/setprefix @a test 5 "$aTest"` with 3 online players and 1 offline player will succeed for 3 and report 1 error

---

## 6. Legacy Parser

### 6.1 Supported Codes

The parser converts `$`-prefixed codes into Minecraft `Component` formatting:

| Code | Meaning |
|---|---|
| `$0` - `$9` | Black, Dark Blue, Dark Green, Dark Aqua, Dark Red, Dark Purple, Gold, Gray, Dark Gray, Blue |
| `$a` - `$f` | Green, Aqua, Red, Light Purple, Yellow, White |
| `$k` | Obfuscated |
| `$l` | Bold |
| `$m` | Strikethrough |
| `$n` | Underline |
| `$o` | Italic |
| `$r` | Reset |

### 6.2 Automatic Reset

The parser automatically appends a reset (`$r`) at the end of each parsed component. This ensures visual isolation between adjacent prefixes/suffixes.

Example:
```
Input: "$6[$eLeader$6]"
Parsed: Component with gold color, containing "[Leader]" with yellow "Leader"
Automatic reset appended: Yes
```

### 6.3 Escape Sequence

To include a literal `$` in the output, use `$$`.

Example:
```
Input: "Price: $$100"
Parsed: Component with text "Price: $100"
```

### 6.4 Implementation

```java
public class LegacyParser {
    public static Component parse(String input) {
        // Parse $-codes into Component
        // Automatically append reset at the end
        // Handle $$ escape sequence
    }
}
```

---

## 7. Event System

### 7.1 NeoForge Events Consumed

The API subscribes to two NeoForge events to inject the composed nickname:

#### `PlayerEvent.TabListNameFormat`
```java
@SubscribeEvent
public static void onTabListName(PlayerEvent.TabListNameFormat event) {
    ServerPlayer player = (ServerPlayer) event.getEntity();
    Component composed = DisplayNameApi.getComposedNickname(player);
    if (composed != null) {
        event.setDisplayName(composed);
    }
}
```

#### `PlayerEvent.NameFormat`
```java
@SubscribeEvent
public static void onNameFormat(PlayerEvent.NameFormat event) {
    ServerPlayer player = (ServerPlayer) event.getEntity();
    Component composed = DisplayNameApi.getComposedNickname(player);
    if (composed != null) {
        event.setDisplayname(composed);
    }
}
```

### 7.2 Consumer Hooks

The API does **not** expose custom events for consumers. All interactions are through the `DisplayNameApi` interface methods. Consumers call these methods directly when they need to update nicknames.

Example consumer usage:
```java
// When player levels up
public void onPlayerLevelUp(ServerPlayer player, int newLevel) {
    Component levelPrefix = Component.literal("[$6Lvl " + newLevel + "$]")
        .withStyle(ChatFormatting.GOLD);
    
    displayNameApi.setPrefix(player, "level", 10, levelPrefix);
    // API automatically refreshes tab, nameplate, and future chat messages
}
```

---

## 8. Implementation Details

### 8.1 Package Structure

```
com.displaynameapi
├── api
│   ├── DisplayNameApi.java
│   ├── Prefix.java
│   ├── Suffix.java
│   └── exceptions
│       └── PriorityCollisionException.java
├── internal
│   ├── NicknameManager.java
│   ├── NicknameData.java
│   ├── CompositionEngine.java
│   └── RefreshCoordinator.java
├── command
│   ├── SetPrefixCommand.java
│   ├── SetSuffixCommand.java
│   └── ResetNicknameCommand.java
├── parser
│   └── LegacyParser.java
└── event
    └── EventHandler.java
```

### 8.2 Thread Safety

All API methods must be called from the server thread. The API does not perform internal synchronization. Consumers are responsible for ensuring thread safety.

### 8.3 Memory Management

- `NicknameData` is stored in a `Map<UUID, NicknameData>` in `NicknameManager`
- When a player disconnects, their `NicknameData` is removed from the map
- No persistence across server restarts

### 8.4 Performance Considerations

- **No tick-based polling**: Refreshes occur only on explicit API calls
- **Lazy composition**: The composed nickname is calculated only when needed (during event handling)
- **Minimal object creation**: Prefix/Suffix are immutable records; composition creates a single Component per refresh
- **Priority collision detection**: O(n log n) due to sorting, but n is typically small (< 10 prefixes/suffixes per player)

---

## 9. Error Handling Strategy

### 9.1 Command Errors

All command errors are reported to the executor via chat messages. Errors include:
- Target not found / offline
- Invalid priority (not an integer)
- Priority collision
- Invalid legacy formatting syntax

### 9.2 API Errors

API methods throw exceptions for programming errors:
- `IllegalArgumentException` for invalid arguments
- `PriorityCollisionException` for priority conflicts

Consumers should catch and handle these exceptions appropriately.

### 9.3 Logging

Critical errors (e.g., unexpected exceptions during composition) are logged to the server log with full stack traces.

---

## 10. Compatibility Notes

### 10.1 NeoForge Version

This design is based on NeoForge 1.21.1, build 21.1.235. Some API signatures may differ slightly from later 21.1.x builds (e.g., 21.1.242+). Implementation should verify exact method signatures against the target build.

### 10.2 Other Mods

- **Mods using this API**: Fully compatible. The API guarantees correct composition and refresh.
- **Mods not using this API**: Compatibility is not guaranteed. If another mod directly modifies `Player.getDisplayName()` or uses scoreboard teams, behavior may conflict.
- **Scoreboard teams**: If the server uses scoreboard teams with prefixes/suffixes, the interaction with this API is undefined. Consumers should avoid using both systems simultaneously.

### 10.3 Chat Signing

The API does not modify signed chat messages. The visual author name in chat is derived from `Player.getDisplayName()`, which is updated by the API. The message content and signature remain unchanged.

---

## 11. Testing Strategy

### 11.1 Unit Tests

- Legacy parser: all formatting codes, escape sequences, edge cases
- Composition engine: priority ordering, collision detection, visual isolation
- Nickname manager: add/remove/update operations

### 11.2 Integration Tests

- Command execution: valid targets, invalid targets, selectors
- Refresh behavior: verify tab, nameplate, and chat update correctly
- Priority collisions: verify error handling and no partial updates

### 11.3 Manual Testing

- Multi-mod scenario: two mods registering prefixes/suffixes for the same player
- High-priority updates: rapid succession of API calls
- Player disconnect/reconnect: verify state cleanup

---

## 12. Future Considerations

### 12.1 Out of Scope (Current Version)

- Persistence across server restarts
- Client-side mod for enhanced rendering
- Placeholder resolution
- Per-destination customization (e.g., prefix in tab but not in nameplate)
- Integration with scoreboard teams
- Support for entities other than players

### 12.2 Potential Extensions

If demand exists, future versions could add:
- Optional persistence layer
- Event system for consumers to listen to nickname changes
- Query API to inspect current prefixes/suffixes
- Bulk operations (set multiple prefixes in one call)

---

## 13. Summary

DisplayName API provides a clean, deterministic, and performant infrastructure for composing player nicknames from multiple sources. By leveraging NeoForge's event system and avoiding tick-based polling, the API minimizes performance impact while maximizing compatibility with vanilla clients.

The API is intentionally minimal: it composes and refreshes, but does not resolve placeholders, persist data, or manage game logic. Consumers retain full control over when and how they update nicknames.

Key design decisions:
- **Priority-based ordering** with strict collision detection
- **Visual isolation** via automatic reset insertion
- **Event-driven refreshes** (no tick polling)
- **Runtime-only storage** (no persistence)
- **Server-side only** (vanilla client compatible)
- **Legacy parser** for easy string-to-Component conversion

This design provides a solid foundation for any mod that needs to display dynamic player nicknames across tab, nameplate, and chat.
</content>
</writeFile>

Listo. He creado el documento `TECHNICAL_DESIGN.md` con toda la especificación técnica del mod en inglés.

**Resumen de lo que incluye**:

1. **Arquitectura completa**: Flujo desde el consumidor hasta los tres destinos (tab, nameplate, chat)
2. **Modelo de datos**: `NicknameData`, `Prefix`, `Suffix` con IDs namespaced como `displayname-api:{id}`
3. **Reglas de composición**: Ordenamiento por prioridad, detección de colisiones, aislamiento visual automático
4. **Contratos de API pública**: Interfaz `DisplayNameApi` con todos los métodos, excepciones y comportamiento
5. **Comandos**: `/setprefix`, `/setsuffix`, `/resetnickname` con sintaxis exacta, permisos, mensajes de éxito/error
6. **Parser legacy**: Soporte completo de códigos `$0-$f`, `$k-$o`, `$r`, escape `$$`, reset automático
7. **Integración NeoForge**: Eventos `TabListNameFormat` y `NameFormat`, métodos de refresh
8. **Estructura de paquetes**: Organización del código
9. **Manejo de errores**: Excepciones, mensajes de comando, logging
10. **Consideraciones de performance**: No tick polling, composición lazy, mínima creación de objetos
11. **Compatibilidad**: Notas sobre NeoForge 21.1.235, otros mods, chat signing
12. **Estrategia de testing**: Unit, integration, manual