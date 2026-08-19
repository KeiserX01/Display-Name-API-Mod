Aquí tienes el documento limpio en formato Markdown, con toda la información de formato RTF eliminada:

---

# NeoForge 1.21.1 / 21.1.x

Conviene separar tres conceptos que a primera vista parecen ser "el nombre del jugador", pero técnicamente son cosas diferentes:

- **Tablist:** `PlayerInfo.tabListDisplayName`
- **Nameplate sobre la cabeza:** normalmente `Player#getDisplayName()`
- **Nombre que aparece como autor en chat:** `ChatType.Bound.name`, que normalmente se obtiene a partir de `Player#getDisplayName()`

Esto es importante porque permite hacer prácticamente todo **desde el servidor**, sin tocar el `GameProfile.name` y sin necesitar que el cliente tenga instalado tu mod.

---

## Resumen rápido

| Pregunta | Tablist | Nameplate | Chat |
|---|---|---|---|
| Representación | `Component` | `Component` | `ChatType.Bound.name` → `Component` |
| API principal | `PlayerEvent.TabListNameFormat` | `PlayerEvent.NameFormat` | `PlayerEvent.NameFormat` / `ChatType.Bound` |
| Refresh explícito | Sí | Sí, mediante refresh | Por mensaje; no existe un cache global del nombre |
| Packet principal | `ClientboundPlayerInfoUpdatePacket` | Normalmente **ningún packet específico de nombre** | `ClientboundPlayerChatPacket` |
| Cache cliente | **Sí** (`PlayerInfo`) | No hay un cache equivalente del display name | El mensaje ya recibido queda renderizado en el historial |
| ¿Cliente modded? | No | No | No |
| Tick handler | No | No | No |
| Mejor mecanismo | Evento NeoForge + refresh | `NameFormat` | `NameFormat` |

