Solace for **Minecraft 1.21.1** (NeoForge).

An AI companion that remembers you, and a psychological horror story about the last player who tried to leave.

### How to install
1. Install **NeoForge for Minecraft 1.21.1** from https://neoforged.net
2. Download the **solace-....jar** file below (under Assets) and put it in your `.minecraft/mods` folder
3. Start Minecraft with the NeoForge profile and open a world. Solace shows up after about 30 seconds.
4. To give Solace an AI brain: **Esc > Mods > Solace > Config**

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
- **The whole story, rewritten:** 8 chapters, Maren's house, the confession, Day 1000, and a fifth ending (COLLECTED).
- **Two forms:** the Mimic and the Lantern, its true form.
- **Tether and Debt:** Solace's power grows the longer you're together. Nothing is off limits, but everything has a price.
- **It moves wrong:** gliding, stuttering, flickering, standing where it shouldn't, copying you.
- **It does things on its own:** gifts, watching you sleep, rearranging your chests, the Fall.
- **Combat:** it stands back and cheers, and now and then ends a fight with one hit.

If you're updating from an older version, your story carries on from the start of the matching part.
