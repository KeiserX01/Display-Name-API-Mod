Aquí tienes el documento `first-idea.md` limpio, convertido a Markdown sin información de formato RTF:

---

# Diseño de la API de Nicknames

Sí. Antes de documentar o diseñar la arquitectura, conviene cerrar varias decisiones porque estamos construyendo **una API de infraestructura**, no simplemente un mod que cambia nombres.

Además, hay un punto importante en tu propuesta: **no quiero asumir todavía que `§`-formatting codes sean la representación interna de la API**. Podemos soportarlos como entrada/salida, pero probablemente convenga que internamente trabajemos con `Component` de Minecraft y una capa de composición. Eso afecta mucho la robustez y compatibilidad.

Te haría estas preguntas, agrupadas por tema.

---

## 1. Arquitectura general de la API

### 1.1.
¿Quieres que la API sea exclusivamente para jugadores (`ServerPlayer`), o eventualmente también debería permitir entidades/NPCs?

Por ahora entiendo:

```
Player -> Nickname data -> Tab / Above Head / Chat
```

y no entidades genéricas.

### 1.2.
¿Los tres componentes deben compartir **exactamente el mismo estado**, como planteaste?

Por ejemplo:

```
Player
└── NicknameData
    ├── Prefixes
    ├── BaseName
    └── Suffixes
```

y luego:

```
TabNickName      ─┐
Nickname         ├──  same composed nickname
ChatNickName     ─┘
```

¿O quieres permitir que en el futuro un mod pueda decir "este prefix aparece en Tab pero no sobre la cabeza"?

Mi recomendación inicial sería **mismo estado y mismo resultado**, pero dejar la arquitectura preparada para diferenciar componentes posteriormente.

---

## 2. Prefix / Suffix

### 2.1.
¿La `priority` debe ser un entero sin límites?

Por ejemplo:

```
priority = 100
priority = 2
priority = 1
priority = -50
```

¿O quieres restringirla a `int >= 0`?

### 2.2.
En tu ejemplo:

```
priority 2 -> level
priority 1 -> clan
```

produce:

```
[level][clan]
```

Por tanto entiendo que:

**Higher priority = closer to the player's name.**

Es decir:

```
Priority 3
Priority 2
Priority 1
PlayerName
```

¿Correcto?

### 2.3.
¿Qué ocurre si dos mods registran un prefix con la misma priority?

Por ejemplo:

```
Mod A -> priority 1
Mod B -> priority 1
```

Necesitamos una regla determinista. Algunas opciones:

- orden de registro
- `modId` alfabético
- timestamp/sequence interno
- lanzar error
- permitir una segunda prioridad

Yo recomendaría **orden de registro + sequence ID interno**, porque mantiene el comportamiento determinista sin depender de nombres de mods.

---

## 3. Identificación de un prefix/suffix

Esta es probablemente la pregunta más importante de la API.

Imagina:

```
mod1
└── prefix "[LEVEL]"
```

Más tarde quiere actualizarlo:

```
"[LEVEL 50]"
```

¿Cómo sabe nuestra API cuál prefix debe modificar?

Propongo que cada registro tenga un **ID propiedad del consumidor**:

```java
PrefixHandle levelPrefix = nicknameApi.registerPrefix(
    player,
    "my_mod:level",
    2,
    component
);
```

Y posteriormente:

```java
nicknameApi.updatePrefix(
    player,
    "my_mod:level",
    component
);
```

o incluso:

```java
levelPrefix.set(component);
```

¿Quieres una API basada en **IDs/handles**, o prefieres que cada mod gestione directamente objetos tipo `Prefix` / `Suffix`?

---

## 4. ¿Un mod puede tener múltiples prefixes?

Por ejemplo:

```
my_mod:level
my_mod:rank
my_mod:prestige
```

¿Sí?

Y lo mismo para suffixes:

```
my_mod:season
my_mod:afk
my_mod:status
```

Entiendo que sí, pero quiero confirmarlo.

---

## 5. Eliminación del formatting

Aquí quiero definir exactamente la semántica.

Tu regla parece ser:

```
PREFIX_1 + RESET + PREFIX_2 + RESET + PLAYER_NAME + RESET + SUFFIX_1 + RESET + SUFFIX_2 + RESET
```

