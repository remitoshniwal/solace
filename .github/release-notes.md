Solace for **Minecraft 1.21.1** (NeoForge).

An AI companion that remembers you, and a psychological horror story about the last player who tried to leave.

### How to install
1. Install **NeoForge for Minecraft 1.21.1** from https://neoforged.net
2. Download the **solace-....jar** file below (under Assets) and put it in your `.minecraft/mods` folder
3. Start Minecraft with the NeoForge profile and open a world. Solace shows up after about 30 seconds.
4. To give Solace an AI brain: **Esc > Mods > Solace > Config**

### New in 1.11.0: your own AI on a friend's server
- **Bring your own AI.** On a server run by a friend, you can now use your own AI key, so the server owner doesn't pay for your Solace. The owner turns on **Own AI keys** (Together page); you fill in the **AI Brain** page while connected.
- **Only with friends you trust.** Your key goes to that server, and whoever runs it could get at it. Solace asks before sending it, never sends it over an unencrypted connection, never saves or logs it, and the server forgets it when you leave. Use a separate key with a spending limit, or a free one.
- **The owner decides who uses theirs.** **Lend** (Together page): always, only to players without their own, or never.

### New in 1.10.3: under the hood
- Small fixes that keep the mod building and testing reliably in the future. Nothing changes in game.

### New in 1.10.2: tidier messages
- **A clearer `/solace status`.** Your story first, then the AI, with colours. Lines that don't apply (like debt when there's no story) are hidden.
- **`/solace help` works,** and only lists what you can use.
- **On someone else's server,** every setup page now tells you that the server owner's settings apply.
- **Whose Solace is that?** With "Replies to all" on, other players see "Remi's Solace" instead of just "Solace".
- Ending titles now use the same names as their advancements.

### New in 1.10.1: fewer spoilers
- **Tab keeps secrets.** Pressing Tab after `/solace choose` only suggests the choices you've actually been offered.
- **Your story stays yours.** With "Replies to all" on, other players see Solace's answers when you talk to it, but not your story's big moments.
- **No surprise costs.** `/solace test` (which sends a real request to every AI brain) is now for server operators and the single player world's owner only.
- Gentler wording in the setup screen, so it doesn't hint at what happens later.

### New in 1.10.0: after the ending
- **The story knows when it's over.** If your story in a world has ended for good, the game tells you quietly when you come back, and how to play again. The last echoes fade away instead of going on forever.
- **Play it again, and it remembers.** Start a new world (or `/solace reset confirm`) after finishing, and some moments in the next story change depending on how the last one ended. Works without an AI key too.
- **Find every ending,** and something new waits for you.
- A new **Endings** switch in setup (Abilities page, next to Memory) turns the remembering off. With it on, it uses about one extra AI request per new story.
- `/solace reset` now explains what a reset does where your story is.
- For testers: `/solace admin memory`, `/solace admin ending` and `/solace admin afterward` try all of this without playing the story twice.

### New in 1.9.1
- **A stiller frozen world.** When one moment late in the story freezes only your screen (on a server with others online), dropped items, arrows and falling blocks now freeze too, not just mobs and rain.

