# Solace

*An AI companion that remembers you. A psychological horror story about the last player who tried to leave.*

Minecraft **1.21.1**, **NeoForge**. Works in singleplayer and on servers.

Solace shows up about 30 seconds after you join a world. It's a tall, headless figure in dark armour that follows you, talks to you, cheers you on in fights (and now and then ends one with a single hit), mines, builds and crafts. It's sweet at first. Then you start finding torn journal pages from a player named Maren, and you see someone standing far away who vanishes when you look straight at them...

## What it can do

- **Talks like a real person.** Chat normally near Solace, or start a message with "Solace". Every word comes from an AI (almost any AI service works, see below). Nothing is pre-written.
- **Not a player. Something else.** Solace can't be hurt (not even by `/kill` or the void; only its boss form can be beaten), hovers, moves silently and blinks in and out of existence. It sees everything: where you are, what you carry, what's buried in the stone around you, how many times you've died, even what time it is in the real world.
- **Has powers.** It can heal you, bless you with effects, change the weather and time, strike monsters with lightning or banish them, make hidden creatures glow, find any ore or structure, conjure items out of nothing, and carry you home.
- **Power comes at a price.** Its strength (**Tether**) grows while you're together and gets spent on powers. Nothing is off limits, not even netherite, but big things cost a lot. Every gift adds to your **Debt**. Owe it enough and it starts asking for things. Owe it too much and it collects. A small black and white flame next to your hotbar shows how strong it is and how much you owe (you can turn it off in setup).
- **Moves wrong.** Most of the time it walks like a player. Sometimes it glides without moving its legs, stutters forward like lag, flickers, turns its head too far, stands somewhere it shouldn't be at the edge of your vision, or copies your every move.
- **Does things on its own.** Leaves gifts on your bed. Watches you sleep. Builds things while you're logged off. Rearranges your chests. Once in a while, for a third of a second, you're in the sky, falling... and then you're back, like nothing happened.
- **Three forms, made in Blockbench.** Every day it's a tall, headless, eyeless shadow in dark grey armour, with spiked shoulders, a laced collar round an empty hollow, long arms and claws, outlined in white. Sometimes it flickers into its negative: a white armoured wraith with shoulder flames and sword blades for hands, outlined in black. Its true form, the Lantern, is a crackling 3D ball of blue plasma with a white-hot middle. No extra mods needed.
- **Does what you ask.** "follow me", "stay here", "guard me", "mine 10 iron", "chop some wood", "pick up the items", "build a shelter", "craft a stone pickaxe", "light this place up", "give me your cobblestone", "remember this place as home", "take me home", "where's the nearest village?", "make it day", "heal me"
- **Remembers you.** Your name, what you like, what you did, how you treated it. It even remembers you **across different worlds**.
- **Voice chat (optional).** Hold **G** to talk with your mic, and hear Solace speak.
- **A real story.** 8 chapters, 7 journal pages, the Watcher, Maren's house, a boss fight, and **5 endings** (two are secret). Its own spoiler-free tab in the advancements screen (L) shows how far you've come.
- **Horror that respects you.** Footsteps, torches going out, fake chat messages, signs, a fake crash screen... Every effect can be turned off, and nothing ever destroys your builds.

## Install (for players)

