package com.github.noamm9.mixin;

import com.github.noamm9.features.impl.general.storageoverlay.StorageOverlay;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Gui.class)
public abstract class MixinGuiScreen {
    @Shadow @Nullable private Screen screen;

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen noammaddons$onSetScreen(Screen newScreen) {
        if (!StorageOverlay.INSTANCE.enabled) return newScreen;
        var replacement = StorageOverlay.onScreenChange(this.screen, newScreen);
        return replacement != null ? replacement : newScreen;
    }
}