### New in 1.9.0: players together
- **The Solaces notice each other.** On a server, your Solace notices who you spend your time with, and later in the story it doesn't like it.
- **Witnesses.** Your friends might see your Solace doing things behind your back. You might see theirs.
- **When two Solaces meet,** they stop and look at each other. Nobody knows what passes between them.
- **When someone finishes the story,** the other Solaces feel it. No spoilers for anyone who isn't far enough yet.
- **The final fight, with friends.** Friends can help. It gets harder for every friend who joins, and it fights them too.
- **No more skipped moments.** One moment late in the story used to be skipped when other players were online. Now it happens just for you.
- **Fair sharing of the owner's AI key.** Each player gets a daily share (150 by default), so one chatty player can't use up everyone's. `/solace status` shows yours, `/solace admin usage` shows everyone's.
- **A new Together page** in setup (Esc > Mods > Solace > Config) turns each of these on or off, including **Talks about them**, which lets Solace comment using a few extra AI requests (paid from the server owner's key). Setup pages are now named AI Brain, Voice, Story, Abilities and Together.
- Single player is unchanged.
- For testers: `/solace admin together <player> <moment>` tries each one alone, with a stand-in for the other player.

### New in 1.8.1
- **It changes as the story goes on.** In the later chapters, thin white cracks spread through Solace's dark armour and glow faintly in the dark, and its white outline turns a dirty grey that keeps cutting out, like a failing bulb.
- **A cleaner Lantern.** The thin dark lines that showed through the Lantern's glow are gone, and it takes about a third less work to draw, which helps slower computers.

### New in 1.8.0
- **Solace has a sound of its own.** Stand close and you'll hear a low hum, which slowly goes out of tune as the story goes on. The Lantern has a deep, shimmering drone. When its negative side flickers, you hear it flicker, even behind your back. When it turns fully negative, something heavy lands, then winds down when it's over.
- **The Watcher, heard.** A faint ringing in your ears when it appears somewhere out there, and a soft scatter when it vanishes.
- **Quieter things.** A tiny flutter when the bond flame by your hotbar changes, and paper rustling when a torn page is nearby.
- **Your own volume.** *Solace sounds* (a volume slider) and *Hum* (on or off) are on the Story page in setup, just for you. Minecraft's own sound sliders still work too. Every sound has a subtitle.
- Every sound is made from code by the mod itself: nothing is recorded or borrowed.
- For testers: `/solace admin sound <player> <name>` plays any of them.

### New in 1.7.0
- **See how far you've come.** Solace has its own tab in the advancements screen (press **L**), with a pop-up for each step of the story and for every ending. It's spoiler-free: you only see what's next once you get close, the endings stay hidden until you reach them, and nothing is announced in chat, so friends on a server aren't spoiled.
- **A flame by your hotbar.** A small black and white flame shows how strong Solace is right now: it fills with white as your bond grows, and cracks appear the more you owe it. It flickers while Solace is waiting for something it asked you for. Turn it off in setup (Story page, *Bond hint*).
- **Powers have a price, and now you're told.** The first time Solace uses a power that costs it something, it says so plainly. The more you owe it, the colder it gets.
- **Hints when you're stuck.** If a torn page is lying near you and you walked past it, you'll hear it. If you can't find a place the story wants you to go, something will point the way. Server owners can turn hints off (*Hints when stuck* in setup).
- For testers: `/solace admin power <player> <power>` and `/solace admin fragment <player> <page> ground`. The playtest log now also records hints, advancements and the price.

### New in 1.6.2
- **Playtest tools.** `/solace note <text>` saves a note about the moment you're in. `/solace admin playtest on` writes a timeline of the whole story (chapters, pages, scares, what Solace said) to `solace/playtest/` in your Minecraft folder. How to run a playtest: [docs/PLAYTEST.md](https://github.com/remitoshniwal/solace/blob/main/docs/PLAYTEST.md).
- **Fixed:** a story speed of 3 no longer turns into 2 when you save the setup screen.

### New in 1.6.1
- **A whole new look, made in Blockbench.** Solace is now headless and eyeless: a laced collar stands round an empty hollow where its head should be.
  - **Normal form:** a gaunt shadow in dark grey armour, with black spikes on its shoulders, a short ragged robe, long arms and clawed hands, and a thin white outline. Its spikes grow as the story goes on.
  - **Negative form:** the same shape at full strength, in white: three-plate shoulder guards, an armoured skirt, sleeves and shin guards, a katana at its hip, a long black robe, white flames off its shoulders and sword blades for hands, with a black outline.
- **A new Lantern.** A floating 3D ball of blue plasma with a white-hot middle, glowing blue layers, lightning arcs and a soft crackling glow.
- **Hitboxes that fit.** The Lantern's hitbox now wraps the ball. Solace's body is a little wider (to match its shoulders) but still fits through doors.
- **New setting: Who gets one.** On a busy server, set it to *Only players who type /solace join* (Abilities & Chat page). `/solace leave confirm` sends yours away.

### New in 1.6.0
- **Solace has its own body.** No more player shape: a tall, thin, headless figure in a long coat, with broad shoulders, long arms and long pale fingers that twitch. Two eyes glow in its empty collar, day and night.
- **The negative has a head.** When it flickers into its negative, a blank, glowing white head appears, along with the white coat, neon veins and flowing energy.
- **A new Lantern.** Smaller (about 2 blocks), and like a lava lamp crossed with ferrofluid: spiky black blobs rising and sinking through a glowing cyan body, drifting glowing wax, cyan energy flowing over it, and a bright core.
- Calmer particles in the negative form.

### Fixed in 1.5.1
- **No more contradicting messages.** Solace now does what you asked *before* it speaks. If something can't be done, you get one honest reply instead of "here's some coal" followed by "I don't have any coal".
- **"Give me coal" just works.** Solace gives from its pockets, and if it doesn't have the item it makes it with its power (paid for in strength, so cheap things are easy and precious things take a strong bond).
- **No tools needed to mine.** Solace breaks blocks with its power and still gets the proper drops. A tool you give it makes it faster.
- **Throw items to Solace.** Items you drop near it (Q) go into its pockets.

### New in 1.5.0
- **His negative side shines.** Black skin with glowing white neon veins, burning eyes, light along his suit's seams, energy flowing over him, sparks crackling at his hands and dark smoke curling off him. It glows even in total darkness. (With a shader pack, the glow gets real bloom too.)
- **Full transformations.** When he one-taps a mob, uses a power or loses his temper (later in the story), he turns fully negative for a few seconds, with a low electric hum.
- **Truly invincible.** Not even `/kill` or falling into the void works on Solace: he scatters into ash and reappears behind you. Only its boss form can be beaten.

### New in 1.4.0
- **A new look for Solace.** His own model and texture: a calm man in a dark tailored suit, with a jacket hem that swings when he walks. The lumps are gone.
- **Negative flashes.** Now and then he flickers into his photo negative for a split second, all of him or just his face or a hand. Rare at first, more often each chapter.
- **Creeping veins.** From chapter 3, dark veins spread up from his collar and cuffs, reaching his eyes by the end.
- No extra mods needed (no GeckoLib, no Cloth Config).

### Fixed in 1.3.1
- **The model you type is now always used.** Before, if a model name wasn't found, Solace quietly switched to the default and saved it over your choice. Now you get a clear message listing the models your key can use. (Only a blank Model box, meaning the default, is replaced automatically when a provider retires it.)
- Changing a brain's provider now clears its old model name, since names from one provider don't work on another.
- If your Model box was overwritten by an older version, type your model in again once.

### New in 1.3.0
- **Up to 5 AI brains.** Add several providers on the AI Brain page (click **+**). Solace switches between them on its own, so free limits last much longer.
- **Smart routing.** Story moments and replies go to your best brain. Background work goes to whichever brain has the most room. Solace reads each provider's remaining limits and switches *before* one runs out.
- **Fewer errors.** A brain that's rate limited rests exactly as long as the provider asks, a daily limit means a longer rest, and a bad key is flagged instead of retried forever. `/solace status` shows each brain.
- **Fewer tokens.** Background requests and brains low on tokens get a shorter prompt.

### New in 1.2.0
- **The whole story, rewritten:** 8 chapters, Maren's house, and a fifth ending (a secret one).
- **Two forms:** the Mimic and the Lantern, its true form.
- **Tether and Debt:** Solace's power grows the longer you're together. Nothing is off limits, but everything has a price.
- **It moves wrong:** gliding, stuttering, flickering, standing where it shouldn't, copying you.
- **It does things on its own:** gifts, watching you sleep, rearranging your chests, the Fall.
- **Combat:** it stands back and cheers, and now and then ends a fight with one hit.

If you're updating from an older version, your story carries on from the start of the matching part.