Pero hay una diferencia importante entre:

```
§r
```

y "volver al estilo visual por defecto".

En Minecraft moderno, utilizando `Component`, el concepto de reset de formatting no necesariamente debe implementarse literalmente como `§r`.

Por eso quiero saber:

**¿Tu requisito es específicamente que la API produzca legacy formatting codes `§...`, o el requisito real es que cada elemento sea visualmente independiente del anterior?**

Por ejemplo, internamente podríamos tener:

```
Component Prefix1
Component Prefix2
Component PlayerName
Component Suffix1
Component Suffix2
```

y aplicar aislamiento de estilos entre componentes.

Después, si algún consumidor necesita legacy string, convertiríamos el resultado.

Esto sería bastante más seguro para una API de Minecraft 1.21.1.

---

## 6. Input de los otros mods

¿Quieres aceptar solamente:

```
Component
```

o también:

```
String
Legacy String
```

por ejemplo:

```
"§4[ §9§l45§r §4]"
```

?

Mi recomendación sería:

```
Primary API:              Component
Optional compatibility API: String / legacy formatting
```

Así los mods modernos no tienen que trabajar con `§`.

---

## 7. Placeholders dinámicos

Tus ejemplos tienen:

```
{lvl}
{clan del jugador}
```

¿Estos placeholders los resuelve **el mod que registra el prefix**?

Es decir, nuestro mod recibe:

```
"[ {lvl} ]"
```

pero **no sabe qué es** `{lvl}`.

Entonces:

```
Mod Level
    ↓
calcula nivel
    ↓
actualiza prefix
    ↓
Nickname API
```

¿Correcto?

Yo recomendaría fuertemente esto.

La API debería ser responsable de **composición**, no de conocer qué significa `{lvl}`, `{clan}`, `{rank}`, etc.

---

## 8. Actualización dinámica

Has dicho algo muy importante:

> ninguna actualización debe ejecutarse por tick; el consumidor decide cuándo actualizar.

Quiero confirmar que buscas algo como:

```java
nicknameApi.updatePrefix(player, "my_mod:level", newComponent);
```

y **esa llamada inmediatamente actualiza los tres lugares**.

Por ejemplo:

```
updatePrefix()
├── Tab
├── Chat
└── Above Head
```

¿Correcto?

---

## 9. Persistencia

¿Qué debe ocurrir cuando:

- el jugador desconecta
- vuelve a entrar
- muere
- cambia de dimensión
- el servidor reinicia

¿Los prefixes/suffixes son:

**A. Runtime-only**
El mod consumidor tiene que volver a registrarlos.

**B. Persistidos por nuestra API**
Nuestra librería guarda la información en datos del jugador.

**C. Híbrido**
Nuestra API puede persistirlos, pero el mod consumidor decide si quiere hacerlo.

Mi recomendación sería **C**, pero necesitamos definirlo antes de diseñar la API pública.

---

## 10. Cliente vs servidor

¿Quieres que esta sea una API fundamentalmente **server-side**?

Mi propuesta sería:

```
Mod consumidor
    ↓
Nickname API
    ↓
ServerPlayer
    ├── Tab packet
    ├── Entity metadata / team / scoreboard mechanism
    └── Chat display
        ↓
    Client
```

Y que el cliente **no tenga que instalar nuestro mod** para visualizar los nombres, siempre que Minecraft vanilla permita transmitir correctamente cada modificación.

Esto es particularmente importante para decidir cómo implementaremos `Nickname` sobre la cabeza.

---

## 11. Compatibilidad con otros sistemas

¿Qué comportamiento quieres si otro mod **no utiliza nuestra API** y modifica directamente el nombre?

Ejemplo:

```
Our API    → "§4[45] Juan"
Other mod  → "§cAdmin Juan"
```

¿Nuestra API debe:

1. sobrescribirlo;
2. intentar detectarlo;
3. ignorarlo;
4. ofrecer hooks para integrarlo;
5. considerarlo fuera de nuestro ámbito?

Mi recomendación inicial sería:

**La API garantiza compatibilidad entre consumidores que utilizan la API. No puede garantizarla frente a mods que manipulan directamente los mecanismos internos de Minecraft.**