1. Install **NeoForge for Minecraft 1.21.1** from https://neoforged.net (pick the installer, choose "Install client").
2. Download the newest `solace-....jar` from the [Releases page](https://github.com/remitoshniwal/solace/releases/latest) and put it in your `.minecraft/mods` folder.
3. Start Minecraft with the NeoForge profile, open a world, wait 30 seconds.

Solace needs an AI brain to talk. Setting one up takes about two minutes and can be free; see below.

## Give Solace an AI brain

No files to edit. Everything is in game:

1. Press **Esc → Mods → Solace → Config** (or click **[Open setup]** when Solace mentions it in chat, or type `/solace setup`).
2. On the **AI Brain** page, pick a **Provider**. Groq is the easiest free one.
3. Click **Get key**, make a free account on that website, copy your key, and paste it into the **API key** box.
4. Click **Test connection**. When it says "It works!", press **Done**.

| Provider | Cost |
|---|---|
| Groq | **Free tier**, very fast |
| Google Gemini | **Free tier** |
| Ollama / LM Studio | **Free forever**, runs on your PC (needs a good graphics card, no key) |
| OpenRouter | Some free models, many paid |
| OpenAI, Anthropic (Claude), DeepSeek, Mistral, xAI, Together, Cerebras | Paid (some have free tiers) |
| Custom | Any other "OpenAI-compatible" server |

**Using free keys? Add a second brain.** Click **+** next to *Brain* and add another free provider (Gemini, Cerebras...). Solace switches between them on its own, before one runs out, so the free limits last much longer. Details: [docs/PROVIDERS.md](docs/PROVIDERS.md#more-than-one-brain-recommended-for-free-keys)

The other pages set up **Voice**, **Story** (turn scares down or off), **Abilities** and **Together** (multiplayer). **Advanced...** shows every setting.

On a multiplayer server, **every player needs the mod installed**, but only the **server owner** needs an AI key: Solace's AI runs on the server, and the owner pays for everyone's requests. Each player gets their own Solace and their own story, and the Solaces notice each other (see the **Together** page to turn any of that off). Each player also has a daily share of the owner's AI requests (the share on the Together page, 150 by default), so one chatty player can't use up everyone's. On a busy server, set **Who gets one** (Together page) to *Only players who type /solace join*. The server owner sets Solace's AI (in the server's `config/solace-common.toml`). Each player's voice and comfort settings are their own.

**Players' own AI (servers among friends only).** The owner can turn on **Own AI keys** (Together page) so players can use their own AI key instead of the owner's. A player then fills in the **AI Brain** page while connected to that server. Their key is only sent if the owner allows it, they say they trust that server, and the connection is encrypted. **Whoever runs the server could get at a player's key**, so only do this with friends you trust, and use a separate key with a spending limit (or a free one). Only the big online providers work this way (not Ollama, LM Studio or a custom address). **Lend** (Together page) decides who may use the owner's AI: *always* (players without their own, and as a backup), *no backup*, or *never*. Details: [docs/PROVIDERS.md](docs/PROVIDERS.md#your-own-ai-on-someone-elses-server).

More detail, including recommended models and costs: [docs/PROVIDERS.md](docs/PROVIDERS.md)

### ⚠️ Read this about API keys and money

- **Your API key is like a password.** Anyone who has it can spend your money.
  - Don't show it on stream or in screenshots (the setup screen hides it unless you click **Show**).
  - It's saved in `config/solace-common.toml` in your Minecraft folder: **never share that file** or put it in a modpack.
- **Paid providers charge per message.** Each Solace reply is one request. Built-in limits stop surprise bills:
  - **Daily limit** on the AI Brain page (default 400 replies a day for all brains together; after that Solace goes quiet until tomorrow)
  - At most 12 requests a minute (change in **Advanced...**)
  - **Talks on its own** (Abilities page) also uses requests. Turn it off to save more.
  - In multiplayer: each player's daily share (Together page), and **AI comments** (Together page), which lets Solace comment on the other players using a few extra requests. Turn it off to save more.
- Check `/solace status` to see how many requests were used today (operators: `/solace admin usage` lists each player).

## Voice chat (optional)

Open setup and go to the **Voice** page:

- **Talk with mic: ON.** Hold **G** while speaking (change the key in Options → Controls). It uses Groq's free speech-to-text and reuses your Groq key automatically.
- **Solace's voice:** pick *OpenAI / local server* or *ElevenLabs* (both paid) and paste the key. Free option: run [Kokoro-FastAPI](https://github.com/remsky/Kokoro-FastAPI) on your PC and set Voice server to `http://localhost:8880/v1` and Voice name to `af_sky` (then in **Advanced...** set the model to `kokoro`).
- On a Mac, allow Minecraft (or your launcher) to use the microphone in System Settings → Privacy.

## Commands

| Command | What it does |
|---|---|
| `/solace` | Help |
| `/solace setup` | Open the settings screen |
| `/solace say <message>` | Talk to Solace from anywhere |
| `/solace come` | Call Solace to you |
| `/solace pockets` | Open Solace's inventory (or right-click it with an empty hand) |
| `/solace status` | Your story so far, trust, and the AI (each brain's state for the owner and operators) |
| `/solace test` | Test each AI brain (server operators, or the single player world's owner) |
| `/solace note <text>` | Playtesting: save a note about this moment (see [docs/PLAYTEST.md](docs/PLAYTEST.md)) |
| `/solace forget confirm` | Wipe Solace's memory of you (all worlds) |
| `/solace reset confirm` | Restart the story in this world |
| `/solace admin usage` | Server operators: how many AI requests each player used today |
| `/solace admin ...` | Server operators, for testing: `chapter`, `trust`, `bond`, `fragment`, `power`, `horror`, `presence`, `lantern`, `question`, `together`, `playtest on/off` (see [docs/STORY.md](docs/STORY.md)) |

**Tip:** give Solace items by right-clicking it while holding them, or just throw them to it (Q). It doesn't need tools to mine (a pickaxe only makes it faster). Ask it for something it doesn't have and it makes it with its power, if it's strong enough. It needs blocks to build and coal + sticks to make torches.

## Comfort and safety settings

On the **Story** page:
- **Scares: Off** turns all scares off. **Story: OFF** makes Solace just a friendly companion.
- Each scare type has its own switch: torches/doors/signs, fake messages, screen glitches, fake crash. Glitches are soft, with no bright strobing.
- Nothing Solace does on its own ever hurts you, and none of it happens while you're in a real fight or low on health. In multiplayer, a late story moment that stops the whole world only does that when you're the only player online. With others online it freezes only your own screen, and nothing can hurt you while it lasts (turn that off with **Own freeze** on the Together page).
- Solace is told to keep things teen-appropriate, and to drop the act and be kind if a player seems genuinely upset in real life.

## Building it yourself (for developers)

You need **Java 21** ([Adoptium](https://adoptium.net)).

```
./gradlew build        (Windows: gradlew.bat build)
```

The mod ends up in `build/libs/`. Other useful commands:

- `./gradlew runClient` starts Minecraft with the mod for testing
- `./gradlew aiTest` runs the AI self-tests (no Minecraft, no API key, no cost)
- `python3 tools/shade_textures.py` repaints Solace's textures; `python3 tools/make_textures.py` the Watcher's and the logo
- `python3 tools/make_sounds.py` makes Solace's own sounds (needs numpy and soundfile). Every sound is built from code, so none are recorded or borrowed
- Solace's model is `src/main/resources/assets/solace/models/entity/solace.bbmodel` (open it in Blockbench)

Pushing to GitHub builds and tests the mod automatically (see `.github/workflows/build.yml`). To publish a new version: change `mod_version` in `gradle.properties`, and push to `main`. After the tests pass, a new Release (like `v1.0.1`) with the jar appears on the Releases page automatically.

### Where things are

| Folder | What's inside |
|---|---|
| `ai/` | Talking to AI providers (no Minecraft code, so it's testable on its own) |
| `story/` | The story, Solace's memory and brain, the endings. **All story writing is in `StoryText.java`** |
| `entity/` | Solace's and the Watcher's bodies, behaviours, and jobs (mining, building...) |
| `horror/` | The scares |
| `client/` | Rendering, voice chat, screen effects (only runs on players' computers) |
| `config/` | All settings |

Story spoilers: [docs/STORY.md](docs/STORY.md)

## Credits

Inspired by the "Verity" AI companion mods and videos. Solace's story, characters and code are original.