Las APIs de NeoForge 1.21.1 confirman explícitamente que `TabListNameFormat` se utiliza al obtener/refrescar el nombre del tablist, mientras que `NameFormat` se utiliza al obtener/refrescar el display name del jugador. ([aldak.netlify.app](https://aldak.netlify.app/javadoc/1.21.1-21.1.x/net/neoforged/neoforge/event/entity/player/playerevent.tablistnameformat?utm_source=chatgpt.com))

---

## 1. ¿Cómo está representado el nombre del Tab?

En el cliente, el tablist está representado por un objeto:

```
net.minecraft.client.multiplayer.PlayerInfo
```

Este contiene:

```java
private Component tabListDisplayName;
```

y la documentación indica específicamente que, cuando este valor no es `null`, se muestra **en lugar del nombre real del jugador**. ([lexxie.dev](https://lexxie.dev/neoforge/1.21.1/net/minecraft/client/multiplayer/PlayerInfo.html?utm_source=chatgpt.com))

Por tanto:

```
GameProfile.name
└── nombre real / fallback

PlayerInfo.tabListDisplayName
└── nombre mostrado en TAB
```

El packet correspondiente es:

```
ClientboundPlayerInfoUpdatePacket
```

y su `Action` contiene explícitamente:

```
UPDATE_DISPLAY_NAME
```

entre sus acciones. ([Mappings](https://mappings.dev/1.21/net/minecraft/network/protocol/game/ClientboundPlayerInfoUpdatePacket%24Action.html?utm_source=chatgpt.com))

Además, el `Entry` del packet contiene:

```java
Component displayName
```

([nekoyue.github.io](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.21.x-neoforge/net/minecraft/network/protocol/game/ClientboundPlayerInfoUpdatePacket.Entry.html?utm_source=chatgpt.com))

### Consecuencia

No necesitas cambiar el `GameProfile`. Puedes tener:

```
GameProfile:  name = "Steve"
Tablist:      "§6[VIP] Steve"
```

sin modificar el nombre real del perfil.

---

## 2. ¿Cómo está representado el nameplate?

El nameplate no es simplemente el mismo campo que el tablist. El renderer obtiene el nombre del `Entity` / `Player`, mediante el sistema de `getDisplayName()`.

`Entity` implementa `Nameable` y tiene:

```java
public Component getDisplayName()
```

y `Player` lo sobreescribe. ([lexxie.dev](https://lexxie.dev/neoforge/1.21.1/net/minecraft/world/entity/Entity.html?utm_source=chatgpt.com))

NeoForge añade precisamente:

```
PlayerEvent.NameFormat
```

que se dispara cuando se obtiene el display name mediante:

```
Player.getDisplayName()
Player.refreshDisplayName()
```

([aldak.netlify.app](https://aldak.netlify.app/javadoc/1.21.1-21.1.x/net/neoforged/neoforge/event/entity/player/playerevent.nameformat?utm_source=chatgpt.com))

Por tanto, conceptualmente:

```
Player
└── getDisplayName()
    └── Component
        └── nameplate
```

Esto es **mucho mejor que utilizar `setCustomName()`** para este propósito.

`Entity#setCustomName(Component)` existe, pero corresponde al sistema genérico de custom names de entidades y además tiene `setCustomNameVisible(boolean)`. ([lexxie.dev](https://lexxie.dev/neoforge/1.21.1/net/minecraft/world/entity/Entity.html?utm_source=chatgpt.com))

Para un jugador yo **no recomendaría** modificar el custom name para implementar un nickname.

---

## 3. ¿Cómo está representado el nombre del chat?

En 1.21.x el sistema de chat utiliza:

```
ChatType.Bound
```

Este objeto tiene:

```java
Component name;
```

y:

```java
Optional<Component> targetName;
```

([Mappings](https://mappings.dev/1.21/net/minecraft/network/chat/ChatType%24Bound.html?utm_source=chatgpt.com))

Es decir:

```
ChatType
└── Bound
    ├── type
    ├── name    → nombre del autor
    └── targetName
```

El servidor crea/bindea ese nombre a partir de la entidad que habla. En concreto, existe:

```
ChatType.bind(...)
```

que recibe una entidad y produce el `ChatType.Bound`. ([Mappings](https://mappings.dev/1.21.11/net/minecraft/network/chat/ChatType.html?utm_source=chatgpt.com))

Por eso `PlayerEvent.NameFormat` es especialmente interesante: si tu nickname es el **display name del jugador**, el mismo nombre puede alimentar el nameplate y el nombre del autor del chat.

---

## 4. ¿Qué APIs/eventos/clases del servidor pueden modificar cada uno?

### TAB

La API oficial de NeoForge para esto es:

```
PlayerEvent.TabListNameFormat
```

Tiene:

```java
event.setDisplayName(Component)
```

y se dispara cuando se obtiene el nombre del jugador para el tablist. ([aldak.netlify.app](https://aldak.netlify.app/javadoc/1.21.1-21.1.x/net/neoforged/neoforge/event/entity/player/playerevent.tablistnameformat?utm_source=chatgpt.com))

Ejemplo conceptual:

```java
@SubscribeEvent
public static void onTabName(PlayerEvent.TabListNameFormat event) {
    event.setDisplayName(
        Component.literal("[VIP] " + event.getEntity().getName().getString())
    );
}
```

Para un `ServerPlayer` existe además:

```java
player.refreshTabListName();
```

NeoForge documenta explícitamente que este método fuerza el refresh del nombre del tablist y vuelve a disparar `PlayerEvent.TabListNameFormat`. ([nekoyue.github.io](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.21.x-neoforge/net/minecraft/server/level/ServerPlayer.html?utm_source=chatgpt.com))

### Nameplate

Utiliza:

```
PlayerEvent.NameFormat
```

Puedes hacer:

```java
@SubscribeEvent
public static void onNameFormat(PlayerEvent.NameFormat event) {
    event.setDisplayname(nickname);
}
```

NeoForge documenta que `NameFormat` se dispara al llamar:

```
Player.getDisplayName()
Player.refreshDisplayName()
```

([aldak.netlify.app](https://aldak.netlify.app/javadoc/1.21.1-21.1.x/net/neoforged/neoforge/event/entity/player/playerevent.nameformat?utm_source=chatgpt.com))

Esto es preferible a tocar directamente el renderer.

### Chat

Hay dos niveles.

#### Nivel recomendado

Usar:

```
PlayerEvent.NameFormat
```

si quieres que el nickname sea coherente con el display name. El chat utiliza un `ChatType.Bound` cuyo `name` es un `Component`. ([Mappings](https://mappings.dev/1.21/net/minecraft/network/chat/ChatType%24Bound.html?utm_source=chatgpt.com))

#### Nivel más bajo

El servidor finalmente trabaja con:

```
ServerPlayer.sendChatMessage(...)
PlayerList.broadcastChatMessage(...)
```

NeoForge/Minecraft exponen `PlayerList.broadcastChatMessage(PlayerChatMessage, ..., ChatType.Bound)`. ([lexxie.dev](https://lexxie.dev/neoforge/1.21.1/net/minecraft/server/players/PlayerList.html?utm_source=chatgpt.com))

Y `ServerGamePacketListenerImpl` tiene:

```java
sendPlayerChatMessage(PlayerChatMessage, ChatType.Bound)
```

([lexxie.dev](https://lexxie.dev/neoforge/1.21.1/net/minecraft/server/network/ServerGamePacketListenerImpl.html?utm_source=chatgpt.com))

Por tanto, si necesitas un control extremadamente específico del chat, puedes construir/controlar el `ChatType.Bound`. Pero para un simple nickname, **no bajaría a este nivel**.

---

## 5. ¿Qué packets intervienen?

### TAB

El importante es:

```
ClientboundPlayerInfoUpdatePacket
```

con:

```
ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME
```

El `Entry` contiene:

```java
Component displayName
```

([Mappings](https://mappings.dev/1.21/net/minecraft/network/protocol/game/ClientboundPlayerInfoUpdatePacket%24Action.html?utm_source=chatgpt.com))

Por tanto:

```
SERVER
├── ClientboundPlayerInfoUpdatePacket
    └── UPDATE_DISPLAY_NAME
        ▼
CLIENT
├── PlayerInfo.tabListDisplayName
    └── TAB
```

### Nameplate

Aquí está la diferencia importante. **No existe un `ClientboundPlayerNameplatePacket`**. El cliente ya conoce al jugador como una entidad y obtiene su display name al renderizarlo.

El sistema genérico de entidades sí tiene:

```
ClientboundSetEntityDataPacket
```

para datos sincronizados de entidad, y `Entity` tiene `DATA_CUSTOM_NAME`, pero eso pertenece al **custom name de entidad**, no es el mecanismo que recomendaría para el nickname normal del jugador. ([Mappings](https://mappings.dev/1.21.1/net/minecraft/world/entity/Entity.html?utm_source=chatgpt.com))

Así que si utilizas:

```
PlayerEvent.NameFormat
```

no estás intentando mandar un packet de "cambia nametag".

### Chat

Para chat normal de jugador:

```
ClientboundPlayerChatPacket
```

Para chat disfrazado:

```
ClientboundDisguisedChatPacket
```

Ambos existen en 1.21.1. ([lexxie.dev](https://lexxie.dev/neoforge/1.21.1/net/minecraft/network/protocol/game/package-summary.html?utm_source=chatgpt.com))

El nombre del autor forma parte de los parámetros de chat (`ChatType.Bound`) que llegan al cliente.

---

## 6. ¿Se puede refrescar cada uno bajo demanda?

### TAB: sí, claramente

Estamos es probablemente la parte más sencilla.

```java
serverPlayer.refreshTabListName();
```

NeoForge dice literalmente que fuerza el refresh del nombre del tablist. ([nekoyue.github.io](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.21.x-neoforge/net/minecraft/server/level/ServerPlayer.html?utm_source=chatgpt.com))

Por ejemplo:

```java
nicknameManager.setNickname(player, nickname);
player.refreshTabListName();
```

No necesitas esperar al siguiente tick para que eventualmente ocurra.

### Nameplate: sí, pero conceptualmente es diferente

El display name tiene:

```java
Player.refreshDisplayName()
```

y `PlayerEvent.NameFormat` está diseñado alrededor de ese mecanismo. ([aldak.netlify.app](https://aldak.netlify.app/javadoc/1.21.1-21.1.x/net/neoforged/neoforge/event/entity/player/playerevent.nameformat?utm_source=chatgpt.com))

Por tanto puedes invalidar/refrescar el display name cuando cambie tu nickname. La diferencia fundamental es que no estás mandando un packet "nametag update"; estás haciendo que el valor que utiliza el cliente para representar al jugador sea recalculado.

### Chat: sí, pero no como "refresh"

El chat es diferente. Una vez que un mensaje ya fue enviado:

```
Steve: Hola
```

no existe un mecanismo vanilla equivalente a:

```java
refreshAllPreviousChatNames();
```

El mensaje ya fue enviado y el cliente ya lo incorporó al historial. Si cambias:

```
Steve → Alex
```

los **mensajes nuevos** pueden aparecer como:

```
Alex: ...
```

pero los mensajes antiguos no se reescriben automáticamente.

---

## 7. ¿El cliente cachea alguno?

### TAB: sí

Este es el caso más claro. El cliente tiene:

```java
PlayerInfo
```

y:

```java
Component tabListDisplayName;
```

([lexxie.dev](https://lexxie.dev/neoforge/1.21.1/net/minecraft/client/multiplayer/PlayerInfo.html?utm_source=chatgpt.com))

Es un cache/estado local explícito.

### Nameplate: no de la misma manera

No existe un:

```java
PlayerInfo.nameplateName
```

equivalente. El cliente mantiene el objeto `Player`, pero el display name es obtenido mediante el sistema de nombres. Por eso no tienes que sincronizar manualmente una "tabla de nameplates".

### Chat: el mensaje sí queda en el historial

Aquí hay que diferenciar:

- `cache del nombre`
- de: `mensaje ya recibido`

El cliente no necesita mantener un `nicknameCache` para cada mensaje. El `Component` que representa el mensaje ya contiene la información necesaria para renderizarlo. Por eso cambiar el nickname después **no puede modificar retroactivamente mensajes ya recibidos** sin volver a enviarlos/reemplazarlos.

---

## 8. ¿Qué causa exactamente el refresh del cache?

### TAB

La secuencia es aproximadamente:

```
ServerPlayer.refreshTabListName()
    ↓
PlayerEvent.TabListNameFormat
    ↓
Component nuevo
    ↓
ClientboundPlayerInfoUpdatePacket
    ↓
UPDATE_DISPLAY_NAME
    ↓
ClientPacketListener
    ↓
PlayerInfo.setTabListDisplayName(...)
    ↓
TAB muestra nuevo nombre
```

El cliente tiene específicamente el método:

```java
applyPlayerInfoUpdate(...)
```

que procesa las acciones del `ClientboundPlayerInfoUpdatePacket`. ([Mappings](https://mappings.dev/1.21.1/net/minecraft/client/multiplayer/ClientPacketListener.html?utm_source=chatgpt.com))

### Nameplate

La secuencia conceptual es:

```
Player.getDisplayName()
    ↓
PlayerEvent.NameFormat
    ↓
Component
    ↓
renderer
    ↓
nameplate
```

La clave es que **no necesitas mandar un packet específico de nameplate**.

### Chat

Cada mensaje nuevo genera:

```
PlayerChatMessage
    ↓
ChatType.Bound
    ↓
name = display name
    ↓
ClientboundPlayerChatPacket
    ↓
cliente
    ↓
render del mensaje
```

Por eso el cambio afecta a **mensajes futuros**.

---

## 9. ¿Se puede hacer sin `TickEvent` / `PlayerTickEvent`?

**Sí.** Y de hecho es lo que recomiendo. No hagas:

```java
@SubscribeEvent
public static void onPlayerTick(PlayerTickEvent.Post event) {
    player.refreshTabListName();
}
```

Eso sería innecesario y puede generar tráfico y trabajo constante. En su lugar, hazlo **event-driven**:

```
nickname cambia
├── actualizar estado del nickname
├── refreshDisplayName
└── refreshTabListName
```

Por ejemplo:

```java
public static void updateNickname(ServerPlayer player, Component nickname) {
    nicknameManager.set(player.getUUID(), nickname);
    player.refreshDisplayName();
    player.refreshTabListName();
}
```

La filosofía correcta es:

> **refresh only when the source value changes**

no:

> `refresh every tick.`

---

## 10. ¿El cliente necesita nuestro mod?

Para el mecanismo que te recomiendo: **No.**

Tu mod puede ser **server-side only**. El cliente vanilla ya entiende:

```
ClientboundPlayerInfoUpdatePacket
ClientboundPlayerChatPacket
Components
PlayerInfo
Player.getDisplayName()
```

No necesitas un packet custom. Esto es una ventaja enorme de compatibilidad. NeoForge además dispone de infraestructura específica para detectar/negociar payloads modded, precisamente porque los packets custom requieren que el cliente los conozca. ([lexxie.dev](https://lexxie.dev/neoforge/1.21.1/net/neoforged/neoforge/network/registration/NetworkRegistry.html?utm_source=chatgpt.com))

Si no utilizas payloads custom:

```
SERVER MOD
├── vanilla packets
    ▼
VANILLA CLIENT
```

es el escenario más compatible.

---

## 11. ¿Hay limitaciones vanilla?

Sí, y son importantes.

### A. No puedes cambiar el `GameProfile.name` libremente como mecanismo de nickname

El nombre real:

```java
GameProfile#getName()
```

tiene implicaciones mucho mayores. No recomiendo modificarlo. Usa:

```java
Component displayName
```

para la identidad visual.

### B. El chat firmado tiene restricciones

Minecraft 1.21.x tiene el sistema de **secure chat**, firmas y validación de mensajes. No conviene intentar:

> modificar el texto firmado

después de que el jugador lo haya firmado. Eso es conceptualmente diferente de cambiar el **nombre visual del autor**. El propio cliente mantiene un `SignedMessageValidator` en `PlayerInfo`. ([lexxie.dev](https://lexxie.dev/neoforge/1.21.1/net/minecraft/client/multiplayer/PlayerInfo.html?utm_source=chatgpt.com))

Por eso la estrategia correcta es:

```
mensaje:      "Hola"
autor visual: nickname
```

y no:

> reescribir el mensaje firmado

### C. El chat antiguo no se actualiza

Si:

```
10:00 Steve: Hola
10:01 nickname cambia a Alex
```

no esperes que automáticamente se convierta en:

```
10:00 Alex: Hola
```

El segundo mensaje sí puede ser:

```
10:02 Alex: ¿Qué tal?
```

### D. Tablist y nameplate son sistemas independientes

Aunque ambos puedan usar tu nickname:

```
TAB       → TabListNameFormat
NAMEPLATE → NameFormat
CHAT      → NameFormat / ChatType.Bound
```

No asumas que cambiar uno cambia los tres.

### E. Teams pueden afectar la apariencia

El scoreboard/team system puede añadir:

```
prefix
suffix
color
name tag visibility
```

`PlayerTeam` tiene, por ejemplo:

```java
getPlayerPrefix()
getPlayerSuffix()
getFormattedName()
getNameTagVisibility()
```

([lexxie.dev](https://lexxie.dev/neoforge/1.21.1/net/minecraft/world/scores/PlayerTeam.html?utm_source=chatgpt.com))

Así que un servidor que ya utiliza Teams puede producir una apariencia diferente a tu `Component` puro. Esto es especialmente relevante si quieres máxima compatibilidad con plugins/mods que utilizan scoreboard teams.

---

## 12. ¿Qué mecanismo proporciona la mayor compatibilidad?

Mi recomendación para tu mod sería esta arquitectura:

```
                    NicknameManager
├── UUID → Component
├────────────────────┼────────────────────┐
│                    │                    │
▼                    ▼                    ▼
NameFormat   TabListNameFormat  Chat
│                    │                    │
▼                    ▼                    ▼
Nameplate       TAB          ChatType
```

Concretamente:

### 1. Mantén el nickname fuera del `GameProfile`

Por ejemplo:

```java
Map<UUID, Component> nicknames;
```

o mejor aún, almacenamiento persistente apropiado para tu caso.

### 2. Para nameplate y usos generales

Usa:

```java
PlayerEvent.NameFormat

@SubscribeEvent
public static void onNameFormat(PlayerEvent.NameFormat event) {
    Player player = event.getEntity();
    Component nickname = NicknameManager.getNickname(player.getUUID());
    if (nickname != null) {
        event.setDisplayname(nickname);
    }
}
```

NeoForge define este evento exactamente para modificar el display name de un jugador. ([aldak.netlify.app](https://aldak.netlify.app/javadoc/1.21.1-21.1.x/net/neoforged/neoforge/event/entity/player/playerevent.nameformat?utm_source=chatgpt.com))

### 3. Para TAB

Usa:

```java
PlayerEvent.TabListNameFormat

@SubscribeEvent
public static void onTabListName(PlayerEvent.TabListNameFormat event) {
    Player player = event.getEntity();
    Component nickname = NicknameManager.getNickname(player.getUUID());
    if (nickname != null) {
        event.setDisplayName(nickname);
    }
}
```

Y cuando cambie el nickname:

```java
player.refreshTabListName();
```

NeoForge proporciona explícitamente este método para este propósito. ([aldak.netlify.app](https://aldak.netlify.app/javadoc/1.21.1-21.1.x/net/neoforged/neoforge/event/entity/player/playerevent.tablistnameformat?utm_source=chatgpt.com))

### 4. Para chat

Deja que el sistema vanilla utilice el `displayName` siempre que sea posible. Esto es mucho más compatible que interceptar y reconstruir manualmente:

```java
ClientboundPlayerChatPacket
```

para cada mensaje. El servidor ya dispone de:

```java
ChatType.Bound
```

y su:

```java
Component name
```

([Mappings](https://mappings.dev/1.21/net/minecraft/network/chat/ChatType%24Bound.html?utm_source=chatgpt.com))

---

## La arquitectura que yo elegiría

Si tu objetivo exacto es:

> "Cambiar el nombre visual de un jugador en todas las instancias posibles, manteniendo el username real intacto y funcionando con clientes vanilla."

yo haría:

```
                     REAL IDENTITY
├── GameProfile/UUID
│
├── NicknameManager
│   └── Component nickname
├────────────────────┼────────────────────┐
│                    │                    │
▼                    ▼                    ▼
NameFormat      TabListNameFormat     ChatType
│                    │                    │
▼                    ▼                    ▼
Nameplate            TAB              CHAT
```

Y **no**:

```java
GameProfile#setName(...)
```

ni:

```
PlayerTickEvent
```

ni:

```
Clientbound custom packet
```

ni:

```
Client-side renderer replacement
```

salvo que posteriormente quieras implementar comportamientos que vanilla simplemente no puede ofrecer.

### En una frase

La combinación `PlayerEvent.NameFormat` + `PlayerEvent.TabListNameFormat` + `ServerPlayer.refreshTabListName()` es probablemente la solución más limpia y compatible para 1.21.1. El chat debe aprovechar el `displayName` / `ChatType.Bound`, en lugar de intentar reemplazar el texto firmado. NeoForge expone explícitamente ambos eventos de formato y el refresh del tablist para este propósito. ([aldak.netlify.app](https://aldak.netlify.app/javadoc/1.21.1-21.1.x/net/neoforged/neoforge/event/entity/player/playerevent.tablistnameformat?utm_source=chatgpt.com))

Un detalle importante: estamos usando **21.1.235**, mientras que parte de la documentación pública actualmente indexada corresponde a revisiones 21.1.x posteriores como 21.1.242. Las APIs mencionadas son de la misma rama 21.1.x, pero para implementar el mod exactamente contra **21.1.235** conviene comprobar las firmas de esa build concreta antes de copiar código literalmente.