Pero podemos diseñar mecanismos de integración si tiene sentido.

---

## 12. Orden de composición

Quiero confirmar también el resultado final.

Si tenemos:

```
Prefixes:   P3  P2  P1
Name:       Juan
Suffixes:   S1  S2  S3
```

¿Quieres:

```
P3 P2 P1 Juan S1 S2 S3
```

Es decir:

```
Prefix:  highest → lowest
Suffix:  lowest  → highest
```

Esto es lo más intuitivo si consideramos que las priorities determinan la cercanía al nombre.

---

## 13. Espaciado

¿La API debe insertar automáticamente espacios?

Por ejemplo, si los mods envían:

```
"[45]"  "[Clan]"
```

¿resultado?

```
[45] [Clan] Juan
```

¿O cada mod debe proporcionar exactamente su propio espacio?

Esto es importante porque tus ejemplos parecen indicar que **la API debería encargarse de la separación**.

Yo recomendaría:

```
API: Prefix + " " + Prefix + " " + Name + " " + Suffix
```

pero dejando configurable el separator.

---

## 14. Nombre base del jugador

¿El `BaseName` debe ser siempre el **Minecraft username real**?

Por ejemplo:

```
UUID
↓
Juan_gamerpro
```

o quieres que nuestra API permita también:

```java
setBaseName(player, component)
```

?

Esto determinaría si somos una **nickname composition API** o una API más general de **display names**.

---

## 15. Cambio del nombre base

Relacionado con lo anterior:

¿Quieres que la API pueda modificar también:

```
Juan_gamerpro
```

por:

```
Juan
```

?

Por el momento parece que **no**, y que nuestra responsabilidad es únicamente:

```
PREFIX + REAL_NAME + SUFFIX
```

pero quiero dejarlo explícito.

---

## 16. "Forzar" actualización del nombre sobre la cabeza

Aquí necesitamos investigar específicamente cómo funciona Minecraft 1.21.1 NeoForge.

No quiero asumir que existe una simple llamada:

```java
player.refreshName()
```

porque probablemente tengamos que trabajar con mecanismos diferentes para:

- `PlayerInfo` / tab list
- display name
- entity nameplate
- chat

Una vez respondidas las preguntas anteriores, considero que éste debería ser uno de los primeros puntos técnicos que investiguemos.

La prioridad será encontrar el mecanismo **correcto y server-authoritative**, no hacer un workaround basado en ticks.

---

## Las decisiones que considero imprescindibles

Si quieres avanzar rápido, puedes responder solamente estas primero:

```
1.  ¿Solo jugadores? Sí/No
2.  ¿Los tres componentes siempre comparten exactamente el mismo nickname? Sí/No
3.  Priority: mayor número = más cerca del nombre? Sí/No
4.  ¿Qué ocurre con priorities iguales? A) orden de registro B) modId C) error D) otro
5.  ¿Cada prefix/suffix tendrá un ID único por mod? Sí/No
6.  ¿Un mod puede registrar múltiples prefixes/suffixes? Sí/No
7.  ¿Component como API principal y legacy § como compatibilidad? Sí/No
8.  ¿Los placeholders los resuelve el mod consumidor? Sí/No
9.  ¿Las actualizaciones son exclusivamente manuales/event-driven? Sí/No
10. Persistencia: A) runtime B) nuestra API C) configurable por consumidor
11. ¿La API será server-side? Sí/No
12. Frente a mods que no usan nuestra API: A) fuera de nuestro ámbito B) intentar compatibilidad C) otro
13. ¿Orden: P3 P2 P1 NAME S1 S2 S3? Sí/No
14. ¿La API inserta espacios automáticamente? Sí/No
15. ¿El nombre base siempre es el username real? Sí/No
16. ¿El mod puede cambiar el nombre base? Sí/No
```

Con esas respuestas ya podemos pasar a la siguiente fase: **definir el modelo conceptual y los contratos de la API antes de escribir una sola línea de implementación**.

Ahí podemos decidir `NicknameData`, `Prefix`, `Suffix`, `Priority`, `Handle`, composición, sincronización y finalmente cómo NeoForge 1.21.1 implementará cada uno de los tres destinos.

