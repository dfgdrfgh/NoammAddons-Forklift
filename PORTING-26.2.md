# NoammAddons 26.2 port notes

This source tree is a compatibility port of the 26.1.2 source to Minecraft 26.2.

Pass 5 additionally updates the 26.2 GUI/HUD split:
- screen access now uses `Minecraft.gui.screen()` / `Gui.screen()`
- HUD mixins now target `net.minecraft.client.gui.Hud`
- storage overlay screen interception now targets `Gui.setScreen`
- notification extraction moved from the removed `GameRenderer.extractGui` hook to HUD extraction
- removed the obsolete `Minecraft.createUserApiService` injection because 26.2 already performs the token-backed service creation directly