---

## Respuestas

**1.1** La API es exclusiva para jugadores

**1.2** Los tres componentes siempre comparten y compartirán el mismo estado.

**2.1** El priority siempre debe ser un entero, no importa si es positivo o negativo

**2.2** sí, es correcto.

**2.3** si 2 mods registran 2 prefix con el mismo priority la API debe devolver error sin aplicar cambios en ningún nickname, lo mismo es para los sufix. Por ejemplo:

```
ClanMod
   prefix: clan, clan_rank
   sufix: medallas

prefix:  "Muertos de Hambre "
    priority: 10
    type: clan
    id:0

prefix:  "fuego negro "
    priority: 10
    type: clan
    id:1

prefix: owner
   priority: 9
   type: rank_clan
   id:2

prefix: member
   priority: 9
   type: rank_clan
   id:2

sufix:  "invierno3 "
   priority: 10
   type: medalla
   id:0

sufix:  "verano3 "
   priority: 10
   type: medalla
   id:1

sufix:  "MVP "
   priority: 9
   type: medalla
   id:3
```

### Ejemplo sin errores

```
Jugador1:
   playername: juan
   prefix1: Muertos de Hambre
   prefix2: member
   sufix: invierno3
   final-nickname: Muertos de Hambre member Juan invierno3

Jugador2:
   playername: pepe
   prefix1: fuego negro
   prefix2: owner
   sufix: verano3
   sufix: MVP
   final-nickname: fuego negro owner Juan verano3 MVP
```

### Ejemplos con errores

```
Jugador1:
   playername: juan
   sufix1: verano3
   sufix2: invierno3
   final-nickname: ERROR. Sufix 1 & 0 have same priority. Change priority or delete one of both

Jugador2:
   playername: pepe
   prefix1: fuego negro
   prefix2: Muertos de Hambre
   final-nickname: ERROR. Prefix 0 & 1 have same priority. Change priority or delete one of both

jugador3:
   playername: maria
   prefix1: fuego negro
   prefix2: Muertos de Hambre
   sufix1: verano3
   sufix2: invierno3
   sufix3: MVP
   final-nickname: ERROR: Prefix 1 & 0, and sufix 1 & 0 have same priority. Change priority or delete one of both
```

**3.** Sí, es un problema con el que ya contaba. La solución es atribuir un id, en los ejemplos del punto 2.3 son numéricos, pero deberían ser strings para mayor legibilidad en el código.

**4.** Sí, un mod consumidor puede tener cuantas prefix quiera

**5.** Buena pregunta. Este carácter `§` fue la representación que utilicé para describir lo que quería, pero sí, el requisito real es que cada elemento sea visualmente independiente del anterior

**6.** Por el momento simplemente utilizaremos `Component`

**7.** Correcto. `[ {lvl} ]` es básicamente una representación in-game, pero nuestra API no es responsable de resolver esto, eso debe venir completo desde el consumidor

**8.** sí, es correcto. El mod consumidor es responsable de actualizar, nuestra API solo proporciona la herramienta.

**9.** La opción más adecuada es la A. El mod consumidor es el responsable de alimentar a la API con los datos. Si ningún dato viene, la API simplemente no hace nada o devuelve error según corresponda. Esta es la correcta separación de responsabilidades

**10.** Efectivamente, esta API debe correr server-side.

**11.** Opción 3. La responsabilidad de utilizar mods compatibles recae en el usuario final. seguimos tu recomendación: La API garantiza compatibilidad entre consumidores que utilizan la API. No puede garantizarla frente a mods que manipulan directamente los mecanismos internos de Minecraft.

**12.** No, un doble sistema para el priority es complejidad innecesaria. Nos guiamos por la lógica: entra menor el priority más cerca del playername:

```
prefix: highest → lowest
sufix:  highest → lowest
```

**13.** No, mis ejemplos son conceptuales. El mod consumidor decidirá si quiere espacios o no

**14.** sí, basename debe ser minecraft real.

**15.** No, por el momento basename deberá ser minecraft real.

**16.** Completamente de acuerdo. Es necesario investigar el funcionamiento de este punto (y de todos los demás) para garantizar el funcionamiento determinista de la API  